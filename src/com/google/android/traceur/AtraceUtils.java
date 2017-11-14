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

import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemProperties;
import android.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Utility functions for calling atrace
 */
public class AtraceUtils {

    static final String TAG = "Traceur";

    private static final Runtime RUNTIME = Runtime.getRuntime();

    public static void atraceStart(String tags, int bufferSizeKb) {
        String cmd = "atrace --async_start -c -b " + bufferSizeKb + " " + tags;

        Log.i(TAG, "Starting async atrace: " + cmd);
        try {
            Process atrace = exec(cmd);
            if (atrace.waitFor() != 0) {
                Log.e(TAG, "atraceStart failed with: " + atrace.exitValue());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void atraceDump(String tags, int bufferSizeKb, File outFile) {
        String cmd = "atrace --async_dump -z -c -b " + bufferSizeKb + " " + tags;

        Log.i(TAG, "Dumping async atrace: " + cmd);
        try {
            Process atrace = exec(cmd);

            new Streamer("atraceDump:stdout",
                    atrace.getInputStream(), new FileOutputStream(outFile));

            if (atrace.waitFor() != 0) {
                Log.e(TAG, "atraceDump failed with: " + atrace.exitValue());
            }

            Process ps = exec("ps -t");

            new Streamer("atraceDump:ps:stdout",
                    ps.getInputStream(), new FileOutputStream(outFile, true /* append */));

            if (ps.waitFor() != 0) {
                Log.e(TAG, "atraceDump:ps failed with: " + ps.exitValue());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void atraceStop() {
        String cmd = "atrace -t 0";

        Log.i(TAG, "Stopping async atrace: " + cmd);
        try {
            Process atrace = exec(cmd);
            if (atrace.waitFor() != 0) {
                Log.e(TAG, "atraceStop failed with: " + atrace.exitValue());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ArrayMap<String,String> atraceListCategories() {
        String cmd = "atrace --list_categories";

        Log.v(TAG, "Listing tags: " + cmd);
        try {
            Process atrace = exec(cmd);

            new Logger("atraceListCat:stderr", atrace.getErrorStream());
            BufferedReader stdout = new BufferedReader(
                    new InputStreamReader(atrace.getInputStream()));

            if (atrace.waitFor() != 0) {
                Log.e(TAG, "atraceListCategories failed with: " + atrace.exitValue());
            }

            ArrayMap<String, String> result = new ArrayMap<>();
            String line;
            while ((line = stdout.readLine()) != null) {
                String[] fields = line.trim().split(" - ", 2);
                if (fields.length == 2) {
                    result.put(fields[0], fields[1]);
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Process exec(String cmd) throws IOException {
        String[] cmdarray = {"sh", "-c", /*"su root " +*/ cmd};
        Log.v(TAG, "exec: " + Arrays.toString(cmdarray));
        return RUNTIME.exec(cmdarray);
    }

    public static boolean isTracingOn() {
        return !"0".equals(SystemProperties.get("debug.atrace.tags.enableflags", "0"));
    }

    public static void atraceDumpAndSend(Context context, String tags, int bufferSizeKb) {
        String format = "yyyy-MM-dd-HH-mm-ss";
        String now = new SimpleDateFormat(format, Locale.US).format(new Date());
        ensureBugreportDirectory(context);
        File file = new File(String.format("/bugreports/traceur-%s-%s-%s.txt",
                Build.BOARD, Build.ID, now));
        FileSender.postCaptureNotification(context, file);
        atraceDump(tags, bufferSizeKb, file);
        FileSender.postNotification(context, file);
    }

    private static void ensureBugreportDirectory(Context context) {
        try {
            new File(context.createPackageContext(
                    "com.android.shell", 0).getFilesDir(), "bugreports").mkdir();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void atraceDumpAndSendInBackground(final Context context,
            final String tags) {
        Toast.makeText(context, "Saving trace...", Toast.LENGTH_SHORT).show();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                atraceDumpAndSend(context, tags, Receiver.BUFFER_SIZE_KB);
                return null;
            }
        }.execute();
    }

    /**
     * Streams data from an InputStream to an OutputStream
     */
    private static class Streamer {
        private boolean mDone;

        Streamer(final String tag, final InputStream in, final OutputStream out) {
            new Thread(tag) {
                @Override
                public void run() {
                    int read;
                    byte[] buf = new byte[2 << 10];
                    try {
                        while ((read = in.read(buf)) != -1) {
                            out.write(buf, 0, read);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error while streaming " + tag);
                    } finally {
                        try {
                            out.close();
                        } catch (IOException e) {
                            // Welp.
                        }
                        synchronized (Streamer.this) {
                            mDone = true;
                            Streamer.this.notify();
                        }
                    }
                }
            }.start();
        }

        synchronized boolean isDone() {
            return mDone;
        }

        synchronized void waitForDone() {
            while (!isDone()) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Streams data from an InputStream to an OutputStream
     */
    private static class Logger {

        Logger(final String tag, final InputStream in) {
            new Thread(tag) {
                @Override
                public void run() {
                    int read;
                    String line;
                    BufferedReader r = new BufferedReader(new InputStreamReader(in));
                    try {
                        while ((line = r.readLine()) != null) {
                            Log.e(TAG, tag + ": " + line);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error while streaming " + tag);
                    } finally {
                        try {
                            r.close();
                        } catch (IOException e) {
                            // Welp.
                        }
                    }
                }
            }.start();
        }
    }
}
