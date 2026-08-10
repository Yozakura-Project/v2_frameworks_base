/*
 * SPDX-FileCopyrightText: The YozakuraOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.infinity.header;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * A FrameLayout whose children fade out along the bottom edge.
 *
 * Infinity wraps the QS header image in com.bosphere.fadingedgelayout.FadingEdgeLayout, a
 * third party library that is in neither this tree nor the Infinity sources, and uses it
 * for nothing but this one fade. This is the same effect without the dependency: the
 * children are drawn into an offscreen layer that a vertical gradient then erases with
 * DST_OUT, so the shade behind shows through the fade.
 *
 * A foreground gradient would have been less code but not the same thing - it paints a
 * colour over the image rather than making it transparent, which is visibly wrong on a
 * light shade.
 */
public class YozakuraFadingEdgeLayout extends FrameLayout {

    private final Paint mFadePaint = new Paint();

    private int mBottomFadeSize;

    public YozakuraFadingEdgeLayout(Context context) {
        this(context, null);
    }

    public YozakuraFadingEdgeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YozakuraFadingEdgeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mFadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    /** Height of the fade along the bottom edge, in px. 0 turns it off. */
    public void setBottomFadeSize(int px) {
        if (px == mBottomFadeSize) {
            return;
        }
        mBottomFadeSize = px;
        updateShader();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShader();
    }

    private void updateShader() {
        final int height = getHeight();
        if (mBottomFadeSize <= 0 || height <= 0) {
            mFadePaint.setShader(null);
            return;
        }
        mFadePaint.setShader(new LinearGradient(0, height - mBottomFadeSize, 0, height,
                Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mFadePaint.getShader() == null) {
            super.dispatchDraw(canvas);
            return;
        }
        final int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        super.dispatchDraw(canvas);
        canvas.drawRect(0, getHeight() - mBottomFadeSize, getWidth(), getHeight(), mFadePaint);
        canvas.restoreToCount(save);
    }
}
