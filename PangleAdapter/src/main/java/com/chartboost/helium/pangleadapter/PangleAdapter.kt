package com.chartboost.helium.pangleadapter

import android.app.Activity
import android.content.Context
import android.view.View
import com.bytedance.sdk.openadsdk.*
import com.chartboost.heliumsdk.domain.*
import com.chartboost.heliumsdk.utils.LogController
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The Helium Pangle SDK adapter.
 */
class PangleAdapter : PartnerAdapter {
    companion object {
        /**
         * Flag that can optionally be set to enable Pangle's multi-process support. It must be set
         * prior to initializing the Pangle SDK for it to take effect.
         */
        public var multiProcessSupport = false
            set(value) {
                field = value
                LogController.d("Pangle's multi-process support is ${if (value) "enabled" else "disabled"}.")
            }

        /**
         * Key for parsing the Pangle SDK application ID.
         */
        private const val APPLICATION_ID_KEY = "application_id"
    }

    /**
     * Indicate whether GDPR currently applies to the user.
     */
    private var gdprApplies: Boolean? = null

    /**
     * Get the Pangle SDK version.
     */
    override val partnerSdkVersion: String
        get() = TTAdSdk.getAdManager().sdkVersion

    /**
     * Get the Pangle adapter version.
     *
     * Note that the version string will be in the format of `Helium.Partner.Partner.Partner.Adapter`,
     * in which `Helium` is the version of the Helium SDK, `Partner` is the major.minor.patch version
     * of the partner SDK, and `Adapter` is the version of the adapter.
     */
    override val adapterVersion: String
        get() = BuildConfig.HELIUM_PANGLE_ADAPTER_VERSION

    /**
     * Get the partner name for internal uses.
     */
    override val partnerId: String
        get() = "pangle"

    /**
     * Get the partner name for external uses.
     */
    override val partnerDisplayName: String
        get() = "Pangle"

    /**
     * A map of Helium's listeners for the corresponding Helium placements.
     */
    private val listeners = mutableMapOf<String, PartnerAdListener>()

    /**
     * Initialize the Pangle SDK so that it is ready to request ads.
     *
     * @param context The current [Context].
     * @param partnerConfiguration Configuration object containing relevant data to initialize Pangle.
     *
     * @return Result.success(Unit) if Pangle successfully initialized, Result.failure(Exception) otherwise.
     */
    override suspend fun setUp(
        context: Context,
        partnerConfiguration: PartnerConfiguration
    ): Result<Unit> {
        return suspendCoroutine { continuation ->
            partnerConfiguration.credentials[APPLICATION_ID_KEY]?.let { appId ->
                TTAdSdk.init(
                    context.applicationContext,
                    buildConfig(appId),
                    object : TTAdSdk.InitCallback {
                        override fun success() {
                            continuation.resume(
                                Result.success(
                                    LogController.i("Pangle SDK successfully initialized.")
                                )
                            )
                        }

                        override fun fail(code: Int, message: String?) {
                            LogController.e("Failed to initialize Pangle SDK: $code and error $message")
                            continuation.resume(
                                Result.failure(HeliumAdException(HeliumErrorCode.PARTNER_SDK_NOT_INITIALIZED))
                            )
                        }
                    })
            } ?: run {
                LogController.e("Failed to initialize Pangle SDK: Missing application ID.")
                continuation.resumeWith(Result.failure(HeliumAdException(HeliumErrorCode.PARTNER_SDK_NOT_INITIALIZED)))
            }
        }
    }

    /**
     * Build the necessery configurations that the Pangle SDK requires on [setUp].
     *
     * @param appId an application ID that will be set in the configuration.
     * @return a [TTAdConfig] object with the necessary configurations.
     */
    private fun buildConfig(appId: String): TTAdConfig {
        return TTAdConfig.Builder()
            .appId(appId)
            .supportMultiProcess(multiProcessSupport)
            .data("[{\"name\":\"mediation\",\"value\":\"Helium\"},{\"name\":\"adapter_version\",\"value\":\"$adapterVersion\"}]")
            .build()
    }

    /**
     * Save the current GDPR applicability state for later use.
     *
     * @param context The current [Context].
     * @param gdprApplies True if GDPR applies, false otherwise.
     */
    override fun setGdprApplies(context: Context, gdprApplies: Boolean) {
        this.gdprApplies = gdprApplies
    }

    /**
     * Notify Pangle of user GDPR consent.
     *
     * @param context The current [Context].
     * @param gdprConsentStatus The user's current GDPR consent status.
     */
    override fun setGdprConsentStatus(context: Context, gdprConsentStatus: GdprConsentStatus) {
        TTAdSdk.setGdpr(
            when (gdprConsentStatus) {
                GdprConsentStatus.GDPR_CONSENT_GRANTED -> 0
                GdprConsentStatus.GDPR_CONSENT_DENIED -> 1
                GdprConsentStatus.GDPR_CONSENT_UNKNOWN -> -1
            }
        )
    }

    /**
     * Notify Pangle of the CCPA compliance.
     *
     * @param context The current [Context].
     * @param hasGivenCcpaConsent True if the user has given CCPA consent, false otherwise.
     * @param privacyString The CCPA privacy String.
     */
    override fun setCcpaConsent(
        context: Context,
        hasGivenCcpaConsent: Boolean,
        privacyString: String?
    ) {
        TTAdSdk.setCCPA(
            when (hasGivenCcpaConsent) {
                true -> 0
                false -> 1
            }
        )
    }

    /**
     * Notify Pangle of the COPPA subjectivity.
     *
     * @param context The current [Context].
     * @param isSubjectToCoppa True if the user is subject to COPPA, false otherwise.
     */
    override fun setUserSubjectToCoppa(context: Context, isSubjectToCoppa: Boolean) {
        TTAdSdk.setCoppa(
            when (isSubjectToCoppa) {
                true -> 1
                false -> 0
            }
        )
    }

    /**
     * Get a bid token if network bidding is supported.
     *
     * @param context The current [Context].
     * @param request The [PreBidRequest] instance containing relevant data for the current bid request.
     *
     * @return A Map of biddable token Strings.
     */
    override suspend fun fetchBidderInformation(
        context: Context,
        request: PreBidRequest
    ): Map<String, String> = emptyMap()

    /**
     * Attempt to load a Pangle ad.
     *
     * @param context The current [Context].
     * @param request An [AdLoadRequest] instance containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    override suspend fun load(
        context: Context,
        request: AdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        return when (request.format) {
            AdFormat.BANNER -> loadBannerAd(context, request, partnerAdListener)
            AdFormat.INTERSTITIAL -> loadInterstitialAd(context, request, partnerAdListener)
            AdFormat.REWARDED -> loadRewardedAd(context, request, partnerAdListener)
        }
    }

    /**
     * Attempt to show the currently loaded Pangle ad.
     *
     * @param context The current [Context]
     * @param partnerAd The [PartnerAd] object containing the Pangle ad to be shown.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    override suspend fun show(context: Context, partnerAd: PartnerAd): Result<PartnerAd> {
        val listener = listeners.remove(partnerAd.request.heliumPlacement)
        return when (partnerAd.request.format) {
            AdFormat.BANNER -> {
                // Banner ads do not have a separate "show" mechanism.
                Result.success(partnerAd)
            }
            AdFormat.INTERSTITIAL -> {
                (context as? Activity)?.let { activity ->
                    showInterstitialAd(activity, partnerAd, listener)
                } ?: run {
                    LogController.e("Pangle failed to show interstitial ad. Activity context is required.")
                    Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
                }
            }
            AdFormat.REWARDED -> {
                (context as? Activity)?.let { activity ->
                    showRewardedAd(activity, partnerAd, listener)
                } ?: run {
                    LogController.e("Pangle failed to show rewarded ad. Activity context is required.")
                    Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
                }
            }
        }
    }

    /**
     * Discard unnecessary Pangle ad objects and release resources.
     *
     * @param partnerAd The [PartnerAd] object containing the Pangle ad to be discarded.
     *
     * @return Result.success(PartnerAd) if the ad was successfully discarded, Result.failure(Exception) otherwise.
     */
    override suspend fun invalidate(partnerAd: PartnerAd): Result<PartnerAd> {
        return when (partnerAd.request.format) {
            AdFormat.BANNER -> destroyBannerAd(partnerAd)
            AdFormat.INTERSTITIAL, AdFormat.REWARDED -> {
                // Pangle does not have destroy methods for their fullscreen ads.
                Result.success(partnerAd)
            }
        }
    }

    /**
     * Attempt to load a Pangle banner ad.
     *
     * @param context The current [Context].
     * @param request An [AdLoadRequest] instance containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadBannerAd(
        context: Context,
        request: AdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        return suspendCoroutine { continuation ->
            val bannerAd = TTAdSdk.getAdManager().createAdNative(context)

            val adSlot = request.size?.let {
                val widthDp = it.width.toFloat()
                val heightDp = it.height.toFloat()
                LogController.d("Pangle setting banner with size (w: $widthDp, h: $heightDp)")

                AdSlot.Builder()
                    .setCodeId(request.partnerPlacement)
                    .setExpressViewAcceptedSize(
                        widthDp,
                        heightDp
                    ).build()
            }

            bannerAd.loadBannerExpressAd(adSlot, object : TTAdNative.NativeExpressAdListener {
                override fun onError(code: Int, message: String?) {
                    LogController.d("Failed to load Pangle banner ad. Pangle code: $code with message: $message")
                    continuation.resume(
                        Result.failure(HeliumAdException(HeliumErrorCode.NO_FILL))
                    )
                }

                override fun onNativeExpressAdLoad(bannerAds: MutableList<TTNativeExpressAd>?) {
                    bannerAds?.firstOrNull()?.let { banner ->
                        banner.setExpressInteractionListener(object :
                            TTNativeExpressAd.ExpressAdInteractionListener {
                            override fun onAdClicked(bannerView: View?, type: Int) {
                                partnerAdListener.onPartnerAdClicked(
                                    PartnerAd(
                                        ad = bannerView,
                                        details = emptyMap(),
                                        request = request
                                    )
                                )
                            }

                            override fun onAdShow(bannerView: View?, type: Int) {
                                //NO-OP
                            }

                            override fun onRenderFail(
                                bannerView: View?,
                                message: String?,
                                code: Int
                            ) {
                                banner.destroy()
                                LogController.e("Failed to render Pangle banner ad.")
                                continuation.resumeWith(
                                    Result.failure(HeliumAdException(HeliumErrorCode.PARTNER_ERROR))
                                )
                            }

                            override fun onRenderSuccess(
                                bannerView: View?,
                                width: Float,
                                height: Float
                            ) {
                                continuation.resume(
                                    Result.success(
                                        PartnerAd(
                                            ad = bannerView,
                                            details = emptyMap(),
                                            request = request
                                        )
                                    )
                                )
                            }
                        })
                        banner.render()
                    } ?: run {
                        LogController.d("No Pangle banner found.")
                        continuation.resume(
                            Result.failure(
                                HeliumAdException(HeliumErrorCode.PARTNER_ERROR)
                            )
                        )
                    }
                }
            })
        }
    }

    /**
     * Attempt to load a Pangle interstitial ad.
     *
     * @param context The current [Context].
     * @param request An [AdLoadRequest] instance containing data to load the ad with.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadInterstitialAd(
        context: Context,
        request: AdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        // Save the listener for later usage.
        listeners[request.heliumPlacement] = partnerAdListener

        return suspendCoroutine { continuation ->
            val interstitialAd = TTAdSdk.getAdManager().createAdNative(context)
            val adSlot = AdSlot.Builder()
                .setCodeId(request.partnerPlacement)
                .build()
            interstitialAd.loadFullScreenVideoAd(
                adSlot, object : TTAdNative.FullScreenVideoAdListener {
                    override fun onError(code: Int, message: String?) {
                        LogController.d(
                            "Failed to load Pangle interstitial ad. " +
                                    "Pangle code: $code with message: $message"
                        )
                        continuation.resume(
                            Result.failure(
                                HeliumAdException(HeliumErrorCode.NO_FILL)
                            )
                        )
                    }

                    override fun onFullScreenVideoAdLoad(videoAd: TTFullScreenVideoAd?) {
                        continuation.resume(
                            Result.success(
                                PartnerAd(
                                    ad = videoAd,
                                    details = emptyMap(),
                                    request = request
                                )
                            )
                        )
                    }

                    override fun onFullScreenVideoCached() {}
                }
            )
        }
    }

    /**
     * Attempt to load a Pangle rewarded ad.
     * @param context The current [Context].
     * @param request The [AdLoadRequest] containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadRewardedAd(
        context: Context,
        request: AdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        // Save the listener for later usage.
        listeners[request.heliumPlacement] = partnerAdListener

        return suspendCoroutine { continuation ->
            val rewardedAd = TTAdSdk.getAdManager().createAdNative(context)
            val adSlot = AdSlot.Builder()
                .setCodeId(request.partnerPlacement)
                .build()

            rewardedAd.loadRewardVideoAd(
                adSlot, object : TTAdNative.RewardVideoAdListener {
                    override fun onError(code: Int, message: String?) {
                        LogController.d(
                            "Failed to load Pangle rewarded ad. " +
                                    "Pangle code: $code with message: $message"
                        )
                        continuation.resume(
                            Result.failure(
                                HeliumAdException(HeliumErrorCode.NO_FILL)
                            )
                        )
                    }

                    override fun onRewardVideoAdLoad(videoAd: TTRewardVideoAd?) {
                        continuation.resume(
                            Result.success(
                                PartnerAd(
                                    ad = videoAd,
                                    details = emptyMap(),
                                    request = request
                                )
                            )
                        )
                    }

                    override fun onRewardVideoCached() {
                        // NO-OP
                    }
                }
            )
        }
    }

    /**
     * Attempt to show a Pangle interstitial ad.
     * @param activity The current [Activity].
     * @param partnerAd The [PartnerAd] object containing the Pangle ad to be shown.
     * @param listener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    private fun showInterstitialAd(
        activity: Activity,
        partnerAd: PartnerAd,
        listener: PartnerAdListener?
    ): Result<PartnerAd> {
        return (partnerAd.ad as? TTFullScreenVideoAd)?.let { ad ->
            val pangleVideoListener =
                object : TTFullScreenVideoAd.FullScreenVideoAdInteractionListener {
                    override fun onAdShow() {
                        Result.success(partnerAd)
                    }

                    override fun onAdVideoBarClick() {
                        listener?.onPartnerAdClicked(partnerAd) ?: run {
                            LogController.e(
                                "Unable to fire onPartnerAdClicked for Pangle adapter. " +
                                        "Listener is null."
                            )
                        }
                    }

                    override fun onAdClose() {
                        listener?.onPartnerAdDismissed(partnerAd, null) ?: run {
                            LogController.e(
                                "Unable to fire onPartnerAdDismissed for Pangle adapter. " +
                                        "Listener is null."
                            )
                        }
                    }

                    override fun onVideoComplete() {
                        // NO-OP
                    }

                    override fun onSkippedVideo() {
                        // NO-OP
                    }
                }
            ad.apply {
                setFullScreenVideoAdInteractionListener(pangleVideoListener)
                showFullScreenVideoAd(activity)
            }
            Result.success(partnerAd)
        } ?: run {
            LogController.e("Failed to show Pangle interstitial ad. Ad is null.")
            Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
        }
    }

    /**
     * Attempt to show a Pangle rewarded ad.
     *
     * @param activity The current [Activity].
     * @param partnerAd The [PartnerAd] object containing the Pangle ad to be shown.
     * @param listener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    private fun showRewardedAd(
        activity: Activity,
        partnerAd: PartnerAd,
        listener: PartnerAdListener?
    ): Result<PartnerAd> {
        return (partnerAd.ad as? TTRewardVideoAd)?.let { ad ->
            val pangleVideoListener =
                object : TTRewardVideoAd.RewardAdInteractionListener {
                    override fun onAdShow() {
                        Result.success(partnerAd)
                    }

                    override fun onAdVideoBarClick() {
                        listener?.onPartnerAdClicked(partnerAd) ?: run {
                            LogController.e(
                                "Unable to fire onPartnerAdClicked for Pangle adapter. " +
                                        "Listener is null."
                            )
                        }
                    }

                    override fun onAdClose() {
                        listener?.onPartnerAdDismissed(partnerAd, null) ?: run {
                            LogController.e(
                                "Unable to fire onPartnerAdDismissed for Pangle adapter. " +
                                        "Listener is null."
                            )
                        }
                    }

                    override fun onVideoComplete() {
                        // NO-OP
                    }

                    override fun onVideoError() {
                        // NO-OP
                    }

                    override fun onRewardVerify(
                        rewardVerify: Boolean,
                        rewardAmount: Int,
                        rewardName: String?,
                        errorCode: Int,
                        errorMessage: String?
                    ) {
                        listener?.onPartnerAdRewarded(
                            partnerAd,
                            Reward(rewardAmount, rewardName ?: "")
                        ) ?: run {
                            LogController.e(
                                "Unable to fire onPartnerAdRewarded for Pangle adapter. " +
                                        "Listener is null."
                            )
                        }
                    }

                    override fun onSkippedVideo() {
                        // NO-OP
                    }
                }
            ad.apply {
                setRewardAdInteractionListener(pangleVideoListener)
                showRewardVideoAd(activity)
            }
            Result.success(partnerAd)
        } ?: run {
            LogController.e("Failed to show Pangle rewarded ad. Ad is null.")
            Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
        }
    }

    /**
     * Destroy the current Pangle banner ad.
     *
     * @param partnerAd The [PartnerAd] object containing the Pangle ad to be destroyed.
     *
     * @return Result.success(PartnerAd) if the ad was successfully destroyed, Result.failure(Exception) otherwise.
     */
    private fun destroyBannerAd(partnerAd: PartnerAd): Result<PartnerAd> {
        return (partnerAd.ad as? TTNativeExpressAd)?.let { bannerAd ->
            bannerAd.destroy()
            Result.success(partnerAd)
        } ?: run {
            LogController.w("Failed to destroy Pangle banner ad. Ad is null.")
            Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
        }
    }
}
