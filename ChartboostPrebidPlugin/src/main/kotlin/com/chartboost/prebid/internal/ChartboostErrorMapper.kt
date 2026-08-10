/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.chartboost.prebid.internal

import com.chartboost.sdk.events.CacheError
import com.chartboost.sdk.events.ExpirationEvent
import com.chartboost.sdk.events.ShowError
import org.prebid.mobile.api.exceptions.AdException

/**
 * Maps a Chartboost [CacheError] to a Prebid [AdException], surfacing the raw Chartboost code in the
 * message. `AdException(type, message)` is the only constructor, so the code rides in the
 * message string. No blanket internal error; an unknown/future code still carries its raw value and is
 * never swallowed. Show errors are not mapped: Prebid has no terminal show-failed signal, so they are
 * logged at the call site instead.
 */
internal object ChartboostErrorMapper {

    fun map(error: CacheError): AdException {
        val code = error.code
        val prefix = when (code) {
            CacheError.Code.NO_AD_FOUND -> "No fill"
            CacheError.Code.SESSION_NOT_STARTED -> "Chartboost SDK not started"
            CacheError.Code.INVALID_ADM -> "Invalid ad markup"
            else -> "Chartboost cache error"
        }
        return adException("$prefix [CacheError ${code.name}(${code.errorCode})]")
    }

    fun mapShow(error: ShowError): String {
        val code = error.code
        val prefix = when (code) {
            ShowError.Code.SESSION_NOT_STARTED -> "Chartboost SDK not started"
            ShowError.Code.NO_CACHED_AD -> "No cached ad to show"
            ShowError.Code.INTERNET_UNAVAILABLE -> "No internet connection"
            ShowError.Code.AD_EXPIRED -> "Ad expired"
            else -> "Chartboost show error"
        }
        return "$prefix [ShowError ${code.name}(${code.errorCode})]"
    }

    fun admInvalid(): AdException = adException("Empty or invalid ADM for a Chartboost-flagged bid")

    /**
     * No Banner.BannerSize fits inside the negotiated slot, so there is nothing the SDK could render there.
     * Declining is deliberate: rendering a size larger than the slot would still count a billable
     * impression.
     */
    fun unsupportedBannerSize(width: Int, height: Int): AdException =
        adException(
            "No Chartboost banner size fits the negotiated ${width}x$height slot " +
                "(available: ${BannerSizeMapper.supportedSizes()})",
        )

    /** A cached ad expired or was evicted before it could be shown; the publisher should reload. */
    fun adExpired(event: ExpirationEvent): AdException =
        adException("Chartboost ad expired before display [reason ${event.reason}]")

    /**
     * Constructing a Chartboost ad throws synchronously (e.g. when the Monetization SDK was never
     * started). Map any such failure to an AdException so it never escapes the plugin entry points.
     */
    fun adCreationFailed(t: Throwable): AdException =
        adException("Failed to create Chartboost ad: ${t.message ?: t.javaClass.simpleName}")

    private fun adException(message: String): AdException {
        // WARN, not DEBUG: a load failure is invisible to a publisher on the default log level otherwise,
        // and these only fire on an actual failure, so they do not add steady-state noise.
        PluginLog.w(message)
        return AdException(AdException.THIRD_PARTY, message)
    }
}
