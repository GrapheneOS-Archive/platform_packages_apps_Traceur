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

package com.google.android.traceur;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class QsService extends TileService {

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
        getQsTile().setIcon(Icon.createWithResource(this, R.drawable.stat_sys_adb));
        getQsTile().setState(tracingOn ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        getQsTile().setLabel(tracingOn ? "Tracing" : "Start Tracing");
        getQsTile().updateTile();
    }

    /** When we click the tile, toggle tracing state.
     *  If tracing is being turned off, dump and offer to share. */
    @Override
    public void onClick() {
        boolean tracingOn = AtraceUtils.isTracingOn();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        prefs.edit().putBoolean("tracing_on", !tracingOn).apply();

        if (tracingOn) {
            Toast.makeText(getApplicationContext(), "Stopping trace...", Toast.LENGTH_SHORT).show();
            AtraceUtils.atraceDumpAndSendInBackground(this,
                    Receiver.getActiveTags(prefs, true));
        } else {
            Toast.makeText(getApplicationContext(), "Starting trace...", Toast.LENGTH_SHORT).show();
        }

        Receiver.updateTracing(this, true);
        Receiver.updateQs(this);
        requestListeningState(this);
        update();
    }

    public static void requestListeningState(Context context) {
        if (sListeningInstance != null) {
            sListeningInstance.update();
        } else {
            requestListeningState(context, new ComponentName(context, QsService.class));
        }
    }
}
