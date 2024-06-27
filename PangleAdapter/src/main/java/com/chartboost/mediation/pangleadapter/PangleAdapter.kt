/*
 * Copyright 2023-2024 Chartboost, Inc.
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

package com.chartboost.mediation.pangleadapter

import android.app.Activity
import android.content.Context
import android.util.Size
import com.bytedance.sdk.openadsdk.api.PAGConstant
import com.bytedance.sdk.openadsdk.api.banner.*
import com.bytedance.sdk.openadsdk.api.init.PAGConfig
import com.bytedance.sdk.openadsdk.api.init.PAGSdk
import com.bytedance.sdk.openadsdk.api.interstitial.PAGInterstitialAd
import com.bytedance.sdk.openadsdk.api.interstitial.PAGInterstitialAdInteractionListener
import com.bytedance.sdk.openadsdk.api.interstitial.PAGInterstitialAdLoadListener
import com.bytedance.sdk.openadsdk.api.interstitial.PAGInterstitialRequest
import com.bytedance.sdk.openadsdk.api.reward.*
import com.chartboost.chartboostmediationsdk.domain.*
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.BIDDER_INFO_FETCH_STARTED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.BIDDER_INFO_FETCH_SUCCEEDED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.CUSTOM
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.DID_CLICK
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.DID_DISMISS
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.DID_REWARD
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.GDPR_CONSENT_DENIED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.GDPR_CONSENT_GRANTED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.GDPR_CONSENT_UNKNOWN
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.INVALIDATE_FAILED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.INVALIDATE_STARTED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.INVALIDATE_SUCCEEDED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.LOAD_FAILED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.LOAD_STARTED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.LOAD_SUCCEEDED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.SETUP_FAILED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.SETUP_STARTED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.SETUP_SUCCEEDED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.SHOW_FAILED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.SHOW_STARTED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.SHOW_SUCCEEDED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.USER_IS_NOT_UNDERAGE
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.USER_IS_UNDERAGE
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.USP_CONSENT_DENIED
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.USP_CONSENT_GRANTED
import com.chartboost.core.consent.*
import com.chartboost.mediation.pangleadapter.PangleAdapterConfiguration.adapterVersion
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.coroutines.resume

/**
 * The Chartboost Mediation Pangle SDK adapter.
 */
class PangleAdapter : PartnerAdapter {
    companion object {
        /**
         * Key for parsing the Pangle SDK application ID.
         */
        private const val APPLICATION_ID_KEY = "application_id"
    }

    /**
     * The Pangle adapter configuration.
     */
    override var configuration: PartnerAdapterConfiguration = PangleAdapterConfiguration

    /**
     * A map of Chartboost Mediation's listeners for the corresponding load identifier.
     */
    private val listeners = mutableMapOf<String, PartnerAdListener>()

    /**
     * Initialize the Pangle SDK so that it is ready to request ads.
     *
     * @param context The current [Activity].
     * @param partnerConfiguration Configuration object containing relevant data to initialize Pangle.
     *
     * @return Result.success(Unit) if Pangle successfully initialized, Result.failure(Exception) otherwise.
     */
    override suspend fun setUp(
        context: Context,
        partnerConfiguration: PartnerConfiguration,
    ): Result<Map<String, Any>> {
        PartnerLogController.log(SETUP_STARTED)

        return suspendCancellableCoroutine { continuation ->
            fun resumeOnce(result: Result<Map<String, Any>>) {
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            Json.decodeFromJsonElement<String>(
                (partnerConfiguration.credentials as JsonObject).getValue(APPLICATION_ID_KEY),
            )
                .trim()
                .takeIf { it.isNotEmpty() }?.let { appId ->
                    PAGSdk.init(
                        context,
                        buildConfig(appId),
                        object : PAGSdk.PAGInitCallback {
                            override fun success() {
                                PartnerLogController.log(SETUP_SUCCEEDED)
                                resumeOnce(Result.success(emptyMap()))
                            }

                            override fun fail(
                                code: Int,
                                message: String?,
                            ) {
                                PartnerLogController.log(
                                    SETUP_FAILED,
                                    "Code: $code. Error: $message",
                                )
                                resumeOnce(
                                    Result.failure(
                                        ChartboostMediationAdException(
                                            ChartboostMediationError.InitializationError.Unknown,
                                        ),
                                    ),
                                )
                            }
                        },
                    )
                } ?: run {
                PartnerLogController.log(SETUP_FAILED, "Missing application ID.")
                resumeOnce(
                    Result.failure(ChartboostMediationAdException(ChartboostMediationError.InitializationError.InvalidCredentials)),
                )
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
    private fun buildConfig(appId: String) =
        PAGConfig.Builder()
            .appId(appId)
            .supportMultiProcess(PangleAdapterConfiguration.multiProcessSupport)
            .setUserData("[{\"name\":\"mediation\",\"value\":\"Chartboost\"},{\"name\":\"adapter_version\",\"value\":\"$adapterVersion\"}]")
            .build()

    /**
     * Notify Pangle of the COPPA subjectivity.
     *
     * @param context The current [Context].
     * @param isUserUnderage True if the user is subject to COPPA, false otherwise.
     */
    override fun setIsUserUnderage(
        context: Context,
        isUserUnderage: Boolean,
    ) {
        PAGConfig.setChildDirected(
            when (isUserUnderage) {
                true -> {
                    PartnerLogController.log(USER_IS_UNDERAGE)
                    PAGConstant.PAGChildDirectedType.PAG_CHILD_DIRECTED_TYPE_CHILD
                }

                false -> {
                    PartnerLogController.log(USER_IS_NOT_UNDERAGE)
                    PAGConstant.PAGChildDirectedType.PAG_CHILD_DIRECTED_TYPE_NON_CHILD
                }
            },
        )
    }

    /**
     * Get a bid token if network bidding is supported.
     *
     * @param context The current [Context].
     * @param request The [PartnerAdPreBidRequest] instance containing relevant data for the current bid request.
     *
     * @return A Map of biddable token Strings.
     */
    override suspend fun fetchBidderInformation(
        context: Context,
        request: PartnerAdPreBidRequest,
    ): Result<Map<String, String>> {
        PartnerLogController.log(BIDDER_INFO_FETCH_STARTED)
        PartnerLogController.log(BIDDER_INFO_FETCH_SUCCEEDED)
        return Result.success(emptyMap())
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
        partnerAdListener: PartnerAdListener,
    ): Result<PartnerAd> {
        PartnerLogController.log(LOAD_STARTED)

        return when (request.format) {
            PartnerAdFormats.BANNER -> loadBannerAd(request, partnerAdListener)
            PartnerAdFormats.INTERSTITIAL -> loadInterstitialAd(request, partnerAdListener)
            PartnerAdFormats.REWARDED -> loadRewardedAd(request, partnerAdListener)
            else -> {
                PartnerLogController.log(LOAD_FAILED)
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.LoadError.UnsupportedAdFormat))
            }
        }
    }

    /**
     * Attempt to show the currently loaded Pangle ad.
     *
     * @param activity The current [Activity]
     * @param partnerAd The [PartnerAd] object containing the Pangle ad to be shown.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    override suspend fun show(
        activity: Activity,
        partnerAd: PartnerAd,
    ): Result<PartnerAd> {
        PartnerLogController.log(SHOW_STARTED)
        val listener = listeners.remove(partnerAd.request.identifier)

        return when (partnerAd.request.format) {
            PartnerAdFormats.BANNER -> {
                // Banner ads do not have a separate "show" mechanism.
                PartnerLogController.log(SHOW_SUCCEEDED)
                Result.success(partnerAd)
            }

            PartnerAdFormats.INTERSTITIAL, PartnerAdFormats.REWARDED -> {
                showFullscreenAd(activity, partnerAd, listener)
            }
            else -> {
                PartnerLogController.log(SHOW_FAILED)
                Result.failure(ChartboostMediationAdException(ChartboostMediationError.ShowError.UnsupportedAdFormat))
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
        listeners.remove(partnerAd.request.identifier)

        return when (partnerAd.request.format) {
            PartnerAdFormats.BANNER -> destroyBannerAd(partnerAd)
            else -> {
                // Pangle does not have destroy methods for their fullscreen ads.
                PartnerLogController.log(INVALIDATE_SUCCEEDED)
                Result.success(partnerAd)
            }
        }
    }

    override fun setConsents(
        context: Context,
        consents: Map<ConsentKey, ConsentValue>,
        modifiedKeys: Set<ConsentKey>,
    ) {
        consents[ConsentKeys.GDPR_CONSENT_GIVEN]?.let {
            if (PangleAdapterConfiguration.isGdprConsentOverridden) {
                return@let
            }
            PAGConfig.setGDPRConsent(
                when (it) {
                    ConsentValues.GRANTED -> {
                        PartnerLogController.log(GDPR_CONSENT_GRANTED)
                        PAGConstant.PAGGDPRConsentType.PAG_GDPR_CONSENT_TYPE_CONSENT
                    }

                    ConsentValues.DENIED -> {
                        PartnerLogController.log(GDPR_CONSENT_DENIED)
                        PAGConstant.PAGGDPRConsentType.PAG_GDPR_CONSENT_TYPE_NO_CONSENT
                    }

                    else -> {
                        PartnerLogController.log(GDPR_CONSENT_UNKNOWN)
                        PAGConstant.PAGGDPRConsentType.PAG_GDPR_CONSENT_TYPE_DEFAULT
                    }
                },
            )
        }

        consents[ConsentKeys.USP]?.let {
            if (PangleAdapterConfiguration.isDoNotSellOverridden) {
                return@let
            }
            val hasGrantedUspConsent = ConsentManagementPlatform.getUspConsentFromUspString(it)
            PAGConfig.setDoNotSell(
                when (hasGrantedUspConsent) {
                    true -> {
                        PartnerLogController.log(USP_CONSENT_GRANTED)
                        PAGConstant.PAGDoNotSellType.PAG_DO_NOT_SELL_TYPE_SELL
                    }

                    false -> {
                        PartnerLogController.log(USP_CONSENT_DENIED)
                        PAGConstant.PAGDoNotSellType.PAG_DO_NOT_SELL_TYPE_NOT_SELL
                    }
                },
            )
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
        partnerAdListener: PartnerAdListener,
    ): Result<PartnerAd> {
        return suspendCancellableCoroutine { continuation ->
            fun resumeOnce(result: Result<PartnerAd>) {
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            PAGBannerAd.loadAd(
                request.partnerPlacement,
                PAGBannerRequest(getPangleBannerSize(request.bannerSize?.size)),
                object : PAGBannerAdLoadListener {
                    override fun onError(
                        code: Int,
                        message: String?,
                    ) {
                        PartnerLogController.log(LOAD_FAILED, "Code: $code. Error: $message")
                        resumeOnce(
                            Result.failure(ChartboostMediationAdException(ChartboostMediationError.LoadError.Unknown)),
                        )
                    }

                    override fun onAdLoaded(pagBannerAd: PAGBannerAd?) {
                        PartnerLogController.log(LOAD_SUCCEEDED)
                        pagBannerAd?.let { banner ->
                            banner.setAdInteractionListener(
                                object : PAGBannerAdInteractionListener {
                                    override fun onAdShowed() {
                                        // NO-OP
                                    }

                                    override fun onAdClicked() {
                                        partnerAdListener.onPartnerAdClicked(
                                            PartnerAd(
                                                ad = banner,
                                                details = emptyMap(),
                                                request = request,
                                            ),
                                        )
                                    }

                                    override fun onAdDismissed() {
                                        // NO-OP
                                    }
                                },
                            )

                            resumeOnce(
                                Result.success(
                                    PartnerAd(
                                        ad = banner.bannerView,
                                        details = emptyMap(),
                                        request = request,
                                    ),
                                ),
                            )
                        } ?: run {
                            PartnerLogController.log(LOAD_FAILED, "No Pangle banner found.")
                            resumeOnce(
                                Result.failure(
                                    ChartboostMediationAdException(ChartboostMediationError.ShowError.AdNotFound),
                                ),
                            )
                        }
                    }
                },
            )
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
        partnerAdListener: PartnerAdListener,
    ): Result<PartnerAd> {
        // Save the listener for later use.
        listeners[request.identifier] = partnerAdListener

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
                    override fun onError(
                        code: Int,
                        message: String,
                    ) {
                        PartnerLogController.log(
                            LOAD_FAILED,
                            "Code: $code. Error: $message",
                        )
                        resumeOnce(
                            Result.failure(
                                ChartboostMediationAdException(ChartboostMediationError.LoadError.Unknown),
                            ),
                        )
                    }

                    override fun onAdLoaded(pagInterstitialAd: PAGInterstitialAd?) {
                        pagInterstitialAd?.let {
                            PartnerLogController.log(LOAD_SUCCEEDED)
                            resumeOnce(
                                Result.success(
                                    PartnerAd(
                                        ad = it,
                                        details = emptyMap(),
                                        request = request,
                                    ),
                                ),
                            )
                        } ?: run {
                            PartnerLogController.log(LOAD_FAILED)
                            resumeOnce(
                                Result.failure(
                                    ChartboostMediationAdException(ChartboostMediationError.LoadError.MismatchedAdParams),
                                ),
                            )
                        }
                    }
                },
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
        partnerAdListener: PartnerAdListener,
    ): Result<PartnerAd> {
        // Save the listener for later use.
        listeners[request.identifier] = partnerAdListener

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
                    override fun onError(
                        code: Int,
                        message: String,
                    ) {
                        PartnerLogController.log(
                            LOAD_FAILED,
                            "Code: $code. Error: $message",
                        )
                        resumeOnce(
                            Result.failure(
                                ChartboostMediationAdException(ChartboostMediationError.LoadError.Unknown),
                            ),
                        )
                    }

                    override fun onAdLoaded(pagRewardedAd: PAGRewardedAd?) {
                        pagRewardedAd?.let {
                            PartnerLogController.log(LOAD_SUCCEEDED)
                            resumeOnce(
                                Result.success(
                                    PartnerAd(
                                        ad = it,
                                        details = emptyMap(),
                                        request = request,
                                    ),
                                ),
                            )
                        } ?: run {
                            PartnerLogController.log(LOAD_FAILED)
                            resumeOnce(
                                Result.failure(
                                    ChartboostMediationAdException(ChartboostMediationError.LoadError.MismatchedAdParams),
                                ),
                            )
                        }
                    }
                },
            )
        }
    }

    /**
     * Attempt to show a Pangle fullscreen ad.
     *
     * @param activity The current [Activity].
     * @param partnerAd The [PartnerAd] object containing the Pangle ad to be shown.
     * @param partnerAdListener A [PartnerAdListener] to notify Chartboost Mediation of ad events.
     *
     * @return Result.success(PartnerAd) if the ad was successfully shown, Result.failure(Exception) otherwise.
     */
    private suspend fun showFullscreenAd(
        activity: Activity,
        partnerAd: PartnerAd,
        partnerAdListener: PartnerAdListener?,
    ): Result<PartnerAd> {
        return suspendCancellableCoroutine { continuation ->
            fun resumeOnce(result: Result<PartnerAd>) {
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            when (val ad = partnerAd.ad) {
                is PAGInterstitialAd -> {
                    ad.setAdInteractionListener(
                        object :
                            PAGInterstitialAdInteractionListener {
                            override fun onAdShowed() {
                                PartnerLogController.log(SHOW_SUCCEEDED)
                                resumeOnce(Result.success(partnerAd))
                            }

                            override fun onAdClicked() {
                                PartnerLogController.log(DID_CLICK)
                                partnerAdListener?.onPartnerAdClicked(partnerAd) ?: run {
                                    PartnerLogController.log(
                                        CUSTOM,
                                        "Unable to fire onPartnerAdClicked for Pangle adapter. " +
                                            "Listener is null.",
                                    )
                                }
                            }

                            override fun onAdDismissed() {
                                PartnerLogController.log(DID_DISMISS)
                                partnerAdListener?.onPartnerAdDismissed(partnerAd, null) ?: run {
                                    PartnerLogController.log(
                                        CUSTOM,
                                        "Unable to fire onPartnerAdDismissed for Pangle adapter. " +
                                            "Listener is null.",
                                    )
                                }
                            }
                        },
                    )
                    ad.show(activity)
                }
                is PAGRewardedAd -> {
                    ad.setAdInteractionListener(
                        object : PAGRewardedAdInteractionListener {
                            override fun onAdShowed() {
                                PartnerLogController.log(SHOW_SUCCEEDED)
                                resumeOnce(Result.success(partnerAd))
                            }

                            override fun onAdClicked() {
                                PartnerLogController.log(DID_CLICK)
                                partnerAdListener?.onPartnerAdClicked(partnerAd) ?: run {
                                    PartnerLogController.log(
                                        CUSTOM,
                                        "Unable to fire onPartnerAdClicked for Pangle adapter. " +
                                            "Listener is null.",
                                    )
                                }
                            }

                            override fun onAdDismissed() {
                                PartnerLogController.log(DID_DISMISS)
                                partnerAdListener?.onPartnerAdDismissed(partnerAd, null) ?: run {
                                    PartnerLogController.log(
                                        CUSTOM,
                                        "Unable to fire onPartnerAdDismissed for Pangle adapter. " +
                                            "Listener is null.",
                                    )
                                }
                            }

                            override fun onUserEarnedReward(rewardItem: PAGRewardItem) {
                                PartnerLogController.log(DID_REWARD)
                                partnerAdListener?.onPartnerAdRewarded(partnerAd) ?: run {
                                    PartnerLogController.log(
                                        CUSTOM,
                                        "Unable to fire onPartnerAdRewarded for Pangle adapter. " +
                                            "Listener is null.",
                                    )
                                }
                            }

                            override fun onUserEarnedRewardFail(
                                code: Int,
                                message: String?,
                            ) {
                                PartnerLogController.log(
                                    CUSTOM,
                                    "Pangle reward failed with code: $code and message: $message",
                                )
                            }
                        },
                    )
                    ad.show(activity)
                }
                else -> {
                    PartnerLogController.log(
                        SHOW_FAILED,
                        "Ad is not an instance of PAGInterstitialAd or PAGRewardedAd.",
                    )
                    resumeOnce(
                        Result.failure(
                            ChartboostMediationAdException(ChartboostMediationError.ShowError.WrongResourceType),
                        ),
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
            Result.failure(ChartboostMediationAdException(ChartboostMediationError.InvalidateError.AdNotFound))
        }
    }
}
