/*
 * Copyright (C) 2017-2026 The LineageOS Project
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

package com.android.internal.util.infinity;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * YozakuraOS: a deliberately partial port of the Infinity Utils.
 *
 * Upstream is ~525 lines and pulls in framework resources (an ic_sleep drawable and the
 * sleep_mode_* strings) plus SystemNotificationChannels and IStatusBarService, so taking
 * it whole would mean touching core/res and paying a full framework rebuild. Every
 * consumer ported so far — the weather tile and the QS header providers — calls exactly
 * one method, isPackageInstalled, so only that is brought over, verbatim.
 *
 * Consumers therefore stay unmodified. When a later port needs another method from
 * upstream Utils, copy that one across too rather than rewriting these.
 */
public class Utils {

    public static boolean isPackageInstalled(Context context, String packageName,
            boolean ignoreState) {
        if (packageName != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, 0);
                if (!pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
        return true;
    }

    public static boolean isPackageInstalled(Context context, String packageName) {
        return isPackageInstalled(context, packageName, true);
    }
}
