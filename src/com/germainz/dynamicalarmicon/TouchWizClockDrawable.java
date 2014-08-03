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
import android.graphics.Paint;
import android.graphics.RectF;

class TouchWizClockDrawable extends ClockDrawable {
    public TouchWizClockDrawable(int color, int hours, int minutes) {
        super(color, hours, minutes);
    }

    @Override
    public void draw(Canvas canvas) {
        float width = getBounds().width();
        float height = getBounds().height() * .9f;
        float x = width / 2;
        float y = height / 2 + height / 10;
        float radius = height / 2.7f;

        /* Clock's circle outline and middle dot */
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(radius / 3.4f);
        canvas.drawCircle(x, y, radius, mPaint);
        canvas.drawPoint(x, y, mPaint);

        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);


        /* Draw hands */
        mPaint.setStrokeWidth(radius / 3.8f);
        float stopX = (float) (x + (radius * .5) * Math.cos(Math.toRadians(mHourAngle - 90)));
        float stopY = (float) (y + (radius * .5) * Math.sin(Math.toRadians(mHourAngle - 90)));
        canvas.drawLine(x, y, stopX, stopY, mPaint);

        stopX = (float) (x + (radius * .7) * Math.cos(Math.toRadians(mMinuteAngle - 90)));
        stopY = (float) (y + (radius * .7) * Math.sin(Math.toRadians(mMinuteAngle - 90)));
        canvas.drawLine(x, y, stopX, stopY, mPaint);

        /* Draw bells */
        mPaint.setStrokeWidth(radius / 3.0f);
        final float afterRadius = radius * 1.5f;
        RectF rectF = new RectF(x - afterRadius, y - afterRadius, x + afterRadius, y + afterRadius);
        canvas.drawArc(rectF, 212, 30, false, mPaint);
        canvas.drawArc(rectF, 298, 30, false, mPaint);

        /* Draw legs */
        float diffX = (float) (radius * Math.cos(Math.toRadians(52)));
        float diffY = (float) (radius * Math.sin(Math.toRadians(52)));
        canvas.drawLine(x + diffX, y + diffY, x + diffX * 1.45f, y + diffY * 1.45f, mPaint);
        diffX = (float) (radius * Math.cos(Math.toRadians(180 - 52)));
        diffY = (float) (radius * Math.sin(Math.toRadians(180 - 52)));
        canvas.drawLine(x + diffX, y + diffY, x + diffX * 1.45f, y + diffY * 1.45f, mPaint);
    }
}
