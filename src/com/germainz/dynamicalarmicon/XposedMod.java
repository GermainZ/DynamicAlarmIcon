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

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Pair;
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
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedMod implements IXposedHookLoadPackage {
    private Context mContext;
    private ClockDrawable mClockDrawable;
    private ContentObserver mNextAlarmObserver;
    private Config mConfig;
    private static final Set<String> CLOCK_PACKAGES = new HashSet<String>(Arrays.asList(new String[]{
            "com.android.deskclock", "com.google.android.deskclock", "com.mobitobi.android.gentlealarmtrial",
            "com.mobitobi.android.gentlealarm"
    }));
    private static final Pattern TIME_PATTERN = Pattern.compile("([01]?[0-9]|2[0-3]):([0-5][0-9])");
    private static final String START_UP_INTENT = "com.germainz.dynamicalarmicon.START_UP";
    private static final int CLOCK_STYLE_AOSP = 0;
    private static final int CLOCK_STYLE_TOUCHWIZ = 1;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.android.systemui")) {
            mConfig = new Config();
            hookSystemUI(lpparam.classLoader);
        } else if (lpparam.packageName.equals("ch.bitspin.timely")) {
            hookTimely(lpparam.classLoader);
        }
    }

    private void hookSystemUI(final ClassLoader classLoader) {
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

                        Integer alarmHour;
                        Integer alarmMinute;
                        // The time should be in the notification's text, not title.
                        Matcher matcher = TIME_PATTERN.matcher(notificationText.get(1));
                        if (matcher.find()) {
                            String[] alarmTime = TextUtils.split(matcher.group(), ":");
                            alarmHour = Integer.parseInt(alarmTime[0]);
                            alarmMinute = Integer.parseInt(alarmTime[1]);
                        } else {
                            return;
                        }

                        // Set the small icon.
                        ImageView icon = (ImageView) param.args[2];
                        icon.setImageDrawable(getClockDrawable(alarmHour, alarmMinute));

                        // Set the large icon (shown in the notification shade) for the normal views.
                        // The expanded view's large icon is set, if needed, in setBigContentView's hook.
                        int width = (int) icon.getResources().getDimension(
                                android.R.dimen.notification_large_icon_width);
                        int height = (int) icon.getResources().getDimension(
                                android.R.dimen.notification_large_icon_height);
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        ClockDrawable clockDrawable = getClockDrawable(alarmHour, alarmMinute);
                        clockDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                        clockDrawable.draw(canvas);
                        contentView.setImageViewBitmap(android.R.id.icon, bitmap);

                        setAdditionalInstanceField(param.thisObject, "hour", alarmHour);
                        setAdditionalInstanceField(param.thisObject, "minute", alarmMinute);
                    }
                }
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View bigContentView = (View) param.args[0];
                    if (bigContentView != null) {
                        ImageView icon = (ImageView) bigContentView.findViewById(android.R.id.icon);
                        Integer hour = (Integer) getAdditionalInstanceField(param.thisObject, "hour");
                        if (hour != null) {
                            Integer minute = (Integer) getAdditionalInstanceField(param.thisObject, "minute");
                            icon.setImageDrawable(getClockDrawable(hour, minute));
                        }
                    }
                }
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                findAndHookMethod("com.android.systemui.statusbar.NotificationData.Entry", classLoader,
                        "setBigContentView", View.class, hook);
            else
                findAndHookMethod("com.android.systemui.statusbar.NotificationData.Entry", classLoader,
                        "setLargeView", View.class, hook);
        }

        findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBar", classLoader, "makeStatusBarView",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        Uri nextAlarmUri = Settings.System.getUriFor(Settings.System.NEXT_ALARM_FORMATTED);
                        mNextAlarmObserver = new ContentObserver(new Handler()) {
                            @Override
                            public void onChange(boolean selfChange) {
                                updateAlarmIcon(param.thisObject);
                            }
                        };

                        // Only needed on start up.
                        BroadcastReceiver startUpReceiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                updateAlarmIcon(param.thisObject);
                                mContext.unregisterReceiver(this);
                            }
                        };

                        mContext = (Context) getObjectField(param.thisObject, "mContext");
                        mContext.getContentResolver().registerContentObserver(nextAlarmUri, false, mNextAlarmObserver);
                        IntentFilter intentFilter = new IntentFilter(START_UP_INTENT);
                        mContext.registerReceiver(startUpReceiver, intentFilter);
                    }
                }
        );

        findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBar", classLoader, "destroy",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        mContext.getContentResolver().unregisterContentObserver(mNextAlarmObserver);
                    }
                }
        );

        findAndHookConstructor("com.android.systemui.statusbar.phone.PhoneStatusBarPolicy", classLoader, Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // For when the device first starts up.
                        ((Context) param.args[0]).sendBroadcast(new Intent(START_UP_INTENT));
                    }
                }
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
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

    private Pair<Integer, Integer> getTimeFromString(String s) {
        Matcher matcher = TIME_PATTERN.matcher(s);
        if (matcher.find()) {
            XposedBridge.log("Match found: '" + matcher.group() + "'");
            String[] nextAlarmTime = TextUtils.split(matcher.group(), ":");
            int nextAlarmHour = Integer.parseInt(nextAlarmTime[0]);
            int nextAlarmMinute = Integer.parseInt(nextAlarmTime[1]);
            return new Pair<Integer, Integer>(nextAlarmHour, nextAlarmMinute);
        }
        return null;
    }

    private void updateAlarmIcon(Object thiz) {
        String nextAlarm = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.NEXT_ALARM_FORMATTED);
        XposedBridge.log("nextAlarm: " + nextAlarm);
        if (nextAlarm.isEmpty()) {
            /* Some vendors (e.g. HTC) seem to remove the alarm_clock status bar icon
             * instead of toggling its visibility, so we'll need to look for it again in
             * updateAlarmIcon next time an alarm is set.
             */
            mClockDrawable = null;
        } else {
            Pair<Integer, Integer> nextAlarmTime = getTimeFromString(nextAlarm);
            if (nextAlarmTime == null)
                return;
            if (mClockDrawable == null) {
                LinearLayout statusIcons = (LinearLayout) getObjectField(thiz, "mStatusIcons");
                for (int i = 0; i < statusIcons.getChildCount(); i++) {
                    View view = statusIcons.getChildAt(i);
                    if (getObjectField(view, "mSlot").equals("alarm_clock")) {
                        mClockDrawable = getClockDrawable(nextAlarmTime.first, nextAlarmTime.second);
                        callMethod(view, "setImageDrawable", mClockDrawable);
                    }
                }
            } else {
                mClockDrawable.setTime(nextAlarmTime.first, nextAlarmTime.second);
            }
        }
    }

    private ClockDrawable getClockDrawable(int hour, int minute) {
        int style = mConfig.getClockStyle();
        int color = mConfig.getClockColor();
        if (style == CLOCK_STYLE_AOSP)
            return new ClockDrawable(color, hour, minute);
        else
            return new TouchWizClockDrawable(color, hour, minute);
    }
}
