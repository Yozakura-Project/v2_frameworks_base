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
import com.android.systemui.common.ui.data.repository.ConfigurationRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.util.kotlin.emitOnStart
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge

@SysUISingleton
class QSColumnsRepository
@Inject
constructor(
    @Application private val context: Context,
    @ShadeDisplayAware private val resources: Resources,
    @ShadeDisplayAware configurationRepository: ConfigurationRepository,
) {
    val splitShadeColumns: Flow<Int> =
        configurationRepository.onConfigurationChange.emitOnStart().mapLatest {
            resources.getInteger(R.integer.quick_settings_split_shade_num_columns)
        }
    val dualShadeColumns: Flow<Int> =
        configurationRepository.onConfigurationChange.emitOnStart().mapLatest {
            resources.getInteger(R.integer.quick_settings_dual_shade_num_columns)
        }
    // YozakuraOS: re-emit when the user changes yozakura_qs_columns so the
    // Compose QS grid updates live without needing a SystemUI restart.
    private val qsColumnsSettingChange: Flow<Unit> = callbackFlow {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("yozakura_qs_columns"),
            false,
            observer,
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val columns: Flow<Int> =
        merge(configurationRepository.onConfigurationChange.map {}, qsColumnsSettingChange)
            .emitOnStart()
            .mapLatest {
                val userColumns =
                    Settings.Secure.getIntForUser(
                        context.contentResolver,
                        "yozakura_qs_columns",
                        0,
                        UserHandle.USER_CURRENT,
                    )
                if (userColumns > 0) userColumns
                else resources.getInteger(R.integer.quick_settings_infinite_grid_num_columns)
            }
    val defaultColumns: Int =
        resources.getInteger(R.integer.quick_settings_infinite_grid_num_columns)
}
