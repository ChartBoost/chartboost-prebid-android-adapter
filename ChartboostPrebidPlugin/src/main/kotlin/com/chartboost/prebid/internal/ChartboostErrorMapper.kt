/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.sdk.events.ChartboostError
import org.prebid.mobile.api.exceptions.AdException

/**
 * Maps Chartboost load, show and expiry errors, plus the adapter's own failures, to the Prebid
 * [AdException] the listeners take, or to a log line for a show error that is only logged. The raw
 * Chartboost code stays in the message, since `AdException(type, message)` is the only constructor. An
 * unknown or future error still carries its raw code rather than collapsing into a blanket internal error.
 */
internal object ChartboostErrorMapper {

    /** A failed load. A null [error] is a load that reported neither an ad nor an error. */
    fun map(error: ChartboostError?): AdException {
        val prefix = when (error) {
            is ChartboostError.Load.NoAd -> "No fill"
            is ChartboostError.Load.NotInitialized -> "Chartboost SDK not started"
            is ChartboostError.Load.InvalidAdm -> "Invalid ad markup"
            else -> "Chartboost load error"
        }
        return adException("$prefix [${error.describe()}]")
    }

    fun mapShow(error: ChartboostError): String {
        val prefix = when (error) {
            is ChartboostError.Show.NotInitialized -> "Chartboost SDK not started"
            is ChartboostError.Show.NoAd -> "No loaded ad to show"
            is ChartboostError.Show.NoContext -> "No usable Activity"
            is ChartboostError.Show.AdExpired -> "Ad expired"
            is ChartboostError.Show.FullscreenAlreadyShowing -> "A fullscreen ad is already showing"
            else -> "Chartboost show error"
        }
        return "$prefix [${error.describe()}]"
    }

    /** A failed show used up the loaded ad; the publisher should reload. */
    fun showFailed(error: ChartboostError): AdException = adException(mapShow(error))

    fun admInvalid(): AdException = adException("Empty or invalid ADM for a Chartboost-flagged bid")

    /** No Chartboost banner size fits inside the negotiated slot. */
    fun unsupportedBannerSize(width: Int, height: Int): AdException =
        adException(
            "No Chartboost banner size fits the negotiated ${width}x$height slot " +
                "(available: ${BannerSizeMapper.supportedSizes()})",
        )

    /** A loaded ad expired before it could be shown; the publisher should reload. */
    fun adExpired(): AdException = adException("Chartboost ad expired before display")

    /**
     * Starting a Chartboost load threw synchronously. Mapping it keeps the failure from escaping the plugin
     * entry points.
     */
    fun adCreationFailed(t: Throwable): AdException =
        adException("Failed to create Chartboost ad: ${t.message ?: t.javaClass.simpleName}")

    private fun ChartboostError?.describe(): String = if (this == null) "no error reported" else "$constant($code)"

    private fun adException(message: String): AdException {
        // WARN, not DEBUG: a load failure would otherwise be invisible at the default level, and these
        // only fire on an actual failure.
        PluginLog.w(message)
        return AdException(AdException.THIRD_PARTY, message)
    }
}
