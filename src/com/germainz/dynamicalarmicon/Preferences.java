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

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.OpacityBar;
import com.larswerkman.holocolorpicker.SaturationBar;
import com.larswerkman.holocolorpicker.ValueBar;

public class Preferences extends PreferenceActivity {
    private static final String PREF_SHOW_APP_ICON = "pref_show_app_icon";
    private static final String PREF_CLOCK_STYLE = "pref_clock_style";
    public static final String PREF_CLOCK_COLOR = "pref_clock_color";
    private boolean mTextChanged = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
        addPreferencesFromResource(R.xml.preferences);
        final Config config = Config.getInstance(this);

        final ImageListPreference stylePref = (ImageListPreference) findPreference(PREF_CLOCK_STYLE);
        int style = config.getClockStyle();
        stylePref.setSummary(stylePref.getEntries()[stylePref.findIndexOfValue(Integer.toString(style))]);
        stylePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                stylePref.setSummary(stylePref.getEntries()[stylePref.findIndexOfValue((String) newValue)]);
                return true;
            }
        });

        final Preference clockPref = findPreference(PREF_CLOCK_COLOR);
        clockPref.setSummary(colorIntToRGB(config.getClockColor()));
        clockPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                LayoutInflater inflater = Preferences.this.getLayoutInflater();
                View colorPickerView = inflater.inflate(R.layout.color_picker,
                        (ViewGroup) findViewById(R.id.color_picker_root));

                final ColorPicker colorPicker = (ColorPicker) colorPickerView.findViewById(R.id.picker);
                SaturationBar saturationBar = (SaturationBar) colorPickerView.findViewById(R.id.saturationbar);
                final ValueBar valueBar = (ValueBar) colorPickerView.findViewById(R.id.valuebar);
                final OpacityBar opacityBar = (OpacityBar) colorPickerView.findViewById(R.id.opacitybar);
                final TextView valueTextView = (TextView) colorPickerView.findViewById(R.id.value);

                colorPicker.addSaturationBar(saturationBar);
                colorPicker.addValueBar(valueBar);
                colorPicker.addOpacityBar(opacityBar);

                int savedColor = config.getClockColor();
                colorPicker.setColor(savedColor);
                valueTextView.setText(colorIntToRGB(config.getClockColor()));

                colorPicker.setOnColorChangedListener(new ColorPicker.OnColorChangedListener() {
                    @Override
                    public void onColorChanged(int color) {
                        mTextChanged = true;
                        valueTextView.setText(colorIntToRGB(color));
                    }
                });

                valueTextView.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence text, int start, int before, int count) {
                        if (mTextChanged)
                            mTextChanged = false;
                        else if (text.length() == 8) {
                            String value = "#" + text;
                            try {
                                int color = Color.parseColor(value);
                                colorPicker.setColor(color);
                            } catch (IllegalArgumentException e) {
                                Toast.makeText(Preferences.this, getString(R.string.invalid_color),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable text) {
                    }
                });

                new AlertDialog.Builder(Preferences.this)
                        .setView(colorPickerView)
                        .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.cancel();
                            }
                        })
                        .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                int color = colorPicker.getColor();
                                config.setClockColor(color);
                                clockPref.setSummary(colorIntToRGB(color));
                                dialogInterface.dismiss();
                            }
                        })
                        .show();
                return true;
            }
        });

        Preference prefShowAppIcon = findPreference(PREF_SHOW_APP_ICON);
        prefShowAppIcon.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                PackageManager packageManager = getPackageManager();
                int state = (Boolean) newValue ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                final ComponentName alias = new ComponentName(Preferences.this,
                        "com.germainz.dynamicalarmicon.Preferences-Alias");
                packageManager.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP);
                return true;
            }
        });
    }

    public String colorIntToRGB(int color) {
        return String.format("%08X", color);
    }
}
