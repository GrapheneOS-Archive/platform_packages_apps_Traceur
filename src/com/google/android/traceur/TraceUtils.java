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

import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Utility functions for tracing.
 * Will call atrace or perfetto depending on the setting.
 */
public class TraceUtils {

    static final String TAG = "Traceur";

    public static final String TRACE_DIRECTORY = "/data/local/traces/";

    private static final String DEBUG_TRACING_FILE = "/sys/kernel/debug/tracing/tracing_on";
    private static final String TRACING_FILE = "/sys/kernel/tracing/tracing_on";

    private static final Runtime RUNTIME = Runtime.getRuntime();

    public static boolean traceStart(String tags, int bufferSizeKb, boolean apps) {
        return AtraceUtils.atraceStart(tags, bufferSizeKb, apps);
    }

    public static void traceStop() {
        AtraceUtils.atraceStop();
    }

    public static boolean traceDump(File outFile) {
        return AtraceUtils.atraceDump(outFile);
    }

    public static TreeMap<String, String> listCategories() {
        return AtraceUtils.atraceListCategories();
    }

    public static void clearSavedTraces() {
        String cmd = "rm -f " + TRACE_DIRECTORY + "trace-*.ctrace";

        Log.v(TAG, "Clearing trace directory: " + cmd);
        try {
            Process rm = exec(cmd);

            if (rm.waitFor() != 0) {
                Log.e(TAG, "clearSavedTraces failed with: " + rm.exitValue());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Process exec(String cmd) throws IOException {
        String[] cmdarray = {"sh", "-c", cmd};
        Log.v(TAG, "exec: " + Arrays.toString(cmdarray));
        return RUNTIME.exec(cmdarray);
    }

    public static boolean isTracingOn() {
        boolean userInitiatedTracingFlag =
            "1".equals(SystemProperties.get("debug.atrace.user_initiated", ""));

        if (!userInitiatedTracingFlag) {
            return false;
        }

        boolean tracingOnFlag = false;

        try {
            List<String> tracingOnContents;

            Path debugTracingOnPath = Paths.get(DEBUG_TRACING_FILE);
            Path tracingOnPath = Paths.get(TRACING_FILE);

            if (Files.isReadable(debugTracingOnPath)) {
                tracingOnContents = Files.readAllLines(debugTracingOnPath);
            } else if (Files.isReadable(tracingOnPath)) {
                tracingOnContents = Files.readAllLines(tracingOnPath);
            } else {
                return false;
            }

            tracingOnFlag = !tracingOnContents.get(0).equals("0");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return userInitiatedTracingFlag && tracingOnFlag;
    }

    public static String getOutputFilename() {
        String format = "yyyy-MM-dd-HH-mm-ss";
        String now = new SimpleDateFormat(format, Locale.US).format(new Date());
        return String.format("trace-%s-%s-%s.ctrace", Build.BOARD, Build.ID, now);
    }

    public static File getOutputFile(String filename) {
        return new File(TraceUtils.TRACE_DIRECTORY, filename);
    }

}
