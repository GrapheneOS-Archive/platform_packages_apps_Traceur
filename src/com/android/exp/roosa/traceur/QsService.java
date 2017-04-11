/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.exp.roosa.traceur;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.Window;
import android.view.WindowManager;

public class QsService extends TileService implements
        DialogInterface.OnClickListener {

    private static QsService sListeningInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        setTheme(android.R.style.Theme_DeviceDefault_Light);
    }

    @Override
    public void onTileAdded() {
        update();
    }

    @Override
    public void onStartListening() {
        sListeningInstance = this;
        update();
    }

    @Override
    public void onStopListening() {
        if (sListeningInstance == this) {
            sListeningInstance = null;
        }
    }

    private void update() {
        boolean tracingOn = AtraceUtils.isTracingOn();
        int resId = tracingOn
                ? R.drawable.stat_sys_adb
                : R.drawable.stat_sys_adb_disabled;
        getQsTile().setIcon(Icon.createWithResource(this, resId));
        getQsTile().setState(tracingOn ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        getQsTile().updateTile();
        setStatusIcon(tracingOn ? Icon.createWithResource(this, R.drawable.stat_sys_adb) : null,
                "Traceur");
    }

    @Override
    public void onClick() {
        boolean tracingOn = AtraceUtils.isTracingOn();
        AlertDialog dialog;
        if (tracingOn) {
            dialog = new AlertDialog.Builder(this)
                    .setTitle("Traceur")
                    .setMessage("Warning: Tracing can significantly impact device performance.")
                    .setPositiveButton("Dump", this)
                    .setNeutralButton("Stop", this)
                    .setNegativeButton("Open App", this)
                    .create();
        } else {
            dialog = new AlertDialog.Builder(this)
                    .setTitle("Traceur")
                    .setMessage("Warning: Tracing can significantly impact device performance.")
                    .setPositiveButton("Start", this)
                    .setNegativeButton("Open App", this)
                    .create();
        }
        dialog.getWindow().setTitle("Traceur");
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        showDialog(dialog);
    }


    @Override
    public void onClick(DialogInterface dialog, int which) {
        boolean tracingOn = AtraceUtils.isTracingOn();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean force = false;

        if (tracingOn) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                AtraceUtils.atraceDumpAndSendInBackground(this,
                        Receiver.getActiveTags(prefs, true));
            } else if (which == DialogInterface.BUTTON_NEUTRAL) {
                prefs.edit().putBoolean("tracing_on", false).apply();
                force = true;
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                startActivity(new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } else {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                prefs.edit().putBoolean("tracing_on", true).apply();
                force = true;
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                startActivity(new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
        if (force) {
            sendBroadcast(new Intent(MainActivity.ACTION_REFRESH_TAGS)
                    .setPackage(getPackageName()));
        }
        Receiver.updateTracing(this, force);
        Receiver.updateQs(this);
        requestListeningState(this);
    }

    public static void requestListeningState(Context context) {
        if (sListeningInstance != null) {
            sListeningInstance.update();
        } else {
            requestListeningState(context, new ComponentName(context, QsService.class));
        }
    }
}
