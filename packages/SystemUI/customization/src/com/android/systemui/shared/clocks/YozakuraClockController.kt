/*
 * SPDX-FileCopyrightText: The YozakuraOS Project
 * SPDX-License-Identifier: Apache-2.0
 *
 * A generic ClockController that renders one of the ported keyguard_clock_* face
 * layouts as the large clock. The face layouts live in SystemUI's res-keyguard and
 * are resolved by name at runtime (this code runs inside the SystemUI process), so
 * they do not need to be relocated into the customization library.
 */
package com.android.systemui.shared.clocks

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.android.systemui.customization.clocks.DefaultClockFaceLayout
import com.android.systemui.plugins.keyguard.data.model.AlarmData
import com.android.systemui.plugins.keyguard.data.model.WeatherData
import com.android.systemui.plugins.keyguard.data.model.ZenData
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAnimations
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockEventListeners
import com.android.systemui.plugins.keyguard.ui.clocks.ClockEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPositionAnimationArgs
import com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings
import com.android.systemui.plugins.keyguard.ui.clocks.ThemeConfig
import com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind
import java.io.PrintWriter
import java.util.Locale
import android.icu.util.TimeZone

class YozakuraClockController(
    private val ctx: Context,
    private val layoutInflater: LayoutInflater,
    private val resources: Resources,
    private val settings: ClockSettings?,
    private val clockId: String,
    private val displayName: String,
    private val largeLayoutName: String,
) : ClockController {

    private val noAnimations =
        object : ClockAnimations {
            override fun enter() {}

            override fun doze(fraction: Float) {}

            override fun fold(fraction: Float) {}

            override fun charge() {}

            override fun onPositionAnimated(anim: ClockPositionAnimationArgs) {}

            override fun onPickerCarouselSwiping(swipingFraction: Float) {}

            override fun onFidgetTap(x: Float, y: Float) {}

            override fun onFontAxesChanged(style: ClockAxisStyle) {}
        }

    /** A face controller wrapping an arbitrary (TextClock-based) face layout. */
    inner class YozakuraFaceController(override val view: View) : ClockFaceController {
        override val layout = DefaultClockFaceLayout(view)
        override val config = ClockFaceConfig()
        override var theme = ThemeConfig(true, settings?.seedColor)
        override val animations: ClockAnimations = noAnimations
        override val events =
            object : ClockFaceEvents {
                // TextClock views update themselves, so ticking is a no-op.
                override fun onTimeTick() {}

                override fun onThemeChanged(theme: ThemeConfig) {
                    this@YozakuraFaceController.theme = theme
                }

                override fun onFontSettingChanged(fontSizePx: Float) {}

                override fun onTargetRegionChanged(targetRegion: Rect?) {}

                override fun onSecondaryDisplayChanged(onSecondaryDisplay: Boolean) {}
            }
    }

    private val parent = FrameLayout(ctx)

    override val smallClock = YozakuraFaceController(inflateFace(largeLayoutName))
    override val largeClock = YozakuraFaceController(inflateFace(largeLayoutName))

    private fun inflateFace(name: String): View {
        val id = resources.getIdentifier(name, "layout", ctx.packageName)
        return layoutInflater.inflate(id, parent, false)
    }

    override val events =
        object : ClockEvents {
            override var isReactiveTouchInteractionEnabled: Boolean = false

            override fun onTimeZoneChanged(timeZone: TimeZone) {}

            override fun onTimeFormatChanged(formatKind: TimeFormatKind) {}

            override fun onLocaleChanged(locale: Locale) {}

            override fun onWeatherDataChanged(data: WeatherData) {}

            override fun onAlarmDataChanged(data: AlarmData) {}

            override fun onZenDataChanged(data: ZenData) {}
        }

    override val eventListeners = ClockEventListeners()

    override val config = ClockConfig(clockId, displayName, displayName)

    override fun initialize(isDarkTheme: Boolean, dozeFraction: Float, foldFraction: Float) {
        largeClock.events.onThemeChanged(largeClock.theme.copy(isDarkTheme = isDarkTheme))
        smallClock.events.onThemeChanged(smallClock.theme.copy(isDarkTheme = isDarkTheme))
        smallClock.events.onTimeTick()
        largeClock.events.onTimeTick()
    }

    override fun dump(pw: PrintWriter) {
        pw.println("YozakuraClockController($clockId -> $largeLayoutName)")
    }
}
