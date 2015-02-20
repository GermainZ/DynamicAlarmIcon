package com.germainz.dynamicalarmicon;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;

public class XposedMod implements IXposedHookLoadPackage {
    private Context mContext;
    private ClockDrawable mClockDrawable;
    private ClockDrawable mClockDrawableStatusbar;
    private ContentObserver mNextAlarmObserver;
    private BroadcastReceiver mNextAlarmChangedReceiver;
    private AlarmManager mAlarmManager;
    private static final Set<String> CLOCK_PACKAGES = new HashSet<String>(Arrays.asList(new String[]{
            "com.android.deskclock", "com.google.android.deskclock", "com.mobitobi.android.gentlealarmtrial",
            "com.mobitobi.android.gentlealarm"
    }));
    private static final Pattern TIME_PATTERN = Pattern.compile("([01]?[0-9]|2[0-3]):([0-5][0-9])");
    public static final boolean IS_LOLLIPOP_OR_ABOVE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    private static final int SBNIDX = IS_LOLLIPOP_OR_ABOVE ? 0 : 1;
    private static final int SBNIIDX = IS_LOLLIPOP_OR_ABOVE ? 1 : 2;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.android.systemui"))
            hookSystemUI(lpparam.classLoader);
        else if (lpparam.packageName.equals("ch.bitspin.timely"))
            hookTimely(lpparam.classLoader);
    }

    private void hookSystemUI(final ClassLoader classLoader) {
        findAndHookConstructor("com.android.systemui.statusbar.NotificationData.Entry", classLoader,
                StatusBarNotification.class, "com.android.systemui.statusbar.StatusBarIconView",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object notification = param.args[SBNIDX];
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

                        /* dynamically find the alarm time since it's place is different from KK to L */
                        Integer alarmHour = null;
                        Integer alarmMinute = null;
                        for (CharSequence txt : notificationText) {
                            if (TextUtils.isEmpty(txt)) continue;
                            Matcher matcher = TIME_PATTERN.matcher(txt);
                            if (matcher.find()) {
                                String[] alarmTime = TextUtils.split(matcher.group(), ":");
                                alarmHour = Integer.parseInt(alarmTime[0]);
                                alarmMinute = Integer.parseInt(alarmTime[1]);
                            }
                        }

                        if (alarmHour == null)
                            return;

                        // Set the small icon.
                        ImageView icon = (ImageView) param.args[SBNIIDX];
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
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        mContext = (Context) getObjectField(param.thisObject, "mContext");
                        /* Beginning with Android Lollipop NEXT_ALARM_FORMATTED has been depreciated
                         * instead we need to register a broadcast receiver to receive an intent
                         * with action ACTION_NEXT_ALARM_CLOCK_CHANGED
                         */
                        if (IS_LOLLIPOP_OR_ABOVE) {
                            IntentFilter filter = new IntentFilter();
                            filter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
                            mNextAlarmChangedReceiver = new BroadcastReceiver() {
                                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                                @Override
                                public void onReceive(Context context, Intent intent) {
                                    if (mAlarmManager == null) {
                                        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                                    }
                                    AlarmManager.AlarmClockInfo mNextAlarm = mAlarmManager.getNextAlarmClock();
                                    if (mNextAlarm == null) return;
                                    Calendar calendar = GregorianCalendar.getInstance();
                                    calendar.setTime(new Date(mNextAlarm.getTriggerTime()));
                                    updateAlarmIcon(calendar.get(Calendar.HOUR), calendar.get(Calendar.MINUTE), param.thisObject);
                                }
                            };
                            mContext.registerReceiver(mNextAlarmChangedReceiver, filter);
                        } else {
                            Uri nextAlarmUri = Settings.System.getUriFor(Settings.System.NEXT_ALARM_FORMATTED);
                            mNextAlarmObserver = new ContentObserver(new Handler()) {
                                @Override
                                public void onChange(boolean selfChange) {
                                    String nextAlarm = Settings.System.getString(mContext.getContentResolver(),
                                            Settings.System.NEXT_ALARM_FORMATTED);
                                    if (nextAlarm.isEmpty()) {
                                    /* Some vendors (e.g. HTC) seem to remove the alarm_clock status bar icon
                                     * instead of toggling its visibility, so we'll need to look for it again in
                                     * updateAlarmIcon next time an alarm is set.
                                     */
                                        mClockDrawable = null;
                                    } else {
                                        Matcher matcher = TIME_PATTERN.matcher(nextAlarm);
                                        if (matcher.find()) {
                                            String[] nextAlarmTime = TextUtils.split(matcher.group(), ":");
                                            int nextAlarmHour = Integer.parseInt(nextAlarmTime[0]);
                                            int nextAlarmMinute = Integer.parseInt(nextAlarmTime[1]);
                                            updateAlarmIcon(nextAlarmHour, nextAlarmMinute, param.thisObject);
                                        }
                                    }
                                }
                            };
                            mContext.getContentResolver().registerContentObserver(nextAlarmUri, false, mNextAlarmObserver);
                        }
                    }
                }
        );

        findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBar", classLoader, "destroy",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        if (IS_LOLLIPOP_OR_ABOVE) {
                            mContext.unregisterReceiver(mNextAlarmChangedReceiver);
                        } else {
                            mContext.getContentResolver().unregisterContentObserver(mNextAlarmObserver);
                        }
                    }
                }
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            findAndHookMethod("com.android.systemui.statusbar.StatusBarIconView", classLoader, "updateDrawable",
                    boolean.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (getObjectField(param.thisObject, "mSlot").equals("alarm_clock")) {
                                param.setResult(true);
                            }
                        }
                    }
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            /* Set the alarm clock drawable in the expanded status bar */
            findAndHookMethod("com.android.systemui.statusbar.phone.StatusBarHeaderView", classLoader, "onNextAlarmChanged",
                    AlarmManager.AlarmClockInfo.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args[0] != null) {
                                if (mClockDrawableStatusbar == null) {
                                    mClockDrawableStatusbar = new ClockDrawable(0, 0);
                                }

                                TextView mAlarmStatus = (TextView) getObjectField(param.thisObject, "mAlarmStatus");
                                mClockDrawableStatusbar.setColorFilter(mAlarmStatus.getCurrentTextColor(), PorterDuff.Mode.MULTIPLY);
                                mClockDrawableStatusbar.setBounds(0, 0, Math.round(18 * Resources.getSystem().getDisplayMetrics().density), Math.round(18 * Resources.getSystem().getDisplayMetrics().density));
                                mAlarmStatus.setCompoundDrawables(mClockDrawableStatusbar, null, null, null);
                            }
                        }
                    }
            );
        }
    }

    private void hookTimely(final ClassLoader classLoader) {
        findAndHookMethod("ch.bitspin.timely.alarm.AlarmManager", classLoader, "f", "ch.bitspin.timely.data.AlarmClock",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // Timely actually does this for Android 4.1 and less, but not for 4.2 and higher.
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

    private void updateAlarmIcon(int hour, int minute, Object thiz) {
        if (mClockDrawable == null) {
            LinearLayout statusIcons = (LinearLayout) getObjectField(thiz, "mStatusIcons");
            for (int i = 0; i < statusIcons.getChildCount(); i++) {
                ImageView alarm_clock = (ImageView) statusIcons.getChildAt(i);
                if (getObjectField(alarm_clock, "mSlot").equals("alarm_clock")) {
                    mClockDrawable = new ClockDrawable(hour, minute);
                    alarm_clock.setImageDrawable(mClockDrawable);

                    if (IS_LOLLIPOP_OR_ABOVE) {
                        alarm_clock.getLayoutParams().width = getIntField(thiz, "mIconSize") + 2 * getIntField(thiz, "mIconHPadding");
                        alarm_clock.getLayoutParams().height = Math.round(20 * Resources.getSystem().getDisplayMetrics().density);
                    }
                }
            }
        } else {
            mClockDrawable.setTime(hour, minute);
        }

        if (IS_LOLLIPOP_OR_ABOVE) {
            if (mClockDrawableStatusbar == null) {
                mClockDrawableStatusbar = new ClockDrawable(hour, minute);
            } else {
                mClockDrawableStatusbar.setTime(hour, minute);
            }
        }
    }
}
