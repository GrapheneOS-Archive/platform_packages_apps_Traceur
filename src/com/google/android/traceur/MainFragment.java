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

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v14.preference.MultiSelectListPreference;
import android.support.v7.preference.Preference;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.support.v14.preference.SwitchPreference;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

public class MainFragment extends PreferenceFragment {

    static final String TAG = AtraceUtils.TAG;

    public static final String ACTION_REFRESH_TAGS = "com.android.traceur.REFRESH_TAGS";

    private SwitchPreference mTracingOn;

    private AlertDialog mAlertDialog;
    private SharedPreferences mPrefs;

    private MultiSelectListPreference mTags;

    private boolean mRefreshing;

    private BroadcastReceiver mRefreshReceiver;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(
                getActivity().getApplicationContext());

        mTracingOn = (SwitchPreference) findPreference(getActivity().getString(R.string.pref_key_tracing_on));
        mTracingOn.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
              refresh();
              return true;
            }
        });

        mTags = (MultiSelectListPreference) findPreference(getContext().getString(R.string.pref_key_tags));
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
                        AtraceUtils.atraceDumpAndSendInBackground(getContext(),
                                Receiver.getActiveTags(getContext(), mPrefs, true));
                        return true;
                    }
                });

        findPreference("restore_default_tags").setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        refreshTags(/* restoreDefaultTags =*/ true);
                        Toast.makeText(getContext(),
                            getContext().getString(R.string.default_categories_restored),
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });

        findPreference(getString(R.string.pref_key_quick_setting))
            .setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        ComponentName name = new ComponentName(getContext(), QsService.class);
                        getContext().getPackageManager().setComponentEnabledSetting(name,
                            (Boolean) newValue
                                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP);
                        return true;
                    }
                });


        refreshTags();
        mRefreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshTags();

            }
        };
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(mRefreshReceiver, new IntentFilter(ACTION_REFRESH_TAGS));
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(mRefreshReceiver);

        if (mAlertDialog != null) {
            mAlertDialog.cancel();
            mAlertDialog = null;
        }

        super.onPause();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.main);
    }

    private void refresh() {
        Receiver.updateTracing(getContext(), false);
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
        try {
            mTags.setEntries(entries.toArray(new String[0]));
            mTags.setEntryValues(values.toArray(new String[0]));
            if (restoreDefaultTags || !mPrefs.contains(getContext().getString(R.string.pref_key_tags))) {
                mTags.setValues(Receiver.ATRACE_TAGS);
            }
        } finally {
            mRefreshing = false;
        }
    }
}
