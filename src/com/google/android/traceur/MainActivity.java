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

package com.google.android.traceur;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.TwoStatePreference;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

public class MainActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    static final String TAG = AtraceUtils.TAG;

    public static final String ACTION_REFRESH_TAGS = "com.google.android.traceur.REFRESH_TAGS";

    private TwoStatePreference mTracingOn;
    private TwoStatePreference mQs;

    private AlertDialog mAlertDialog;
    private SharedPreferences mPrefs;

    private final Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };

    private MultiSelectListPreference mTags;

    private boolean mRefreshing;

    private BroadcastReceiver mReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());

        addPreferencesFromResource(R.xml.main);

        mTracingOn = (TwoStatePreference) findPreference(getString(R.string.pref_key_tracing_on));
        mTracingOn.setOnPreferenceChangeListener(this);
        mQs = (TwoStatePreference) findPreference(getString(R.string.pref_key_show_qs));

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M || "N".equals(Build.VERSION.CODENAME)) {
            getPreferenceScreen().removePreference(mQs);
        } else {
            mQs.setOnPreferenceChangeListener(this);
        }

        mTags = (MultiSelectListPreference) findPreference(getString(R.string.pref_key_tags));
        mTags.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (mRefreshing) {
                    return true;
                }
                Set<String> set = (Set<String>) newValue;
                TreeMap<String, String> available = AtraceUtils.atraceListCategories();
                ArrayList<String> clean = new ArrayList<>(set.size());

                for (String s : set) {
                    if (available.containsKey(s)) {
                        clean.add(s);
                    }
                }
                set.clear();
                set.addAll(clean);
                return true;
            }
        });

        findPreference("dump").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        AtraceUtils.atraceDumpAndSendInBackground(MainActivity.this,
                                Receiver.getActiveTags(MainActivity.this, mPrefs, true));
                        return true;
                    }
                });

        findPreference("restore_default_tags").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        refreshTags(/* restoreDefaultTags =*/ true);
                        Toast.makeText(MainActivity.this, "Default tags restored",
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshTags();

            }
        };
        registerReceiver(mReceiver, new IntentFilter(ACTION_REFRESH_TAGS));

        refreshTags();

        getWindow().getDecorView().post(mRefreshRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        if (mAlertDialog != null) {
            mAlertDialog.cancel();
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        getWindow().getDecorView().post(mRefreshRunnable);
        return true;
    }

    private void refresh() {
        Receiver.updateTracing(this, false);
        Receiver.updateQs(this);
    }

    private void refreshTags() {
        refreshTags(/* restoreDefaultTags =*/ false);
    }

    private void refreshTags(boolean restoreDefaultTags) {
        mTracingOn.setChecked(mTracingOn.getPreferenceManager().getSharedPreferences().getBoolean(
                mTracingOn.getKey(), false));

        Set<Entry<String, String>> availableTags = AtraceUtils.atraceListCategories().entrySet();
        ArrayList<String> entries = new ArrayList<String>(availableTags.size());
        ArrayList<String> values = new ArrayList<String>(availableTags.size());
        for (Entry<String, String> entry : availableTags) {
            entries.add(entry.getKey() + ": " + entry.getValue());
            values.add(entry.getKey());
        }

        mRefreshing = true;
        mTags.setEntries(entries.toArray(new String[0]));
        mTags.setEntryValues(values.toArray(new String[0]));
        if (restoreDefaultTags || !mPrefs.contains(getString(R.string.pref_key_tags))) {
            mTags.setValues(Receiver.ATRACE_TAGS);
        }
        mRefreshing = false;
    }
}
