package com.chartboost.helium.pangleadapter

import android.app.Activity
import android.content.Context
import android.view.View
import com.bytedance.sdk.openadsdk.*
import com.chartboost.heliumsdk.domain.*
import com.chartboost.heliumsdk.utils.PartnerLogController
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.*
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterFailureEvents.*
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterSuccessEvents.*
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
                PartnerLogController.log(
                    CUSTOM,
                    "Pangle's multi-process support is ${if (value) "enabled" else "disabled"}."
                )
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
        PartnerLogController.log(SETUP_STARTED)

        return suspendCoroutine { continuation ->
            partnerConfiguration.credentials[APPLICATION_ID_KEY]?.let { appId ->
                TTAdSdk.init(
                    context.applicationContext,
                    buildConfig(appId),
                    object : TTAdSdk.InitCallback {
                        override fun success() {
                            continuation.resume(
                                Result.success(
                                    PartnerLogController.log(SETUP_SUCCEEDED)
                                )
                            )
                        }

                        override fun fail(code: Int, message: String?) {
                            PartnerLogController.log(SETUP_FAILED, "Code: $code. Error: $message")
                            continuation.resume(
                                Result.failure(HeliumAdException(HeliumErrorCode.PARTNER_SDK_NOT_INITIALIZED))
                            )
                        }
                    })
            } ?: run {
                PartnerLogController.log(SETUP_FAILED, "Missing application ID.")
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
    ): Map<String, String> {
        PartnerLogController.log(BIDDER_INFO_FETCH_STARTED)
        PartnerLogController.log(BIDDER_INFO_FETCH_SUCCEEDED)
        return emptyMap()
    }

    /**
     * Attempt to load a Pangle ad.
     *
     * @param context The current [Context].
     * @param request An [PartnerAdLoadRequest] instance containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    override suspend fun load(
        context: Context,
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        PartnerLogController.log(LOAD_STARTED)

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
        PartnerLogController.log(SHOW_STARTED)

        val listener = listeners.remove(partnerAd.request.heliumPlacement)
        return when (partnerAd.request.format) {
            AdFormat.BANNER -> {
                // Banner ads do not have a separate "show" mechanism.
                PartnerLogController.log(SHOW_SUCCEEDED)
                Result.success(partnerAd)
            }
            AdFormat.INTERSTITIAL -> {
                (context as? Activity)?.let { activity ->
                    showInterstitialAd(activity, partnerAd, listener)
                } ?: run {
                    PartnerLogController.log(SHOW_FAILED, "Activity context is required.")
                    Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
                }
            }
            AdFormat.REWARDED -> {
                (context as? Activity)?.let { activity ->
                    showRewardedAd(activity, partnerAd, listener)
                } ?: run {
                    PartnerLogController.log(SHOW_FAILED, "Activity context is required.")
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
        PartnerLogController.log(INVALIDATE_STARTED)

        return when (partnerAd.request.format) {
            AdFormat.BANNER -> destroyBannerAd(partnerAd)
            AdFormat.INTERSTITIAL, AdFormat.REWARDED -> {
                // Pangle does not have destroy methods for their fullscreen ads.
                PartnerLogController.log(INVALIDATE_SUCCEEDED)
                Result.success(partnerAd)
            }
        }
    }

    /**
     * Attempt to load a Pangle banner ad.
     *
     * @param context The current [Context].
     * @param request An [PartnerAdLoadRequest] instance containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadBannerAd(
        context: Context,
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        return suspendCoroutine { continuation ->
            val bannerAd = TTAdSdk.getAdManager().createAdNative(context)

            val adSlot = request.size?.let {
                val widthDp = it.width.toFloat()
                val heightDp = it.height.toFloat()

                PartnerLogController.log(
                    CUSTOM,
                    "Pangle setting banner with size (w: $widthDp, h: $heightDp)"
                )

                AdSlot.Builder()
                    .setCodeId(request.partnerPlacement)
                    .setExpressViewAcceptedSize(
                        widthDp,
                        heightDp
                    ).build()
            }

            bannerAd.loadBannerExpressAd(adSlot, object : TTAdNative.NativeExpressAdListener {
                override fun onError(code: Int, message: String?) {
                    PartnerLogController.log(LOAD_FAILED, "Code: $code. Error: $message")
                    continuation.resume(
                        Result.failure(HeliumAdException(HeliumErrorCode.NO_FILL))
                    )
                }

                override fun onNativeExpressAdLoad(bannerAds: MutableList<TTNativeExpressAd>?) {
                    bannerAds?.firstOrNull()?.let { banner ->
                        PartnerLogController.log(LOAD_SUCCEEDED)
                        continuation.resume(
                            Result.success(
                                PartnerAd(
                                    ad = banner,
                                    details = emptyMap(),
                                    request = request
                                )
                            )
                        )

                        banner.setExpressInteractionListener(object :
                            TTNativeExpressAd.ExpressAdInteractionListener {
                            override fun onAdClicked(bannerView: View?, type: Int) {
                                PartnerLogController.log(DID_CLICK)
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
                                PartnerLogController.log(SHOW_FAILED)
                                continuation.resumeWith(
                                    Result.failure(HeliumAdException(HeliumErrorCode.PARTNER_ERROR))
                                )
                            }

                            override fun onRenderSuccess(
                                bannerView: View?,
                                width: Float,
                                height: Float
                            ) {
                                PartnerLogController.log(SHOW_SUCCEEDED)
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
                        PartnerLogController.log(LOAD_FAILED, "No Pangle banner found.")
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
     * @param request An [PartnerAdLoadRequest] instance containing data to load the ad with.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadInterstitialAd(
        context: Context,
        request: PartnerAdLoadRequest,
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
                    // Variable to store a Pangle TTFullScreenVideoAd.
                    var fullScreenAd : TTFullScreenVideoAd? = null

                    override fun onError(code: Int, message: String?) {
                        PartnerLogController.log(
                            LOAD_FAILED,
                            "Code: $code. Error: $message"
                        )
                        continuation.resume(
                            Result.failure(
                                HeliumAdException(HeliumErrorCode.NO_FILL)
                            )
                        )
                    }

                    /**
                     * This method is executed when an ad material is loaded successfully.
                     */
                    override fun onFullScreenVideoAdLoad(videoAd: TTFullScreenVideoAd?) {
                        fullScreenAd = videoAd
                    }

                    /**
                     * This method is executed when the video file has finished loading.
                     */
                    override fun onFullScreenVideoCached() {
                        fullScreenAd?.let {
                            PartnerLogController.log(LOAD_SUCCEEDED)
                            continuation.resume(
                                Result.success(
                                    PartnerAd(
                                        ad = it,
                                        details = emptyMap(),
                                        request = request
                                    )
                                )
                            )
                        } ?: run {
                            PartnerLogController.log(LOAD_FAILED)
                            continuation.resume(
                                Result.failure(
                                    HeliumAdException(HeliumErrorCode.NO_FILL)
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    /**
     * Attempt to load a Pangle rewarded ad.
     * @param context The current [Context].
     * @param request The [PartnerAdLoadRequest] containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Helium of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadRewardedAd(
        context: Context,
        request: PartnerAdLoadRequest,
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
                    // Variable to store a Pangle TTRewardVideoAd.
                    var rewardVideoAd : TTRewardVideoAd? = null

                    override fun onError(code: Int, message: String?) {
                        PartnerLogController.log(
                            LOAD_FAILED,
                            "Code: $code. Error: $message"
                        )
                        continuation.resume(
                            Result.failure(
                                HeliumAdException(HeliumErrorCode.NO_FILL)
                            )
                        )
                    }

                    /**
                     * This method is executed when an ad material is loaded successfully.
                     */
                    override fun onRewardVideoAdLoad(videoAd: TTRewardVideoAd?) {
                        rewardVideoAd = videoAd
                    }

                    /**
                     * This method is executed when the video file has finished loading.
                     */
                    override fun onRewardVideoCached() {
                        rewardVideoAd?.let {
                            PartnerLogController.log(LOAD_SUCCEEDED)
                            continuation.resume(
                                Result.success(
                                    PartnerAd(
                                        ad = it,
                                        details = emptyMap(),
                                        request = request
                                    )
                                )
                            )
                        } ?: run {
                            PartnerLogController.log(LOAD_FAILED)
                            continuation.resume(
                                Result.failure(
                                    HeliumAdException(HeliumErrorCode.NO_FILL)
                                )
                            )
                        }
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
    private suspend fun showInterstitialAd(
        activity: Activity,
        partnerAd: PartnerAd,
        listener: PartnerAdListener?
    ): Result<PartnerAd> {
        (partnerAd.ad as? TTFullScreenVideoAd)?.let { ad ->
            return suspendCoroutine { continuation ->
                val pangleVideoListener =
                    object : TTFullScreenVideoAd.FullScreenVideoAdInteractionListener {
                        override fun onAdShow() {
                            PartnerLogController.log(SHOW_SUCCEEDED)
                            continuation.resume(Result.success(partnerAd))
                        }

                        override fun onAdVideoBarClick() {
                            PartnerLogController.log(DID_CLICK)
                            listener?.onPartnerAdClicked(partnerAd) ?: PartnerLogController.log(
                                CUSTOM,
                                "Unable to fire onPartnerAdClicked for Pangle adapter. " +
                                        "Listener is null."
                            )
                        }

                        override fun onAdClose() {
                            PartnerLogController.log(DID_TRACK_IMPRESSION)
                            listener?.onPartnerAdDismissed(partnerAd, null)
                                ?: PartnerLogController.log(
                                    CUSTOM,
                                    "Unable to fire onPartnerAdDismissed for Pangle adapter. " +
                                            "Listener is null."
                                )
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
            }
        } ?: run {
            PartnerLogController.log(SHOW_FAILED, "Ad is null.")
            return Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
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
    private suspend fun showRewardedAd(
        activity: Activity,
        partnerAd: PartnerAd,
        listener: PartnerAdListener?
    ): Result<PartnerAd> {
        (partnerAd.ad as? TTRewardVideoAd)?.let { ad ->
            return suspendCoroutine { continuation ->
                val pangleVideoListener =
                    object : TTRewardVideoAd.RewardAdInteractionListener {
                        override fun onAdShow() {
                            PartnerLogController.log(SHOW_SUCCEEDED)
                            continuation.resume(Result.success(partnerAd))
                        }

                        override fun onAdVideoBarClick() {
                            PartnerLogController.log(DID_CLICK)
                            listener?.onPartnerAdClicked(partnerAd) ?: PartnerLogController.log(
                                CUSTOM,
                                "Unable to fire onPartnerAdClicked for Pangle adapter. Listener is null."
                            )
                        }

                        override fun onAdClose() {
                            PartnerLogController.log(DID_DISMISS)
                            listener?.onPartnerAdDismissed(partnerAd, null)
                                ?: PartnerLogController.log(
                                    CUSTOM,
                                    "Unable to fire onPartnerAdDismissed for Pangle adapter. Listener is null."
                                )
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
                            PartnerLogController.log(DID_REWARD)
                            listener?.onPartnerAdRewarded(
                                partnerAd,
                                Reward(rewardAmount, rewardName ?: "")
                            ) ?: PartnerLogController.log(
                                CUSTOM,
                                "Unable to fire onPartnerAdRewarded for Pangle adapter. " +
                                        "Listener is null."
                            )
                        }

                        override fun onSkippedVideo() {
                            // NO-OP
                        }
                    }

                ad.apply {
                    setRewardAdInteractionListener(pangleVideoListener)
                    showRewardVideoAd(activity)
                }
            }
        } ?: run {
            PartnerLogController.log(SHOW_FAILED, "Ad is null.")
            return Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
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

            PartnerLogController.log(INVALIDATE_SUCCEEDED)
            Result.success(partnerAd)
        } ?: run {
            PartnerLogController.log(INVALIDATE_FAILED, "Ad is null.")
            Result.failure(HeliumAdException(HeliumErrorCode.INTERNAL))
        }
    }
}
