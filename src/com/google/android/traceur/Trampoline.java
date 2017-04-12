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

import android.app.ActivityManagerNative;
import android.content.Context;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Trampoline {

    public static final String TAG = "Traceur";

    public static void writeTrampoline(Context ctx) {
        System.out.println("Launching trampoline...");

        if (new File("/system/bin/dumpstate_orig").exists()) {
            SystemProperties.set("ctl.start", "dumpstate");
        }

        try {
            copyResToFile(ctx, R.raw.enable_full_tracing, "/data/local/tmp/enable_full_tracing");
            copyResToFile(ctx, R.raw.enable_tracing_permanent, "/data/local/tmp/enable_tracing_permanent");
        } catch (IOException e) {
            Log.e(TAG, "Unable to write trampoline", e);
        }
    }

    private static void copyResToFile(Context ctx, int resId, String targetFile)
            throws IOException {
        InputStream inputStream = ctx.getResources().openRawResource(resId);
        OutputStream outputStream = new FileOutputStream(targetFile);
        copy(inputStream, outputStream);
        outputStream.close();
        inputStream.close();
        new File(targetFile).setExecutable(true);
    }


    private static int copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[2048];
        int total = 0;
        int n;
        while ((n = input.read(buffer)) != -1) {
            output.write(buffer, 0, n);
            total = total + n;
        }
        return total;
    }

}
