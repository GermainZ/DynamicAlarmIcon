/*
 * Copyright (C) 2014 GermainZ@xda-developers.com
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

package com.germainz.dynamicalarmicon;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

class ClockDrawable extends Drawable {

    final Paint mPaint;
    int mHourAngle;
    int mMinuteAngle;

    public ClockDrawable(int color, int hours, int minutes) {
        mPaint = new Paint();
        mPaint.setColor(color);
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaint.setAntiAlias(true);
        mHourAngle = (int) ((hours + minutes / 60f) * 360 / 12);
        mMinuteAngle = minutes * 360 / 60;
    }

    @Override
    public void draw(Canvas canvas) {
        float width = getBounds().width();
        float height = getBounds().height() * .9f;
        float x = width / 2;
        float y = height / 2 + height / 10;
        float radius = height / 3;

		/* Clock's circle outline and middle dot */
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(radius / 7f);
        canvas.drawCircle(x, y, radius, mPaint);
        canvas.drawCircle(x, y, radius / 20f, mPaint);

        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        /* Draw hands */
        float stopX = (float) (x + (radius * .5) * Math.cos(Math.toRadians(mHourAngle - 90)));
        float stopY = (float) (y + (radius * .5) * Math.sin(Math.toRadians(mHourAngle - 90)));
        canvas.drawLine(x, y, stopX, stopY, mPaint);

        stopX = (float) (x + (radius * .8) * Math.cos(Math.toRadians(mMinuteAngle - 90)));
        stopY = (float) (y + (radius * .8) * Math.sin(Math.toRadians(mMinuteAngle - 90)));
        canvas.drawLine(x, y, stopX, stopY, mPaint);

		/* Draw bells */
        final float afterRadius = radius * 1.3f;
        RectF rectF = new RectF(x - afterRadius, y - afterRadius, x + afterRadius, y + afterRadius);
        canvas.drawArc(rectF, 200, 40, false, mPaint);
        canvas.drawArc(rectF, 300, 40, false, mPaint);
    }

    public void setTime(int hours, int minutes) {
        mHourAngle = (int) ((hours + minutes / 60f) * 360 / 12);
        mMinuteAngle = minutes * 360 / 60;
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
