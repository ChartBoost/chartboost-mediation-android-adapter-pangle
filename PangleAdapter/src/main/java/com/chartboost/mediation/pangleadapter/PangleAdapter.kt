/*
 * Copyright 2022-2023 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.mediation.pangleadapter

import android.app.Activity
import android.content.Context
import android.util.Size
import android.view.View
import com.bytedance.sdk.openadsdk.api.PAGClientBidding
import com.bytedance.sdk.openadsdk.api.PAGConstant
import com.bytedance.sdk.openadsdk.api.banner.PAGBannerAd
import com.bytedance.sdk.openadsdk.api.banner.PAGBannerAdInteractionListener
import com.bytedance.sdk.openadsdk.api.banner.PAGBannerAdLoadListener
import com.bytedance.sdk.openadsdk.api.banner.PAGBannerRequest
import com.bytedance.sdk.openadsdk.api.banner.PAGBannerSize
import com.bytedance.sdk.openadsdk.api.init.PAGConfig
import com.bytedance.sdk.openadsdk.api.init.PAGSdk
import com.bytedance.sdk.openadsdk.api.interstitial.PAGInterstitialAd
import com.bytedance.sdk.openadsdk.api.interstitial.PAGInterstitialAdInteractionListener
import com.bytedance.sdk.openadsdk.api.interstitial.PAGInterstitialAdLoadListener
import com.bytedance.sdk.openadsdk.api.interstitial.PAGInterstitialRequest
import com.bytedance.sdk.openadsdk.api.reward.PAGRewardItem
import com.bytedance.sdk.openadsdk.api.reward.PAGRewardedAd
import com.bytedance.sdk.openadsdk.api.reward.PAGRewardedAdInteractionListener
import com.bytedance.sdk.openadsdk.api.reward.PAGRewardedAdLoadListener
import com.bytedance.sdk.openadsdk.api.reward.PAGRewardedRequest
import com.chartboost.heliumsdk.domain.AdFormat
import com.chartboost.heliumsdk.domain.ChartboostMediationAdException
import com.chartboost.heliumsdk.domain.ChartboostMediationError
import com.chartboost.heliumsdk.domain.GdprConsentStatus
import com.chartboost.heliumsdk.domain.PartnerAd
import com.chartboost.heliumsdk.domain.PartnerAdListener
import com.chartboost.heliumsdk.domain.PartnerAdLoadRequest
import com.chartboost.heliumsdk.domain.PartnerAdapter
import com.chartboost.heliumsdk.domain.PartnerConfiguration
import com.chartboost.heliumsdk.domain.PreBidRequest
import com.chartboost.heliumsdk.utils.PartnerLogController
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.BIDDER_INFO_FETCH_STARTED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.BIDDER_INFO_FETCH_SUCCEEDED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.CCPA_CONSENT_DENIED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.CCPA_CONSENT_GRANTED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.COPPA_NOT_SUBJECT
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.COPPA_SUBJECT
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.CUSTOM
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.DID_CLICK
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.DID_DISMISS
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.DID_REWARD
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.DID_TRACK_IMPRESSION
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.GDPR_APPLICABLE
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.GDPR_CONSENT_DENIED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.GDPR_CONSENT_GRANTED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.GDPR_CONSENT_UNKNOWN
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.GDPR_NOT_APPLICABLE
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.GDPR_UNKNOWN
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.INVALIDATE_FAILED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.INVALIDATE_STARTED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.INVALIDATE_SUCCEEDED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.LOAD_FAILED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.LOAD_STARTED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.LOAD_SUCCEEDED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.SETUP_FAILED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.SETUP_STARTED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.SETUP_SUCCEEDED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.SHOW_FAILED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.SHOW_STARTED
import com.chartboost.heliumsdk.utils.PartnerLogController.PartnerAdapterEvents.SHOW_SUCCEEDED
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
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
     * A lambda to call for successful Pangle ad shows.
     */
    private var onShowSuccess: () -> Unit = {}

    /**
     * Get the Pangle SDK version.
     */
    override val partnerSdkVersion: String
        get() = PAGSdk.getSDKVersion()

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

        return suspendCancellableCoroutine { continuation ->
            fun resumeOnce(result: Result<Unit>) {
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            Json.decodeFromJsonElement<String>(
                (partnerConfiguration.credentials as JsonObject).getValue(APPLICATION_ID_KEY)
            )
                .trim()
                .takeIf { it.isNotEmpty() }?.let { appId ->
                    PAGSdk.init(
                        context,
                        buildConfig(appId),
                        object : PAGSdk.PAGInitCallback {
                            override fun success() {
                                resumeOnce(
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
                                resumeOnce(
                                    Result.failure(
                                        ChartboostMediationAdException(
                                            ChartboostMediationError.CM_INITIALIZATION_FAILURE_UNKNOWN
                                        )
                                    )
                                )
                            }
                        })
                } ?: run {
                PartnerLogController.log(SETUP_FAILED, "Missing application ID.")
                resumeOnce(Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_INITIALIZATION_FAILURE_INVALID_CREDENTIALS)))
            }
        }
    }

    /**
     * Build the necessary configurations that the Pangle SDK requires on [setUp].
     *
     * @param appId an application ID that will be set in the configuration.
     *
     * @return a [PAGConfig] object with the necessary configurations.
     */
    private fun buildConfig(appId: String) = PAGConfig.Builder()
        .appId(appId)
        .supportMultiProcess(multiProcessSupport)
        .setUserData("[{\"name\":\"mediation\",\"value\":\"Chartboost\"},{\"name\":\"adapter_version\",\"value\":\"$adapterVersion\"}]")
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

        PAGConfig.setGDPRConsent(
            when (gdprConsentStatus) {
                GdprConsentStatus.GDPR_CONSENT_GRANTED -> {
                    PartnerLogController.log(GDPR_CONSENT_GRANTED)
                    PAGConstant.PAGGDPRConsentType.PAG_GDPR_CONSENT_TYPE_CONSENT
                }

                GdprConsentStatus.GDPR_CONSENT_DENIED -> {
                    PartnerLogController.log(GDPR_CONSENT_DENIED)
                    PAGConstant.PAGGDPRConsentType.PAG_GDPR_CONSENT_TYPE_NO_CONSENT
                }

                GdprConsentStatus.GDPR_CONSENT_UNKNOWN -> {
                    PartnerLogController.log(GDPR_CONSENT_UNKNOWN)
                    PAGConstant.PAGGDPRConsentType.PAG_GDPR_CONSENT_TYPE_DEFAULT
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
        PAGConfig.setDoNotSell(
            when (hasGrantedCcpaConsent) {
                true -> {
                    PartnerLogController.log(CCPA_CONSENT_GRANTED)
                    PAGConstant.PAGDoNotSellType.PAG_DO_NOT_SELL_TYPE_SELL
                }

                false -> {
                    PartnerLogController.log(CCPA_CONSENT_DENIED)
                    PAGConstant.PAGDoNotSellType.PAG_DO_NOT_SELL_TYPE_NOT_SELL
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
        PAGConfig.setChildDirected(
            when (isSubjectToCoppa) {
                true -> {
                    PartnerLogController.log(COPPA_SUBJECT)
                    PAGConstant.PAGChildDirectedType.PAG_CHILD_DIRECTED_TYPE_CHILD
                }

                false -> {
                    PartnerLogController.log(COPPA_NOT_SUBJECT)
                    PAGConstant.PAGChildDirectedType.PAG_CHILD_DIRECTED_TYPE_NON_CHILD
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

        return when (request.format.key) {
            AdFormat.BANNER.key, "adaptive_banner" -> loadBannerAd(request, partnerAdListener)
            AdFormat.INTERSTITIAL.key -> loadInterstitialAd(request, partnerAdListener)
            AdFormat.REWARDED.key -> loadRewardedAd(request, partnerAdListener)
            else -> {
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

        return when (partnerAd.request.format.key) {
            AdFormat.BANNER.key, "adaptive_banner" -> {
                // Banner ads do not have a separate "show" mechanism.
                PartnerLogController.log(SHOW_SUCCEEDED)
                Result.success(partnerAd)
            }
            AdFormat.INTERSTITIAL.key, AdFormat.REWARDED.key -> {
                (context as? Activity)?.let { activity ->
                    showFullscreenAd(activity, partnerAd)
                    Result.success(partnerAd)
                } ?: run {
                    PartnerLogController.log(SHOW_FAILED, "Activity context is required.")
                    Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_SHOW_FAILURE_ACTIVITY_NOT_FOUND))
                }
            }
            else -> {
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

        return when (partnerAd.request.format.key) {
            AdFormat.BANNER.key, "adaptive_banner" -> destroyBannerAd(partnerAd)
            AdFormat.INTERSTITIAL.key, AdFormat.REWARDED.key -> {
                // Pangle does not have destroy methods for their fullscreen ads.
                PartnerLogController.log(INVALIDATE_SUCCEEDED)
                Result.success(partnerAd)
            }
            else -> {
                PartnerLogController.log(INVALIDATE_SUCCEEDED)
                Result.success(partnerAd)
            }
        }
    }

    /**
     * Attempt to load a Pangle banner ad.
     *
     * @param request An [PartnerAdLoadRequest] instance containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadBannerAd(
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        return suspendCoroutine { continuation ->
            PAGBannerAd.loadAd(
                request.partnerPlacement,
                PAGBannerRequest(getPangleBannerSize(request.size)),
                object : PAGBannerAdLoadListener {
                    override fun onError(code: Int, message: String?) {
                        PartnerLogController.log(LOAD_FAILED, "Code: $code. Error: $message")
                        continuation.resume(
                            Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_LOAD_FAILURE_UNKNOWN))
                        )
                    }

                    override fun onAdLoaded(pagBannerAd: PAGBannerAd?) {
                        PartnerLogController.log(LOAD_SUCCEEDED)
                        pagBannerAd?.let {
                            it.setAdInteractionListener(object : PAGBannerAdInteractionListener {
                                override fun onAdShowed() {
                                    PartnerLogController.log(DID_TRACK_IMPRESSION)
                                    partnerAdListener.onPartnerAdImpression(
                                        PartnerAd(
                                            ad = it,
                                            details = emptyMap(),
                                            request = request,
                                        )
                                    )
                                }

                                override fun onAdClicked() {
                                    partnerAdListener.onPartnerAdClicked(
                                        PartnerAd(
                                            ad = it,
                                            details = emptyMap(),
                                            request = request
                                        )
                                    )
                                }

                                override fun onAdDismissed() {}
                            })

                            continuation.resume(
                                Result.success(
                                    PartnerAd(
                                        ad = it.bannerView,
                                        details = emptyMap(),
                                        request = request
                                    )
                                )
                            )
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
     * @param request An [PartnerAdLoadRequest] instance containing data to load the ad with.
     * @param partnerAdListener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadInterstitialAd(
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        return suspendCancellableCoroutine { continuation ->
            fun resumeOnce(result: Result<PartnerAd>) {
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            PAGInterstitialAd.loadAd(
                request.partnerPlacement,
                PAGInterstitialRequest(),
                object : PAGInterstitialAdLoadListener {
                    override fun onError(code: Int, message: String) {
                        PartnerLogController.log(
                            LOAD_FAILED,
                            "Code: $code. Error: $message"
                        )
                        resumeOnce(
                            Result.failure(
                                ChartboostMediationAdException(ChartboostMediationError.CM_LOAD_FAILURE_UNKNOWN)
                            )
                        )
                    }

                    override fun onAdLoaded(pagInterstitialAd: PAGInterstitialAd?) {
                        pagInterstitialAd?.let {
                            val partnerAd = PartnerAd(
                                ad = it,
                                details = emptyMap(),
                                request = request,
                            )
                            it.setAdInteractionListener(object :
                                PAGInterstitialAdInteractionListener {
                                override fun onAdShowed() {
                                    onShowSuccess()
                                }

                                override fun onAdClicked() {
                                    PartnerLogController.log(DID_CLICK)
                                    partnerAdListener.onPartnerAdClicked(partnerAd)
                                }

                                override fun onAdDismissed() {
                                    PartnerLogController.log(DID_DISMISS)
                                    partnerAdListener.onPartnerAdDismissed(partnerAd, null)
                                }
                            })
                            PartnerLogController.log(LOAD_SUCCEEDED)
                            resumeOnce(Result.success(partnerAd))
                        } ?: run {
                            PartnerLogController.log(LOAD_FAILED)
                            resumeOnce(
                                Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_LOAD_FAILURE_MISMATCHED_AD_PARAMS))
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
     * @param request The [PartnerAdLoadRequest] containing relevant data for the current ad load call.
     * @param partnerAdListener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully loaded, Result.failure(Exception) otherwise.
     */
    private suspend fun loadRewardedAd(
        request: PartnerAdLoadRequest,
        partnerAdListener: PartnerAdListener
    ): Result<PartnerAd> {
        return suspendCancellableCoroutine { continuation ->
            fun resumeOnce(result: Result<PartnerAd>) {
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            PAGRewardedAd.loadAd(
                request.partnerPlacement,
                PAGRewardedRequest(),
                object : PAGRewardedAdLoadListener {
                    override fun onError(code: Int, message: String) {
                        PartnerLogController.log(
                            LOAD_FAILED,
                            "Code: $code. Error: $message"
                        )
                        resumeOnce(
                            Result.failure(
                                ChartboostMediationAdException(ChartboostMediationError.CM_LOAD_FAILURE_UNKNOWN)
                            )
                        )
                    }

                    override fun onAdLoaded(pagRewardedAd: PAGRewardedAd?) {
                        pagRewardedAd?.let {
                            val partnerAd = PartnerAd(
                                ad = it,
                                details = emptyMap(),
                                request = request,
                            )
                            it.setAdInteractionListener(object : PAGRewardedAdInteractionListener {
                                override fun onAdShowed() {
                                    onShowSuccess()
                                }

                                override fun onAdClicked() {
                                    PartnerLogController.log(DID_CLICK)
                                    partnerAdListener.onPartnerAdClicked(partnerAd)
                                }

                                override fun onAdDismissed() {
                                    PartnerLogController.log(DID_DISMISS)
                                    partnerAdListener.onPartnerAdDismissed(partnerAd, null)
                                }

                                override fun onUserEarnedReward(rewardItem: PAGRewardItem) {
                                    PartnerLogController.log(DID_REWARD)
                                    partnerAdListener.onPartnerAdRewarded(partnerAd)
                                }

                                override fun onUserEarnedRewardFail(code: Int, message: String?) {}
                            })
                            PartnerLogController.log(LOAD_SUCCEEDED)
                            resumeOnce(Result.success(partnerAd))
                        } ?: run {
                            PartnerLogController.log(LOAD_FAILED)
                            resumeOnce(
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
     * Attempt to show a Pangle fullscreen ad.
     *
     * @param activity The current [Activity].
     * @param partnerAd The [PartnerAd] object containing the Pangle ad to be shown.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    private suspend fun showFullscreenAd(
        activity: Activity,
        partnerAd: PartnerAd,
    ): Result<PartnerAd> {
        return suspendCancellableCoroutine { continuation ->
            fun resumeOnce(result: Result<PartnerAd>) {
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            onShowSuccess = {
                PartnerLogController.log(SHOW_SUCCEEDED)
                resumeOnce(Result.success(partnerAd))
            }

            when (val ad = partnerAd.ad) {
                is PAGInterstitialAd -> {
                    ad.show(activity)
                }
                is PAGRewardedAd -> {
                    ad.show(activity)
                }
                else -> {
                    PartnerLogController.log(
                        SHOW_FAILED,
                        "Ad is not an instance of PAGInterstitialAd or PAGRewardedAd."
                    )
                    resumeOnce(
                        Result.failure(
                            ChartboostMediationAdException(ChartboostMediationError.CM_SHOW_FAILURE_WRONG_RESOURCE_TYPE)
                        )
                    )
                }
            }
        }
    }

    /**
     * Convert a Chartboost Mediation banner size into the corresponding Pangle banner size.
     *
     * @param size The Chartboost Mediation banner size.
     *
     * @return The Pangle banner size.
     */
    private fun getPangleBannerSize(size: Size?): PAGBannerSize {
        return size?.height?.let {
            when {
                it in 50 until 90 -> PAGBannerSize.BANNER_W_320_H_50
                it in 90 until 250 -> PAGBannerSize.BANNER_W_728_H_90
                it >= 250 -> PAGBannerSize.BANNER_W_300_H_250
                else -> PAGBannerSize.BANNER_W_320_H_50
            }
        } ?: PAGBannerSize.BANNER_W_320_H_50
    }

    /**
     * Destroy the current Pangle banner ad.
     *
     * @param partnerAd The [PartnerAd] object containing the Pangle ad to be destroyed.
     *
     * @return Result.success(PartnerAd) if the ad was successfully destroyed, Result.failure(Exception) otherwise.
     */
    private fun destroyBannerAd(partnerAd: PartnerAd): Result<PartnerAd> {
        return (partnerAd.ad as? PAGBannerAd)?.let { bannerView ->
            bannerView.destroy()

            PartnerLogController.log(INVALIDATE_SUCCEEDED)
            Result.success(partnerAd)
        } ?: run {
            PartnerLogController.log(INVALIDATE_FAILED, "Ad is null.")
            Result.failure(ChartboostMediationAdException(ChartboostMediationError.CM_INVALIDATE_FAILURE_AD_NOT_FOUND))
        }
    }
}
