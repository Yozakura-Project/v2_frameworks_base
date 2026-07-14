/*
 * SPDX-FileCopyrightText: The YozakuraOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.statusbar;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

import org.lineageos.internal.statusbar.LineageStatusBarItem;

import java.util.ArrayList;

/**
 * YozakuraOS status bar carrier label (v2-B).
 * Shows the carrier name (SubscriptionManager, multi-SIM aware) or a user
 * custom string in the status bar when enabled.
 *   yozakura_carrier_label       (Settings.Secure int 0/1)  enable
 *   yozakura_carrier_label_text  (Settings.Secure string)   optional custom text
 */
public class YozakuraCarrierLabel extends TextView {

    private static final String KEY_ENABLED = "yozakura_carrier_label";
    private static final String KEY_TEXT = "yozakura_carrier_label_text";

    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubscriptionManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private LineageStatusBarItem.Manager mManager;
    private int mIconTint = Color.WHITE;
    private boolean mAttached;
    private boolean mEnabled;

    private final ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    };

    private final LineageStatusBarItem.DarkReceiver mDarkReceiver =
            new LineageStatusBarItem.DarkReceiver() {
        @Override
        public void onDarkChanged(ArrayList<Rect> areas, float darkIntensity, int tint) {
            mIconTint = tint;
            setTextColor(mIconTint);
        }
        @Override
        public void setFillColors(int darkColor, int lightColor) {
        }
    };

    private final TelephonyCallback mTelephonyCallback = new CarrierCallback();

    public YozakuraCarrierLabel(Context context) {
        this(context, null);
    }

    public YozakuraCarrierLabel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YozakuraCarrierLabel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAttached) {
            return;
        }
        mAttached = true;

        mManager = LineageStatusBarItem.findManager((android.view.View) this);
        if (mManager != null) {
            mManager.addDarkReceiver(mDarkReceiver);
        }

        getContext().getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(KEY_ENABLED), false, mObserver);
        getContext().getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(KEY_TEXT), false, mObserver);

        if (mTelephonyManager != null) {
            try {
                mTelephonyManager.registerTelephonyCallback(
                        getContext().getMainExecutor(), mTelephonyCallback);
            } catch (Exception e) {
                // ignore
            }
        }

        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!mAttached) {
            return;
        }
        mAttached = false;

        if (mManager != null) {
            mManager.removeDarkReceiver(mDarkReceiver);
            mManager = null;
        }

        getContext().getContentResolver().unregisterContentObserver(mObserver);

        if (mTelephonyManager != null) {
            try {
                mTelephonyManager.unregisterTelephonyCallback(mTelephonyCallback);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void updateSettings() {
        mEnabled = Settings.Secure.getInt(
                getContext().getContentResolver(), KEY_ENABLED, 0) == 1;
        updateLabel();
    }

    private void updateLabel() {
        if (!mEnabled) {
            setVisibility(GONE);
            setText("");
            return;
        }
        final String name = resolveLabel();
        if (TextUtils.isEmpty(name)) {
            setVisibility(GONE);
        } else {
            setText(name);
            setVisibility(VISIBLE);
        }
    }

    private String resolveLabel() {
        // 1. user custom text takes priority
        final String custom = Settings.Secure.getString(
                getContext().getContentResolver(), KEY_TEXT);
        if (!TextUtils.isEmpty(custom)) {
            return custom;
        }
        // 2. carrier name from the default data subscription (multi-SIM aware)
        try {
            final int subId = SubscriptionManager.getDefaultDataSubscriptionId();
            if (mSubscriptionManager != null
                    && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                final SubscriptionInfo si =
                        mSubscriptionManager.getActiveSubscriptionInfo(subId);
                if (si != null && !TextUtils.isEmpty(si.getCarrierName())) {
                    return si.getCarrierName().toString();
                }
            }
        } catch (Exception e) {
            // fall through to legacy
        }
        // 3. legacy fallback
        return mTelephonyManager != null ? mTelephonyManager.getNetworkOperatorName() : null;
    }

    private class CarrierCallback extends TelephonyCallback
            implements TelephonyCallback.ServiceStateListener {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            updateLabel();
        }
    }
}
