/*
 * Copyright 2022-2023 Chartboost, Inc.
 * 
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.mediation.pangleadapter

import android.app.Activity
import android.content.Context
import android.view.View
import com.bytedance.sdk.openadsdk.*
import com.chartboost.heliumsdk.domain.*
import com.chartboost.heliumsdk.utils.PartnerLogController
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The Chartboost Mediation Pangle SDK adapter.
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
     * Get the Pangle SDK version.
     */
    override val partnerSdkVersion: String
        get() = TTAdSdk.getAdManager().sdkVersion

    /**
     * Get the Pangle adapter version.
     *
     * You may version the adapter using any preferred convention, but it is recommended to apply the
     * following format if the adapter will be published by Chartboost Mediation:
     *
     * Chartboost Mediation.Partner.Adapter
     *
     * "Chartboost Mediation" represents the Chartboost Mediation SDK’s major version that is compatible with this adapter. This must be 1 digit.
     * "Partner" represents the partner SDK’s major.minor.patch.x (where x is optional) version that is compatible with this adapter. This can be 3-4 digits.
     * "Adapter" represents this adapter’s version (starting with 0), which resets to 0 when the partner SDK’s version changes. This must be 1 digit.
     */
    override val adapterVersion: String
        get() = BuildConfig.CHARTBOOST_MEDIATION_PANGLE_ADAPTER_VERSION

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
     * A map of Chartboost Mediation's listeners for the corresponding Chartboost placements.
     */
    private val listeners = mutableMapOf<String, PartnerAdListener>()

    /**
     * A set of banners so we can keep track of which ads to destroy.
     */
    private val loadedBanners = mutableSetOf<WeakReference<TTNativeExpressAd>>()

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
            Json.decodeFromJsonElement<String>(
                (partnerConfiguration.credentials as JsonObject).getValue(APPLICATION_ID_KEY)
            )
                .trim()
                .takeIf { it.isNotEmpty() }?.let { appId ->
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
                                PartnerLogController.log(
                                    SETUP_FAILED,
                                    "Code: $code. Error: $message"
                                )
                                continuation.resume(
                                    Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_INITIALIZATION_FAILURE_UNKNOWN))
                                )
                            }
                        })
                } ?: run {
                PartnerLogController.log(SETUP_FAILED, "Missing application ID.")
                continuation.resumeWith(Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_INITIALIZATION_FAILURE_INVALID_CREDENTIALS)))
            }
        }
    }

    /**
     * Build the necessary configurations that the Pangle SDK requires on [setUp].
     *
     * @param appId an application ID that will be set in the configuration.
     *
     * @return a [TTAdConfig] object with the necessary configurations.
     */
    private fun buildConfig(appId: String) = TTAdConfig.Builder()
        .appId(appId)
        .supportMultiProcess(multiProcessSupport)
        .data("[{\"name\":\"mediation\",\"value\":\"Chartboost\"},{\"name\":\"adapter_version\",\"value\":\"$adapterVersion\"}]")
        .build()

    /**
     * Notify the Pangle SDK of the GDPR applicability and consent status.
     *
     * @param context The current [Context].
     * @param applies True if GDPR applies, false otherwise.
     * @param gdprConsentStatus The user's GDPR consent status.
     */
    override fun setGdpr(
        context: Context,
        applies: Boolean?,
        gdprConsentStatus: GdprConsentStatus
    ) {
        PartnerLogController.log(
            when (applies) {
                true -> GDPR_APPLICABLE
                false -> GDPR_NOT_APPLICABLE
                else -> GDPR_UNKNOWN
            }
        )

        TTAdSdk.setGdpr(
            when (gdprConsentStatus) {
                GdprConsentStatus.GDPR_CONSENT_GRANTED -> {
                    PartnerLogController.log(GDPR_CONSENT_GRANTED)
                    0
                }
                GdprConsentStatus.GDPR_CONSENT_DENIED -> {
                    PartnerLogController.log(GDPR_CONSENT_DENIED)
                    1
                }
                GdprConsentStatus.GDPR_CONSENT_UNKNOWN -> {
                    PartnerLogController.log(GDPR_CONSENT_UNKNOWN)
                    -1
                }
            }
        )
    }

    /**
     * Notify Pangle of the CCPA compliance.
     *
     * @param context The current [Context].
     * @param hasGrantedCcpaConsent True if the user has granted CCPA consent, false otherwise.
     * @param privacyString The CCPA privacy String.
     */
    override fun setCcpaConsent(
        context: Context,
        hasGrantedCcpaConsent: Boolean,
        privacyString: String
    ) {
        TTAdSdk.setCCPA(
            when (hasGrantedCcpaConsent) {
                true -> {
                    PartnerLogController.log(CCPA_CONSENT_GRANTED)
                    0
                }
                false -> {
                    PartnerLogController.log(CCPA_CONSENT_DENIED)
                    1
                }
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
                true -> {
                    PartnerLogController.log(COPPA_SUBJECT)
                    1
                }
                false -> {
                    PartnerLogController.log(COPPA_NOT_SUBJECT)
                    0
                }
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
     * @param partnerAdListener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
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
            AdFormat.REWARDED_INTERSTITIAL -> {
                PartnerLogController.log(LOAD_FAILED)
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_LOAD_FAILURE_UNSUPPORTED_AD_FORMAT))
            }
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

        val listener = listeners.remove(partnerAd.request.chartboostPlacement)
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
                    Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_SHOW_FAILURE_ACTIVITY_NOT_FOUND))
                }
            }
            AdFormat.REWARDED -> {
                (context as? Activity)?.let { activity ->
                    showRewardedAd(activity, partnerAd, listener)
                } ?: run {
                    PartnerLogController.log(SHOW_FAILED, "Activity context is required.")
                    Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_SHOW_FAILURE_ACTIVITY_NOT_FOUND))
                }
            }
            AdFormat.REWARDED_INTERSTITIAL -> {
                PartnerLogController.log(SHOW_FAILED)
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_SHOW_FAILURE_UNSUPPORTED_AD_FORMAT))
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
            AdFormat.REWARDED_INTERSTITIAL -> {
                PartnerLogController.log(LOAD_FAILED)
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_LOAD_FAILURE_UNSUPPORTED_AD_FORMAT))
            }
        }
    }

    /**
     * Attempt to load a Pangle banner ad.
     *
     * @param context The current [Context].
     * @param request An [PartnerAdLoadRequest] instance containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
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
                        Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_LOAD_FAILURE_UNKNOWN))
                    )
                }

                override fun onNativeExpressAdLoad(bannerAds: MutableList<TTNativeExpressAd>?) {
                    bannerAds?.firstOrNull()?.let { banner ->
                        PartnerLogController.log(LOAD_SUCCEEDED)

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
                                // NO-OP
                            }

                            override fun onRenderFail(
                                bannerView: View?,
                                message: String?,
                                code: Int
                            ) {
                                banner.destroy()
                                PartnerLogController.log(SHOW_FAILED)
                            }

                            override fun onRenderSuccess(
                                bannerView: View?,
                                width: Float,
                                height: Float
                            ) {
                                loadedBanners.add(WeakReference(banner))
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
                                ChartboostMediationAdException(ChartboostMediationError.CM_SHOW_FAILURE_AD_NOT_FOUND)
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
     * @param partnerAdListener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadInterstitialAd(
        context: Context,
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        // Save the listener for later usage.
        listeners[request.chartboostPlacement] = partnerAdListener

        return suspendCoroutine { continuation ->
            val interstitialAd = TTAdSdk.getAdManager().createAdNative(context)
            val adSlot = AdSlot.Builder()
                .setCodeId(request.partnerPlacement)
                .build()
            interstitialAd.loadFullScreenVideoAd(
                adSlot, object : TTAdNative.FullScreenVideoAdListener {
                    var fullScreenAd: TTFullScreenVideoAd? = null

                    override fun onError(code: Int, message: String?) {
                        PartnerLogController.log(
                            LOAD_FAILED,
                            "Code: $code. Error: $message"
                        )
                        continuation.resume(
                            Result.failure(
                                ChartboostMediationAdException(ChartboostMediationError.CM_LOAD_FAILURE_UNKNOWN)
                            )
                        )
                    }

                    override fun onFullScreenVideoAdLoad(videoAd: TTFullScreenVideoAd?) {
                        fullScreenAd = videoAd
                    }

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
                                    ChartboostMediationAdException(ChartboostMediationError.CM_LOAD_FAILURE_MISMATCHED_AD_PARAMS)
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
     *
     * @param context The current [Context].
     * @param request The [PartnerAdLoadRequest] containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadRewardedAd(
        context: Context,
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        // Save the listener for later usage.
        listeners[request.chartboostPlacement] = partnerAdListener

        return suspendCoroutine { continuation ->
            val rewardedAd = TTAdSdk.getAdManager().createAdNative(context)
            val adSlot = AdSlot.Builder()
                .setCodeId(request.partnerPlacement)
                .build()

            rewardedAd.loadRewardVideoAd(
                adSlot, object : TTAdNative.RewardVideoAdListener {
                    var rewardVideoAd: TTRewardVideoAd? = null

                    override fun onError(code: Int, message: String?) {
                        PartnerLogController.log(
                            LOAD_FAILED,
                            "Code: $code. Error: $message"
                        )
                        continuation.resume(
                            Result.failure(
                                ChartboostMediationAdException(ChartboostMediationError.CM_LOAD_FAILURE_UNKNOWN)
                            )
                        )
                    }

                    override fun onRewardVideoAdLoad(videoAd: TTRewardVideoAd?) {
                        rewardVideoAd = videoAd
                    }

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
                                    ChartboostMediationAdException(ChartboostMediationError.CM_LOAD_FAILURE_MISMATCHED_AD_PARAMS)
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
     *
     * @param activity The current [Activity].
     * @param partnerAd The [PartnerAd] object containing the Pangle ad to be shown.
     * @param listener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
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
            return Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_SHOW_FAILURE_AD_NOT_FOUND))
        }
    }

    /**
     * Attempt to show a Pangle rewarded ad.
     *
     * @param activity The current [Activity].
     * @param partnerAd The [PartnerAd] object containing the Pangle ad to be shown.
     * @param listener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
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
                            listener?.onPartnerAdRewarded(partnerAd) ?: PartnerLogController.log(
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
            return Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_SHOW_FAILURE_AD_NOT_FOUND))
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
        return (partnerAd.ad as? View)?.let { bannerView ->
            with(loadedBanners.iterator()) {
                forEach {
                    val bannerAd = it.get()
                    if (bannerAd == null) {
                        remove()
                        return@forEach
                    }
                    if (bannerView == bannerAd.expressAdView) {
                        bannerAd.destroy()
                        remove()
                    }
                }
            }

            PartnerLogController.log(INVALIDATE_SUCCEEDED)
            Result.success(partnerAd)
        } ?: run {
            PartnerLogController.log(INVALIDATE_FAILED, "Ad is null.")
            Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_INVALIDATE_FAILURE_AD_NOT_FOUND))
        }
    }
}
