/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.traceur;

import com.google.android.collect.Sets;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.Set;
import java.util.TreeMap;

public class Receiver extends BroadcastReceiver {

    public static final String DUMP_ACTION = "com.android.traceur.DUMP";
    public static final String OPEN_ACTION = "com.android.traceur.OPEN";
    public static final String FORCE_UPDATE_ACTION = "com.android.traceur.FORCE_UPDATE";

    private static final Set<String> ATRACE_TAGS = Sets.newArraySet(
            "am", "binder_driver", "camera", "dalvik", "freq", "gfx", "hal",
            "idle", "input", "irq", "res", "sched", "sync", "view", "wm",
            "workq");

    /* The user list doesn't include workq, irq, or sync, because the user builds don't have
     * permissions for them. */
    private static final Set<String> ATRACE_TAGS_USER = Sets.newArraySet(
            "am", "binder_driver", "camera", "dalvik", "freq", "gfx", "hal",
            "idle", "input", "res", "sched", "view", "wm");

    public static final int BUFFER_SIZE_KB = 16384;

    private static final String TAG = "Traceur";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            updateTracing(context, false);
            QsService.requestListeningState(context);
        } else if (FORCE_UPDATE_ACTION.equals(intent.getAction())) {
            updateTracing(context, true);
        } else if (DUMP_ACTION.equals(intent.getAction())) {
            context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            if (AtraceUtils.isTracingOn()) {
                AtraceUtils.atraceDumpAndSend(context);
            } else {
                context.startActivity(new Intent(context, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } else if (OPEN_ACTION.equals(intent.getAction())) {
            context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            context.startActivity(new Intent(context, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    public static void updateTracing(Context context, boolean force) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(context.getString(R.string.pref_key_tracing_on), false) != AtraceUtils.isTracingOn() || force) {
            if (prefs.getBoolean(context.getString(R.string.pref_key_tracing_on), false)) {
                String activeAvailableTags = getActiveTags(context, prefs, true);
                if (!TextUtils.equals(activeAvailableTags, getActiveTags(context, prefs, false))) {
                    postRootNotification(context, prefs);
                } else {
                    cancelRootNotification(context);
                }

                AtraceUtils.atraceStart(activeAvailableTags, BUFFER_SIZE_KB);
            } else {
                AtraceUtils.atraceStop();
                cancelRootNotification(context);
            }
        }

    }

    private static void postRootNotification(Context context, SharedPreferences prefs) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        Intent sendIntent = new Intent(context, MainActivity.class);

        String title = context.getString(R.string.tracing_categories_unavailable);
        String msg = getActiveUnavailableTags(context, prefs);
        final Notification.Builder builder = new Notification.Builder(context)
                .setStyle(new Notification.BigTextStyle().bigText(
                        msg))
                .setSmallIcon(R.drawable.stat_sys_adb)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(msg)
                .setContentIntent(PendingIntent.getActivity(
                        context, 0, sendIntent, PendingIntent.FLAG_ONE_SHOT
                                | PendingIntent.FLAG_CANCEL_CURRENT))
                .setAutoCancel(true)
                .setLocalOnly(true)
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .setColor(context.getColor(
                        com.android.internal.R.color.system_notification_accent_color));
        nm.notify(Receiver.class.getName(), 0, builder.build());
    }

    private static void cancelRootNotification(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.cancel(Receiver.class.getName(), 0);
    }

    public static String getActiveTags(Context context, SharedPreferences prefs, boolean onlyAvailable) {
        Set<String> tags = prefs.getStringSet(context.getString(R.string.pref_key_tags),
                getDefaultTagList());
        StringBuilder sb = new StringBuilder(10 * tags.size());
        TreeMap<String, String> available =
                onlyAvailable ? AtraceUtils.atraceListCategories() : null;

        for (String s : tags) {
            if (onlyAvailable && !available.containsKey(s)) continue;
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(s);
        }
        String s = sb.toString();
        Log.v(TAG, "getActiveTags(onlyAvailable=" + onlyAvailable + ") = \"" + s + "\"");
        return s;
    }

    public static String getActiveUnavailableTags(Context context, SharedPreferences prefs) {
        Set<String> tags = prefs.getStringSet(context.getString(R.string.pref_key_tags),
                getDefaultTagList());
        StringBuilder sb = new StringBuilder(10 * tags.size());
        TreeMap<String, String> available = AtraceUtils.atraceListCategories();

        for (String s : tags) {
            if (available.containsKey(s)) continue;
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(s);
        }
        String s = sb.toString();
        Log.v(TAG, "getActiveUnavailableTags() = \"" + s + "\"");
        return s;
    }

    public static Set<String> getDefaultTagList() {
        return Build.TYPE.equals("user") ? ATRACE_TAGS_USER : ATRACE_TAGS;
    }
}
