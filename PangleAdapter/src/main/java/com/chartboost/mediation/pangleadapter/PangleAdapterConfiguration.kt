package com.chartboost.mediation.pangleadapter

import com.bytedance.sdk.openadsdk.api.PAGConstant.PAGDoNotSellType
import com.bytedance.sdk.openadsdk.api.PAGConstant.PAGGDPRConsentType
import com.bytedance.sdk.openadsdk.api.init.PAGConfig
import com.bytedance.sdk.openadsdk.api.init.PAGSdk
import com.chartboost.chartboostmediationsdk.domain.PartnerAdapterConfiguration
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController
import com.chartboost.chartboostmediationsdk.utils.PartnerLogController.PartnerAdapterEvents.CUSTOM

object PangleAdapterConfiguration : PartnerAdapterConfiguration {
    /**
     * The partner name for internal uses.
     */
    override val partnerId = "pangle"

    /**
     * The partner name for external uses.
     */
    override val partnerDisplayName = "Pangle"

    /**
     * The partner SDK version.
     */
    override val partnerSdkVersion: String = PAGSdk.getSDKVersion()

    /**
     * The partner adapter version.
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
    override val adapterVersion = BuildConfig.CHARTBOOST_MEDIATION_PANGLE_ADAPTER_VERSION

    /**
     * Flag that can optionally be set to enable Pangle's multi-process support. It must be set
     * prior to initializing the Pangle SDK for it to take effect.
     */
    var multiProcessSupport = false
        set(value) {
            field = value
            PartnerLogController.log(
                CUSTOM,
                "Pangle's multi-process support is ${if (value) "enabled" else "disabled"}.",
            )
        }

    /**
     * Use to manually set the GDPR consent status on the Pangle SDK.
     * This is generally unnecessary as the Mediation SDK will set the consent status automatically
     * based on the latest consent info.
     *
     * @param consent An Int representing the [PAGGDPRConsentType].
     */
    fun setGdprConsentOverride(@PAGGDPRConsentType consent: Int) {
        isGdprConsentOverridden = true
        PAGConfig.setGDPRConsent(consent)
        PartnerLogController.log(CUSTOM, "Pangle GDPR consent status overridden to $consent")
    }


    /**
     * Use to manually set the do not sell flag on the Pangle SDK.
     * This is generally unnecessary as the Mediation SDK will set the consent flags automatically
     * based on the latest consent info.
     *
     * @param doNotSell An Int representing the [PAGDoNotSellType].
     */
    fun setDoNotSellOverride(@PAGDoNotSellType doNotSell: Int) {
        isDoNotSellOverridden = true
        PAGConfig.setDoNotSell(doNotSell)
        PartnerLogController.log(CUSTOM, "Pangle do not sell overridden to $doNotSell")
    }

    /**
     * Whether GDPR consent has been overridden by the publisher.
     */
    internal var isGdprConsentOverridden = false

    /**
     * Whether privacy consent has been overridden by the publisher.
     */
    internal var isDoNotSellOverridden = false
}
