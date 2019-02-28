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

import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Utility functions for calling Perfetto
 */
public class PerfettoUtils implements TraceUtils.TraceEngine {

    static final String TAG = "Traceur";
    public static final String NAME = "PERFETTO";

    private static final String OUTPUT_EXTENSION = "perfetto-trace";
    private static final String TEMP_DIR= "/data/local/traces/";
    private static final String TEMP_TRACE_LOCATION = "/data/local/traces/.trace-in-progress.trace";

    private static final String PERFETTO_TAG = "traceur";
    private static final String MARKER = "PERFETTO_ARGUMENTS";
    private static final int STARTUP_TIMEOUT_MS = 600;
    private static final long MEGABYTES_TO_BYTES = 1024L * 1024L;
    private static final long MINUTES_TO_MILLISECONDS = 60L * 1000L;

    public String getName() {
        return NAME;
    }

    public String getOutputExtension() {
        return OUTPUT_EXTENSION;
    }

    public boolean traceStart(Collection<String> tags, int bufferSizeKb, boolean apps,
            boolean longTrace, int maxLongTraceSizeMb, int maxLongTraceDurationMinutes) {
        if (isTracingOn()) {
            Log.e(TAG, "Attempting to start perfetto trace but trace is already in progress");
            return false;
        } else {
            // Ensure the temporary trace file is cleared.
            try {
                Files.deleteIfExists(Paths.get(TEMP_TRACE_LOCATION));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // Build the perfetto config that will be passed on the command line.
        StringBuilder config = new StringBuilder()
            .append("write_into_file: true\n")
            // Ensure that we flush ftrace data every 30s even if cpus are idle.
            .append("flush_period_ms: 30000\n");

            // If we have set one of the long trace parameters, we must also
            // tell Perfetto to notify Traceur when the long trace is done.
            if (longTrace) {
                config.append("notify_traceur: true\n");

                if (maxLongTraceSizeMb != 0) {
                    config.append("max_file_size_bytes: "
                        + (maxLongTraceSizeMb * MEGABYTES_TO_BYTES) + "\n");
                }

                if (maxLongTraceDurationMinutes != 0) {
                    config.append("duration_ms: "
                        + (maxLongTraceDurationMinutes * MINUTES_TO_MILLISECONDS)
                        + "\n");
                }

                // Default value for long traces to write to file.
                config.append("file_write_period_ms: 2500\n");
            } else {
                // For short traces, we don't write to the file.
                // So, always use the maximum value here: 7 days.
                config.append("file_write_period_ms: 604800000\n");
            }

            config.append("buffers {\n")
            .append("  size_kb: " + bufferSizeKb + "\n")
            .append("  fill_policy: RING_BUFFER\n")
            .append("} \n")
            .append("data_sources {\n")
            .append("  config {\n")
            .append("    name: \"linux.ftrace\"\n")
            .append("    target_buffer: 0\n")
            .append("    ftrace_config {\n");

        for (String tag : tags) {
            // Tags are expected to be all lowercase letters and underscores.
            String cleanTag = tag.replaceAll("[^a-z_]", "");
            if (!cleanTag.equals(tag)) {
                Log.w(TAG, "Attempting to use an invalid tag: " + tag);
            }
            config.append("      atrace_categories: \"" + cleanTag + "\"\n");
        }

        if (apps) {
            config.append("      atrace_apps: \"*\"\n");
        }

        // These parameters affect only the kernel trace buffer size and how
        // frequently it gets moved into the userspace buffer defined above.
        config.append("      buffer_size_kb: 4096\n")
            .append("      drain_period_ms: 250\n")
            .append("    }\n")
            .append("  }\n")
            .append("}\n")
            .append(" \n")

            // For process association
            .append("data_sources {\n")
            .append("  config {\n")
            .append("    name: \"linux.process_stats\"\n")
            .append("    target_buffer: 0\n")
            .append("  }\n")
            .append("} \n");

        String configString = config.toString();

        // If the here-doc ends early, within the config string, exit immediately.
        // This should never happen.
        if (configString.contains(MARKER)) {
            throw new RuntimeException("The arguments to the Perfetto command are malformed.");
        }

        String cmd = "perfetto --detach=" + PERFETTO_TAG
            + " -o " + TEMP_TRACE_LOCATION
            + " -c - --txt"
            + " <<" + MARKER +"\n" + configString + "\n" + MARKER;

        Log.v(TAG, "Starting perfetto trace.");
        try {
            Process process = TraceUtils.exec(cmd, TEMP_DIR);

            // If we time out, ensure that the perfetto process is destroyed.
            if (!process.waitFor(STARTUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "perfetto traceStart has timed out after "
                    + STARTUP_TIMEOUT_MS + " ms.");
                process.destroyForcibly();
                return false;
            }

            if (process.exitValue() != 0) {
                Log.e(TAG, "perfetto traceStart failed with: "
                    + process.exitValue());
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Log.v(TAG, "perfetto traceStart succeeded!");
        return true;
    }

    public void traceStop() {
        Log.v(TAG, "Stopping perfetto trace.");

        if (!isTracingOn()) {
            Log.w(TAG, "No trace appears to be in progress. Stopping perfetto trace may not work.");
        }

        String cmd = "perfetto --stop --attach=" + PERFETTO_TAG;
        try {
            Process process = TraceUtils.exec(cmd);
            if (process.waitFor() != 0) {
                Log.e(TAG, "perfetto traceStop failed with: " + process.exitValue());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean traceDump(File outFile) {
        traceStop();

        // Short-circuit if the file we're trying to dump to doesn't exist.
        if (!Files.exists(Paths.get(TEMP_TRACE_LOCATION))) {
            Log.e(TAG, "In-progress trace file doesn't exist, aborting trace dump.");
            return false;
        }

        Log.v(TAG, "Saving perfetto trace to " + outFile);

        try {
            Os.rename(TEMP_TRACE_LOCATION, outFile.getCanonicalPath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        outFile.setReadable(true, false); // (readable, ownerOnly)
        return true;
    }

    public boolean isTracingOn() {
        String cmd = "perfetto --is_detached=" + PERFETTO_TAG;

        try {
            Process process = TraceUtils.exec(cmd);

            // 0 represents a detached process exists with this name
            // 2 represents no detached process with this name
            // 1 (or other error code) represents an error
            int result = process.waitFor();
            if (result == 0) {
                return true;
            } else if (result == 2) {
                return false;
            } else {
                throw new RuntimeException("Perfetto error: " + result);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
