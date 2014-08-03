package com.germainz.dynamicalarmicon;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class Preferences extends PreferenceActivity {
    private static final String PREF_SHOW_APP_ICON = "pref_show_app_icon";
    private static final String PREF_CLOCK_STYLE = "pref_clock_style";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
        addPreferencesFromResource(R.xml.preferences);
        Config config = Config.getInstance(this);

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
}
