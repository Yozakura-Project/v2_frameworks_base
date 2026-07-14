/*
 * SPDX-FileCopyrightText: The YozakuraOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.statusbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * YozakuraOS status bar battery bar.
 * Draws a thin horizontal bar (width proportional to battery level) at the top
 * or bottom edge of the status bar.
 *   yozakura_battery_bar          (Settings.Secure int 0/1)  enable
 *   yozakura_battery_bar_position (Settings.Secure int 0=bottom/1=top)
 */
public class YozakuraBatteryBar extends View {

    private static final String KEY_ENABLED = "yozakura_battery_bar";
    private static final String KEY_POSITION = "yozakura_battery_bar_position";
    private static final int LOW_LEVEL = 15;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private boolean mAttached;
    private boolean mEnabled;
    private int mLevel = 0;
    private boolean mCharging;

    private final ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    };

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            final int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            mLevel = scale > 0 ? (level * 100 / scale) : 0;
            final int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);
            mCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            if (mEnabled) {
                invalidate();
            }
        }
    };

    public YozakuraBatteryBar(Context context) {
        this(context, null);
    }

    public YozakuraBatteryBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YozakuraBatteryBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAttached) {
            return;
        }
        mAttached = true;
        getContext().getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(KEY_ENABLED), false, mObserver);
        getContext().getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(KEY_POSITION), false, mObserver);
        getContext().registerReceiver(mBatteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!mAttached) {
            return;
        }
        mAttached = false;
        getContext().getContentResolver().unregisterContentObserver(mObserver);
        try {
            getContext().unregisterReceiver(mBatteryReceiver);
        } catch (Exception e) {
            // ignore
        }
    }

    private void updateSettings() {
        mEnabled = Settings.Secure.getInt(
                getContext().getContentResolver(), KEY_ENABLED, 0) == 1;
        final int pos = Settings.Secure.getInt(
                getContext().getContentResolver(), KEY_POSITION, 0);
        final ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            final FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
            final int g = (pos == 1 ? Gravity.TOP : Gravity.BOTTOM);
            if (flp.gravity != g) {
                flp.gravity = g;
                setLayoutParams(flp);
            }
        }
        setVisibility(mEnabled ? VISIBLE : GONE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mEnabled || mLevel <= 0) {
            return;
        }
        final int color;
        if (mCharging) {
            color = 0xFF4CAF50;
        } else if (mLevel <= LOW_LEVEL) {
            color = 0xFFF44336;
        } else {
            color = Color.WHITE;
        }
        mPaint.setColor(color);
        final int w = getWidth() * mLevel / 100;
        canvas.drawRect(0f, 0f, (float) w, (float) getHeight(), mPaint);
    }
}
