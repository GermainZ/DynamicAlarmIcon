package com.germainz.dynamicalarmicon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
    private static final String UPDATE_DESKCLOCK_ICON = "com.germainz.dynamicalarmicon.UPDATE_DESKCLOCK_ICON";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.android.systemui"))
            hookSystemUI(lpparam.classLoader);
        else if (lpparam.packageName.equals("ch.bitspin.timely"))
            hookTimely(lpparam.classLoader);
        else if (lpparam.packageName.equals("com.android.deskclock"))
            hookDeskClock(lpparam.classLoader);
    }

    private void hookSystemUI(final ClassLoader classLoader) {
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
                            public void onReceive(final Context context, final Intent intent) {
                                /* Why the short delay? For two reasons:
                                *  1- Some clock apps send the ALARM_CHANGED broadcast before setting
                                *     NEXT_ALARM_FORMATTED.
                                *  2- The broadcast is sometimes received and handled *before* the notification is
                                *     actually added to the status bar (for UPDATE_DESKCLOCK_ICON).
                                */
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (intent.getAction().equals(UPDATE_ALARM_ICON)) {
                                            updateAlarmIcon(intent, param.thisObject);
                                        } else if (intent.getAction().equals(UPDATE_DESKCLOCK_ICON)) {
                                            updateDeskClockIcon(context, param.thisObject);
                                        }
                                    }
                                }, 5);
                            }
                        };

                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(UPDATE_ALARM_ICON);
                        intentFilter.addAction(UPDATE_DESKCLOCK_ICON);
                        Context context = (Context) getObjectField(param.thisObject, "mContext");
                        context.registerReceiver(broadcastReceiver, intentFilter);
                    }
                }
        );

        findAndHookMethod("com.android.systemui.statusbar.StatusBarIconView", classLoader, "updateDrawable",
                boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (getObjectField(param.thisObject, "mSlot").equals("alarm_clock"))
                            param.setResult(true);
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

    private void hookDeskClock(final ClassLoader classLoader) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Context context = (Context) param.args[0];
                Intent intent = new Intent(UPDATE_DESKCLOCK_ICON);
                context.sendBroadcast(intent);
            }
        };

        findAndHookMethod("com.android.deskclock.alarms.AlarmNotifications", classLoader,
                "showLowPriorityNotification", Context.class, "com.android.deskclock.provider.AlarmInstance", hook);
        findAndHookMethod("com.android.deskclock.alarms.AlarmNotifications", classLoader,
                "showHighPriorityNotification", Context.class, "com.android.deskclock.provider.AlarmInstance", hook);
        findAndHookMethod("com.android.deskclock.alarms.AlarmNotifications", classLoader,
                "showMissedNotification", Context.class, "com.android.deskclock.provider.AlarmInstance", hook);
        findAndHookMethod("com.android.deskclock.alarms.AlarmNotifications", classLoader,
                "showSnoozeNotification", Context.class, "com.android.deskclock.provider.AlarmInstance", hook);
        findAndHookMethod("com.android.deskclock.alarms.AlarmNotifications", classLoader,
                "showAlarmNotification", Context.class, "com.android.deskclock.provider.AlarmInstance", hook);
    }

    private void updateAlarmIcon(Intent intent, Object thiz) {
        int hour = intent.getIntExtra("hour", 0);
        int minute = intent.getIntExtra("minute", 0);
        if (mClockDrawable == null) {
            LinearLayout statusIcons = (LinearLayout) getObjectField(thiz, "mStatusIcons");
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

    private void updateDeskClockIcon(Context context, Object thiz) {
        LinearLayout notificationIcons = (LinearLayout) getObjectField(thiz, "mNotificationIcons");
        Object notificationData = getObjectField(thiz, "mNotificationData");
        for (int i = 0; i < notificationIcons.getChildCount(); i++) {
            View view = notificationIcons.getChildAt(i);
            if (((String) getObjectField(view, "mSlot")).startsWith("com.android.deskclock/")) {
                int N = (Integer) callMethod(notificationData, "size");
                Object entry = callMethod(notificationData, "get", N - i - 1);
                View content = (View) getObjectField(entry, "content");

                // Get the time from the notification's text.
                int textId = context.getResources().getIdentifier("text", "id", "android");
                String alarmText = (String) ((TextView) content.findViewById(textId)).getText();
                String[] alarmTime = alarmText.split(" ");
                int index = 1;
                Context deskClockContext = null;
                try {
                    deskClockContext = context.createPackageContext("com.android.deskclock",
                            Context.CONTEXT_IGNORE_SECURITY);
                } catch (PackageManager.NameNotFoundException ignored) {
                    // Impossible — com.android.deskclock definitely exists.
                }
                int snoozeUntilId = deskClockContext.getResources().getIdentifier("alarm_alert_snooze_until", "string",
                        "com.android.deskclock");
                String snoozeUntilString = deskClockContext.getString(snoozeUntilId);
                String[] snoozeUntilStringArray = TextUtils.split(snoozeUntilString, "%s");
                snoozeUntilString = TextUtils.join("\\E.*\\Q", snoozeUntilStringArray);
                snoozeUntilString = "\\Q" + snoozeUntilString + "\\E";
                if (alarmText.matches(snoozeUntilString))
                    index = snoozeUntilString.split(" ").length;
                alarmTime = alarmTime[index].split(":");
                final int alarmHour = Integer.parseInt(alarmTime[0]) % 12;
                final int alarmMinute = Integer.parseInt(alarmTime[1]);

                // Set the small icon.
                ClockDrawable clockDrawable = new ClockDrawable(alarmHour, alarmMinute);
                callMethod(view, "setImageDrawable", new ClockDrawable(alarmHour, alarmMinute));

                // Set the large icon (shown in the notification shade) for the normal and expanded views.
                int iconId = context.getResources().getIdentifier("icon", "id", "android");
                ImageView icon = (ImageView) content.findViewById(iconId);
                icon.setImageDrawable(clockDrawable);
                View big = (View) getObjectField(entry, "expandedBig");
                icon = (ImageView) big.findViewById(iconId);
                icon.setImageDrawable(clockDrawable);
            }
        }
    }

}
