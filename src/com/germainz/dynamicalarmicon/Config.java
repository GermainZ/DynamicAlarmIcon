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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import de.robv.android.xposed.XSharedPreferences;

public class Config {
    private static Config mInstance;
    private XSharedPreferences mXPreferences = null;
    private SharedPreferences mPreferences = null;
    private static final String PACKAGE_NAME = "com.germainz.dynamicalarmicon";
    private static final String PREFS = PACKAGE_NAME + "_preferences";
    private static final String PREF_CLOCK_STYLE = "pref_clock_style";
    private static final String PREF_CLOCK_COLOR = "pref_clock_color";

    public Config() {
        mXPreferences = new XSharedPreferences(PACKAGE_NAME, PREFS);
        mXPreferences.makeWorldReadable();
    }

    private Config(Context context) {
        mPreferences = context.getSharedPreferences(PREFS, Context.MODE_WORLD_READABLE);
    }

    public static Config getInstance(Context context) {
        if (mInstance == null)
            mInstance = new Config(context);
        return mInstance;
    }

    public int getClockStyle() {
        return Integer.parseInt(getString(PREF_CLOCK_STYLE, "0"));
    }

    public int getClockColor() {
        return getInt(PREF_CLOCK_COLOR, Color.WHITE);
    }

    public void setClockColor(int color) {
        mPreferences.edit().putInt(PREF_CLOCK_COLOR, color).apply();
    }

    private String getString(String key, String defaultValue) {
        String returnResult = defaultValue;
        if (mPreferences != null)
            returnResult = mPreferences.getString(key, defaultValue);
        else if (mXPreferences != null)
            returnResult = mXPreferences.getString(key, defaultValue);
        return returnResult;
    }

    private int getInt(String key, int defaultValue) {
        int returnResult = defaultValue;
        if (mPreferences != null)
            returnResult = mPreferences.getInt(key, defaultValue);
        else if (mXPreferences != null)
            returnResult = mXPreferences.getInt(key, defaultValue);
        return returnResult;
    }
}
