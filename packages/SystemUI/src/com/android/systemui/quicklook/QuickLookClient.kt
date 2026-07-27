/*
 * SPDX-FileCopyrightText: The YozakuraOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.quicklook

import android.app.PendingIntent
import com.android.axion.quicklook.SportsData
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

/**
 * YozakuraOS DynamicBar Stage A2 compatibility shim.
 *
 * Infinity's QuickLookClient binds to the Axion "QuickLook" system service
 * (com.android.axion.quicklook.SERVICE) over AIDL to surface now-playing / live
 * sports activities. That platform service does not exist on LineageOS, so this
 * is a no-op client with the same public surface SmartspaceIslandManager depends
 * on. It never binds a service and never fires callbacks, so the sports / now-
 * playing island sources are simply inert. The rest of DynamicBar is unaffected.
 * Keeping this shim lets the ported axdynamicbar files stay byte-for-byte from
 * Infinity. Wire up a real provider here later if an equivalent lands.
 */
@SysUISingleton
class QuickLookClient @Inject constructor() {

    interface Callback {
        fun onNowPlayingUpdate(nowPlayingText: String, tapAction: PendingIntent?) {}
        fun onSportsUpdate(sports: List<SportsData>) {}
    }

    fun addCallback(callback: Callback) {}

    fun removeCallback(callback: Callback) {}
}
