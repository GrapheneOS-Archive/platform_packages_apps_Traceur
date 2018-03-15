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

import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemProperties;
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
import java.util.TreeMap;

/**
 * Utility functions for calling atrace
 */
public class AtraceUtils {

    static final String TAG = "Traceur";

    static final String TRACE_DIRECTORY = "/data/local/traces/";

    private static final Runtime RUNTIME = Runtime.getRuntime();

    public static void atraceStart(String tags, int bufferSizeKb) {
        String cmd = "atrace --async_start -c -b " + bufferSizeKb + " " + tags;

        Log.v(TAG, "Starting async atrace: " + cmd);
        try {
            Process atrace = exec(cmd);
            if (atrace.waitFor() != 0) {
                Log.e(TAG, "atraceStart failed with: " + atrace.exitValue());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void atraceDump(File outFile) {
        String cmd = "atrace --async_stop -z -c -o " + outFile;

        Log.v(TAG, "Dumping async atrace: " + cmd);
        try {
            Process atrace = exec(cmd);

            if (atrace.waitFor() != 0) {
                Log.e(TAG, "atraceDump failed with: " + atrace.exitValue());
            }

            Process ps = exec("ps -AT");

            new Streamer("atraceDump:ps:stdout",
                    ps.getInputStream(), new FileOutputStream(outFile, true /* append */));

            if (ps.waitFor() != 0) {
                Log.v(TAG, "atraceDump:ps failed with: " + ps.exitValue());
            }

            // Set the new file world readable to allow it to be adb pulled.
            outFile.setReadable(true, false); // (readable, ownerOnly)
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static TreeMap<String,String> atraceListCategories() {
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

            TreeMap<String, String> result = new TreeMap<>();
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

    private static Process exec(String cmd) throws IOException {
        String[] cmdarray = {"sh", "-c", cmd};
        Log.v(TAG, "exec: " + Arrays.toString(cmdarray));
        return RUNTIME.exec(cmdarray);
    }

    public static boolean isTracingOn() {
        return !"0".equals(SystemProperties.get("debug.atrace.tags.enableflags", "0"));
    }

    public static void atraceDumpAndSend(final Context context) {
        new AsyncTask<Void, Void, Void>() {
            private File mFile;

            @Override
            protected void onPreExecute() {
                String format = "yyyy-MM-dd-HH-mm-ss";
                String now = new SimpleDateFormat(format, Locale.US).format(new Date());
                mFile = new File(TRACE_DIRECTORY,
                    String.format("trace-%s-%s-%s.ctrace", Build.BOARD, Build.ID, now));

                FileSender.postCaptureNotification(context, mFile);
            }

            @Override
            protected Void doInBackground(Void... params) {
                atraceDump(mFile);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                FileSender.postNotification(context, mFile);
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
