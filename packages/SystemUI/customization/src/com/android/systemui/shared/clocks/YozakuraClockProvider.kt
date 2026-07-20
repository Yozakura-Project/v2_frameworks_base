/*
 * SPDX-FileCopyrightText: The YozakuraOS Project
 * SPDX-License-Identifier: Apache-2.0
 *
 * Registers the ported keyguard_clock_* faces as real ClockRegistry clocks so they
 * appear in the standard "Wallpaper & style -> Clock" picker and are managed natively
 * by the clock system (which hides the default clock / smartspace for us).
 */
package com.android.systemui.shared.clocks

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockMessageBuffers
import com.android.systemui.plugins.keyguard.ui.clocks.ClockMetadata
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPickerConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockProvider
import com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings

/** One selectable Yozakura clock face. [short] labels the picker thumbnail. */
private data class YozakuraFace(
    val id: String,
    val name: String,
    val layout: String,
    val short: String,
    val center: Boolean,
)

class YozakuraClockProvider(
    val layoutInflater: LayoutInflater,
    val resources: Resources,
) : ClockProvider {
    private var messageBuffers: ClockMessageBuffers? = null

    // Every ported keyguard_clock_* face (res-keyguard). "none" is excluded (empty face).
    // Base names map 1:1 to keyguard_clock_<name>.xml; ids are stable across upgrades so a
    // saved selection keeps working. A face whose layout fails to inflate falls back to the
    // default clock (see createClock) rather than crashing.
    // Only faces that render correctly in the AOSP large-clock slot. The excluded ones
    // (ios/taller/cos*/miui/etc.) rely on the old overlay's own container to paint their
    // oversized time and stay blank here; they can be re-added if that path is ported.
    private val faceNames =
        listOf(
            "oos", "center", "stylish", "a9", "big1", "big2", "big3", "big4", "block",
            "bubble", "deliriumdual", "gateway", "gobold", "gobold2", "ide", "ios7", "ios8",
            "ios9", "ios18", "life", "miui2", "moto", "nos1", "nos2", "num", "oos2",
            "skewrom", "stylish2", "stylish3", "stylish4", "stylish5", "stylish6",
            "stylish7", "stylish8", "stylish9", "stylish10", "tall", "taller2", "taller3",
            "word",
        )

    // Faces the original overlay picker centered (others are start-aligned).
    private val centerFaces =
        setOf(
            "center", "simple", "ide", "moto", "stylish", "stylish2", "stylish3",
            "stylish4", "stylish5", "stylish6", "stylish7", "stylish8", "stylish9",
            "stylish10", "word", "life", "a9", "nos1", "nos2", "num", "accent",
            "analog", "block", "bubble", "ios",
        )

    private val faces: List<YozakuraFace> =
        faceNames.map { n ->
            YozakuraFace(
                id = "YOZAKURA_" + n.uppercase(),
                name = "Yozakura " + n.replaceFirstChar { it.uppercase() },
                layout = "keyguard_clock_$n",
                short = n,
                center = n in centerFaces,
            )
        }

    private fun faceFor(clockId: String?): YozakuraFace? = faces.firstOrNull { it.id == clockId }

    override fun initialize(buffers: ClockMessageBuffers?) {
        messageBuffers = buffers
    }

    override fun getClocks(): List<ClockMetadata> = faces.map { ClockMetadata(it.id) }

    override fun createClock(ctx: Context, settings: ClockSettings): ClockController? {
        val face = faceFor(settings.clockId) ?: return null
        // A broken/unavailable face layout must not crash the keyguard: returning null makes
        // ClockRegistry fall back to the default clock for that selection.
        return try {
            YozakuraClockController(
                ctx,
                layoutInflater,
                resources,
                settings,
                face.id,
                face.name,
                face.layout,
                face.center,
            )
        } catch (t: Throwable) {
            null
        }
    }

    override fun getClockPickerConfig(settings: ClockSettings): ClockPickerConfig {
        val face = faceFor(settings.clockId) ?: faces[0]
        return ClockPickerConfig(
            face.id,
            face.name,
            face.name,
            // Distinct per-face thumbnail so the picker carousel isn't all identical. The face
            // layouts aren't available in the picker (wallpaper) process, so label by name.
            LabelThumbnail(face.short),
            isReactiveToTone = true,
        )
    }

    /** A simple thumbnail that renders the face's short name, so faces are distinguishable. */
    private class LabelThumbnail(private val label: String) : Drawable() {
        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            paint.textSize = b.height() * 0.16f
            val w = paint.measureText(label)
            val maxW = b.width() * 0.86f
            if (w > maxW && w > 0f) {
                paint.textSize = paint.textSize * (maxW / w)
            }
            val cx = b.exactCenterX()
            val cy = b.exactCenterY() - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(label, cx, cy, paint)
        }

        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
