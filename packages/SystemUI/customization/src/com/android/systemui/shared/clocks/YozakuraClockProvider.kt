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
import android.view.LayoutInflater
import com.android.systemui.customization.R as customR
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockMessageBuffers
import com.android.systemui.plugins.keyguard.ui.clocks.ClockMetadata
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPickerConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockProvider
import com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings

/** One selectable Yozakura clock face. */
private data class YozakuraFace(val id: String, val name: String, val layout: String)

class YozakuraClockProvider(
    val layoutInflater: LayoutInflater,
    val resources: Resources,
) : ClockProvider {
    private var messageBuffers: ClockMessageBuffers? = null

    // Foundation set. Expand to the full 82 faces once verified on-device.
    private val faces =
        listOf(
            YozakuraFace("YOZAKURA_TALLER", "Yozakura Taller", "keyguard_clock_taller"),
            YozakuraFace("YOZAKURA_OOS", "Yozakura OnePlus", "keyguard_clock_oos"),
            YozakuraFace("YOZAKURA_IOS", "Yozakura iOS", "keyguard_clock_ios"),
            YozakuraFace("YOZAKURA_CENTER", "Yozakura Center", "keyguard_clock_center"),
            YozakuraFace("YOZAKURA_STYLISH", "Yozakura Stylish", "keyguard_clock_stylish"),
        )

    private fun faceFor(clockId: String?): YozakuraFace? = faces.firstOrNull { it.id == clockId }

    override fun initialize(buffers: ClockMessageBuffers?) {
        messageBuffers = buffers
    }

    override fun getClocks(): List<ClockMetadata> = faces.map { ClockMetadata(it.id) }

    override fun createClock(ctx: Context, settings: ClockSettings): ClockController? {
        val face = faceFor(settings.clockId) ?: return null
        return YozakuraClockController(
            ctx,
            layoutInflater,
            resources,
            settings,
            face.id,
            face.name,
            face.layout,
        )
    }

    override fun getClockPickerConfig(settings: ClockSettings): ClockPickerConfig {
        val face = faceFor(settings.clockId) ?: faces[0]
        return ClockPickerConfig(
            face.id,
            face.name,
            face.name,
            resources.getDrawable(customR.drawable.clock_default_thumbnail, null),
            isReactiveToTone = true,
        )
    }
}
