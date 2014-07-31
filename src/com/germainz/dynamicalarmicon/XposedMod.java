package com.germainz.dynamicalarmicon;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedMod implements IXposedHookLoadPackage {
    private ClockDrawable mClockDrawable;
    private static final String UPDATE_ALARM_ICON = "com.germainz.dynamicalarmicon.UPDATE_ALARM_ICON";
    private static final Set<String> CLOCK_PACKAGES = new HashSet<String>(Arrays.asList(new String[]{
            "com.android.deskclock", "com.mobitobi.android.gentlealarmtrial", "com.mobitobi.android.gentlealarm"
    }));
    private static final Pattern TIME_PATTERN = Pattern.compile("([01]?[0-9]|2[0-3]):([0-5][0-9])");

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.android.systemui"))
            hookSystemUI(lpparam.classLoader);
        else if (lpparam.packageName.equals("ch.bitspin.timely"))
            hookTimely(lpparam.classLoader);
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

        findAndHookConstructor("com.android.systemui.statusbar.NotificationData.Entry", classLoader,
                IBinder.class, StatusBarNotification.class, "com.android.systemui.statusbar.StatusBarIconView",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object notification = param.args[1];
                        String packageName = (String) getObjectField(notification, "pkg");
                        if (!CLOCK_PACKAGES.contains(packageName))
                            return;

                        Notification notif = (Notification) getObjectField(notification, "notification");
                        RemoteViews contentView = notif.contentView;

                        List<CharSequence> notificationText = new ArrayList<CharSequence>();
                        ArrayList<Parcelable> actions = (ArrayList<Parcelable>) getObjectField(contentView, "mActions");
                        for (Parcelable parcelable : actions) {
                            Parcel parcel = Parcel.obtain();
                            parcelable.writeToParcel(parcel, 0);
                            parcel.setDataPosition(0);

                            /* RemoteViews.setTextViewText(…) adds a ReflectionAction action:
                             *   ReflectionAction(int viewId, String methodName, CharSequence value)
                             * ReflectionAction writes, in order, the following values to the parcelable:
                             *   int TAG: 2 for ReflectionAction.
                             *   int viewId: the view's ID, we don't need that.
                             *   String methodName: "setText".
                             *   int type: CHAR_SEQUENCE = 10, but we don't need to check it since it's always 10
                             *             with setText.
                             *   CharSequence value: the text we want, written using TextUtils.writeToParcel(…)
                             */

                            // Check if it's a ReflectionAction.
                            if (parcel.readInt() != 2)
                                continue;

                            parcel.readInt(); // discard the viewId.
                            // Check if methodName = "setText"
                            if (parcel.readString().equals("setText")) {
                                parcel.readInt(); // discard type.
                                // Get value.
                                notificationText.add(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel));
                            }
                            parcel.recycle();
                        }

                        Integer alarmHour = null;
                        Integer alarmMinute = null;
                        // The time should be in the notification's text, not title.
                        Matcher matcher = TIME_PATTERN.matcher(notificationText.get(1));
                        if (matcher.find()) {
                            String[] alarmTime = TextUtils.split(matcher.group(), ":");
                            alarmHour = Integer.parseInt(alarmTime[0]);
                            alarmMinute = Integer.parseInt(alarmTime[1]);
                        }
                        // No time found.
                        if (alarmHour == null)
                            return;

                        // Set the small icon.
                        ImageView icon = (ImageView) param.args[2];
                        icon.setImageDrawable(new ClockDrawable(alarmHour, alarmMinute));

                        // Set the large icon (shown in the notification shade) for the normal views.
                        // The expanded view's large icon is set, if needed, in setBigContentView's hook.
                        int width = (int) icon.getResources().getDimension(
                                android.R.dimen.notification_large_icon_width);
                        int height = (int) icon.getResources().getDimension(
                                android.R.dimen.notification_large_icon_height);
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        ClockDrawable clockDrawable = new ClockDrawable(alarmHour, alarmMinute);
                        clockDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                        clockDrawable.draw(canvas);
                        contentView.setImageViewBitmap(android.R.id.icon, bitmap);

                        setAdditionalInstanceField(param.thisObject, "hour", alarmHour);
                        setAdditionalInstanceField(param.thisObject, "minute", alarmMinute);
                    }
                }
        );

        findAndHookMethod("com.android.systemui.statusbar.NotificationData.Entry", classLoader,
                "setBigContentView", View.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        View bigContentView = (View) param.args[0];
                        if (bigContentView != null) {
                            ImageView icon = (ImageView) bigContentView.findViewById(android.R.id.icon);
                            Integer hour = (Integer) getAdditionalInstanceField(param.thisObject, "hour");
                            if (hour != null) {
                                Integer minute = (Integer) getAdditionalInstanceField(param.thisObject, "minute");
                                icon.setImageDrawable(new ClockDrawable(hour, minute));
                            }
                        }
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
                                /* Why the short delay? Because some clock apps send the ALARM_CHANGED broadcast before
                                 * setting NEXT_ALARM_FORMATTED.
                                 */
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (intent.getAction().equals(UPDATE_ALARM_ICON))
                                            updateAlarmIcon(intent, param.thisObject);
                                    }
                                }, 5);
                            }
                        };

                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(UPDATE_ALARM_ICON);
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
}
