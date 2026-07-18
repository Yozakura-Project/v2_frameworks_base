/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.panels.data.repository

import android.content.Context
import android.content.res.Resources
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.common.ui.data.repository.ConfigurationRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.util.kotlin.emitOnStart
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

@SysUISingleton
class QuickQuickSettingsRowRepository
@Inject
constructor(
    @Application private val context: Context,
    @ShadeDisplayAware private val resources: Resources,
    @ShadeDisplayAware configurationRepository: ConfigurationRepository,
) {
    // YozakuraOS: re-emit when the user changes yozakura_qqs_rows so the
    // collapsed Quick Quick Settings updates live without a SystemUI restart.
    private val qqsRowsSettingChange: Flow<Unit> = callbackFlow {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("yozakura_qqs_rows"),
            false,
            observer,
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val rows =
        merge(configurationRepository.onConfigurationChange.map {}, qqsRowsSettingChange)
            .emitOnStart()
            .map {
                val userRows =
                    Settings.Secure.getIntForUser(
                        context.contentResolver,
                        "yozakura_qqs_rows",
                        0,
                        UserHandle.USER_CURRENT,
                    )
                if (userRows > 0) userRows
                else resources.getInteger(R.integer.quick_qs_paginated_grid_num_rows)
            }
}
