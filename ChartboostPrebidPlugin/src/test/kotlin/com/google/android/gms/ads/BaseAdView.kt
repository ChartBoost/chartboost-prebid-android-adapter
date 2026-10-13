/*
 * Copyright (c) 2026 Chartboost, Inc.
 *
 * Licensed under the MIT License.
 */

package com.google.android.gms.ads

import android.content.Context
import android.widget.FrameLayout

// Test-only stand-ins for AdMob's ad views. The adapter recognizes an AdMob host by class name, so tests need
// a view with this exact name without putting Google Mobile Ads on the test classpath.
open class BaseAdView(context: Context) : FrameLayout(context)

/** AdMob's own banner view extends [BaseAdView]; a host seen through a subclass must still match. */
class AdView(context: Context) : BaseAdView(context)
