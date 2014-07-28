package com.germainz.dynamicalarmicon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.LinearLayout;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedMod implements IXposedHookLoadPackage {
    private ClockDrawable mClockDrawable;
    private static final String UPDATE_ALARM_ICON = "com.germainz.dynamicalarmicon.UPDATE_ALARM_ICON";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        if (lpparam.packageName.equals("com.android.systemui"))
            hookSystemUI(lpparam.classLoader);
        else if (lpparam.packageName.equals("ch.bitspin.timely"))
            hookTimely(lpparam.classLoader);


    }

    private void hookSystemUI(ClassLoader classLoader) {
        findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarPolicy", classLoader, "updateAlarm",
                Intent.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        boolean alarmSet = ((Intent) param.args[0]).getBooleanExtra("alarmSet", false);
                        Object mService = getObjectField(param.thisObject, "mService");
                        callMethod(mService, "setIconVisibility", "alarm_clock", alarmSet);
                        if (!alarmSet)
                            return null;
                        Context context = (Context) getObjectField(param.thisObject, "mContext");
                        String nextAlarm = Settings.System.getString(context.getContentResolver(),
                                Settings.System.NEXT_ALARM_FORMATTED);
                        if (nextAlarm.isEmpty())
                            return null;
                        String[] nextAlarmTime = nextAlarm.split(" ")[1].split(":");
                        int nextAlarmHour = Integer.parseInt(nextAlarmTime[0]) % 12;
                        int nextAlarmMinute = Integer.parseInt(nextAlarmTime[1]);
                        Intent intent = new Intent(UPDATE_ALARM_ICON);
                        intent.putExtra("hour", nextAlarmHour);
                        intent.putExtra("minute", nextAlarmMinute);
                        context.sendBroadcast(intent);
                        return null;
                    }
                }
        );

        findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBar", classLoader, "makeStatusBarView",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                int hour = intent.getIntExtra("hour", 0);
                                int minute = intent.getIntExtra("minute", 0);
                                if (mClockDrawable == null) {
                                    LinearLayout statusIcons = (LinearLayout) getObjectField(param.thisObject,
                                            "mStatusIcons");
                                    for (int i = 0; i < statusIcons.getChildCount(); i++) {
                                        View view = statusIcons.getChildAt(i);
                                        if (getObjectField(view, "mSlot").equals("alarm_clock")) {
                                            mClockDrawable = new ClockDrawable(hour, minute);
                                            callMethod(view, "setImageDrawable", mClockDrawable);
                                        }
                                    }
                                } else {
                                    mClockDrawable.setTime(hour, minute);
                                }
                            }
                        };
                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(UPDATE_ALARM_ICON);
                        Context context = (Context) getObjectField(param.thisObject, "mContext");
                        context.registerReceiver(broadcastReceiver, intentFilter);
                    }
                }
        );
    }

    private void hookTimely(final ClassLoader classLoader) {
        findAndHookMethod("ch.bitspin.timely.alarm.AlarmManager", classLoader, "f", "ch.bitspin.timely.data.AlarmClock",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // Timely actually does this for Android 4.1 and less (but not for 4.2+) but only after the
                        // ALARM_CHANGED intent is sent, which is too late.
                        String nextAlarmFormatted = "";
                        Context context = (Context) getObjectField(param.thisObject, "e");
                        Object alarmClock = param.args[0];
                        if (alarmClock != null) {
                            Object nextAlarmUTC = callStaticMethod(findClass("ch.bitspin.timely.alarm.e", classLoader),
                                    "a", alarmClock);
                            if (nextAlarmUTC != null) {
                                Object nextAlarmLocal = callMethod(nextAlarmUTC, "c", (Object) null);
                                String timeFormat = "E kk:mm";
                                if (!DateFormat.is24HourFormat(context))
                                    timeFormat = "E h:mm aa";
                                nextAlarmFormatted = (String) DateFormat.format(timeFormat,
                                        (Long) callMethod(nextAlarmLocal, "d"));
                            }
                        }
                        Settings.System.putString(context.getContentResolver(),
                                Settings.System.NEXT_ALARM_FORMATTED, nextAlarmFormatted);
                    }
                }
        );
    }
}
