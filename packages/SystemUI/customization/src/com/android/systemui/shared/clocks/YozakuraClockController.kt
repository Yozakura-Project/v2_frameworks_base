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
import android.icu.util.TimeZone
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
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
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import com.android.systemui.plugins.keyguard.ui.clocks.ThemeConfig
import com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind
import java.io.PrintWriter
import java.util.Locale

class YozakuraClockController(
    private val ctx: Context,
    private val layoutInflater: LayoutInflater,
    private val resources: Resources,
    private val settings: ClockSettings?,
    private val clockId: String,
    private val displayName: String,
    private val largeLayoutName: String,
    private val isCenter: Boolean,
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
    inner class YozakuraFaceController(override val view: View, isLarge: Boolean) :
        ClockFaceController {
        init {
            // The keyguard identifies the active clock face by these ids; without
            // them DefaultClockFaceLayout won't place/show the face.
            view.id =
                if (isLarge) ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE
                else ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL
        }

        override val layout = DefaultClockFaceLayout(view)
        override val config = ClockFaceConfig()
        override var theme = ThemeConfig(true, settings?.seedColor)
        override val animations: ClockAnimations = noAnimations
        override val events =
            object : ClockFaceEvents {
                override fun onTimeTick() {
                    (view as? AnimatableClockView)?.refreshTime()
                    refreshTextClocks(view)
                }

                override fun onThemeChanged(theme: ThemeConfig) {
                    this@YozakuraFaceController.theme = theme
                }

                override fun onFontSettingChanged(fontSizePx: Float) {}

                override fun onTargetRegionChanged(targetRegion: Rect?) {}

                override fun onSecondaryDisplayChanged(onSecondaryDisplay: Boolean) {}
            }
    }

    private val parent = FrameLayout(ctx)

    override val smallClock = YozakuraFaceController(buildSmallClock(), false)
    override val largeClock = YozakuraFaceController(buildLargeFace(), true)

    /**
     * The small-clock slot (shown when the double-line clock setting is off, or when
     * notifications/media occupy the lockscreen) renders the SAME selected face, shrunk
     * and anchored to the top-left so it sits compactly in the small-clock frame instead
     * of overlapping the large clock (the doubled/garbled result seen on cp46).
     */
    private fun buildSmallClock(): View {
        return try {
            val face = inflateFace(largeLayoutName)
            // inflateFace returns a bare View where the face layout is absent (e.g. the
            // wallpaper picker process); keep that empty fallback as-is.
            if (face.javaClass == View::class.java) return face
            val container = ScaledFaceContainer(ctx, SMALL_FACE_SCALE)
            container.addView(
                face,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            face.pivotX = 0f
            face.pivotY = 0f
            face.scaleX = SMALL_FACE_SCALE
            face.scaleY = SMALL_FACE_SCALE
            container
        } catch (t: Throwable) {
            View(ctx)
        }
    }

    /**
     * Force every TextClock in a face to recompute its text now. A freshly inflated
     * TextClock can otherwise stay blank until its own ticker fires, which races the
     * initial layout and leaves several faces showing only the date. Re-applying the
     * format strings triggers TextClock.onTimeChanged() immediately.
     */
    private fun refreshTextClocks(v: View) {
        when (v) {
            // refreshTime() recomputes the text AND invalidates the view, so a TextClock
            // that was laid out but not yet painted actually shows up.
            is TextClock -> v.refreshTime()
            is ViewGroup -> for (i in 0 until v.childCount) refreshTextClocks(v.getChildAt(i))
        }
    }

    private fun buildLargeFace(): View {
        val face = inflateFace(largeLayoutName)
        applyFaceStyle(face)
        return face
    }

    /**
     * Match the original Yozakura overlay picker so ClockRegistry faces render in the same
     * place: center the center-clocks (others start-aligned), and disable clipping up the
     * view hierarchy once attached so tall faces aren't cropped (a lot of faces looked
     * "broken" only because they were being clipped/left-aligned here).
     */
    private fun applyFaceStyle(face: View) {
        (face as? LinearLayout)?.gravity = if (isCenter) Gravity.CENTER else Gravity.START
        face.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    disableClippingUpTree(v)
                    // A face's TextClocks can be laid out but not painted on the first frame
                    // after a switch (uiautomator sees the time, the screen stays blank until
                    // the next minute tick). Nudge them just after attach so it paints now.
                    refreshTextClocks(v)
                    v.post { refreshTextClocks(v) }
                    v.postDelayed({ refreshTextClocks(v) }, 300)
                    v.postDelayed({ refreshTextClocks(v) }, 800)
                }

                override fun onViewDetachedFromWindow(v: View) {}
            }
        )
    }

    private fun disableClippingUpTree(view: View) {
        var current: View? = view
        var depth = 0
        while (current is ViewGroup && depth < 12) {
            current.clipChildren = false
            current.clipToPadding = false
            current = current.parent as? View
            depth++
        }
    }

    private fun inflateFace(name: String): View {
        val id = resources.getIdentifier(name, "layout", ctx.packageName)
        // The face layouts live in SystemUI's res-keyguard. In other hosts (e.g. the
        // Wallpaper picker process) they are not present, so fall back to an empty view
        // instead of crashing on inflate(0).
        if (id == 0) return View(ctx)
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

    /**
     * Wraps the face for the small-clock slot: measures the child at its full natural size
     * (ignoring any compact height the small-clock frame requests) but reports the SCALED
     * size, so the keyguard allocates a compact box and the bottom of the face (e.g. the
     * date line) isn't clipped. The child itself is drawn small via scaleX/scaleY.
     */
    private class ScaledFaceContainer(context: Context, private val scale: Float) :
        FrameLayout(context) {
        init {
            clipChildren = false
            clipToPadding = false
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val child = getChildAt(0)
            if (child == null) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            measureChild(
                child,
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            setMeasuredDimension(
                (child.measuredWidth * scale).toInt(),
                (child.measuredHeight * scale).toInt(),
            )
        }
    }

    private companion object {
        // Shrink factor for the face when shown in the small-clock slot (top-left).
        private const val SMALL_FACE_SCALE = 0.45f
    }
}
