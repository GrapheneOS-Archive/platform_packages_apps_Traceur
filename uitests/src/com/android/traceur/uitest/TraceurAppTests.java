/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.traceur.uitest;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.Until;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class TraceurAppTests {

    private static final String TRACEUR_PACKAGE = "com.android.traceur";
    private static final int LAUNCH_TIMEOUT_MS = 10000;
    private static final int UI_TIMEOUT_MS = 7500;

    private UiDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        try {
            if (!mDevice.isScreenOn()) {
                mDevice.wakeUp();
            }

            // Press Menu to skip the lock screen.
            // In case we weren't on the lock screen, press Home to return to a clean launcher.
            mDevice.pressMenu();
            mDevice.pressHome();

            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to freeze device orientation.", e);
        }

        mDevice.waitForIdle();

        Context context = InstrumentationRegistry.getContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(TRACEUR_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);    // Clear out any previous instances
        context.startActivity(intent);

        // Wait for the app to appear
        assertTrue(mDevice.wait(Until.hasObject(By.pkg(TRACEUR_PACKAGE).depth(0)),
                  LAUNCH_TIMEOUT_MS));
    }

    @After
    public void tearDown() throws Exception {
        mDevice.unfreezeRotation();
        // Finish Traceur activity.
        mDevice.pressBack();
        mDevice.pressHome();
    }

    @Presubmit
    @Test
    public void testElementsOnMainScreen() throws Exception {
        UiScrollable scrollableMainScreen = new UiScrollable(new UiSelector().scrollable(true));

        if (scrollableMainScreen.exists()) {
            scrollableMainScreen.setAsVerticalList();
            scrollableMainScreen.setMaxSearchSwipes(10);

            boolean recordFound = scrollableMainScreen.scrollTextIntoView("Record trace");
            assertTrue("Record trace switch not found.", recordFound);

            boolean applicationsFound =
                    scrollableMainScreen.scrollTextIntoView("Trace debuggable applications");
            assertTrue("Applications element not found.", applicationsFound);

            boolean categoriesFound = scrollableMainScreen.scrollTextIntoView("Categories");
            assertTrue("Categories element not found.", categoriesFound);

            boolean restoreFound = scrollableMainScreen.scrollTextIntoView("Restore default categories");
            assertTrue("Restore default categories element not found.", restoreFound);

            boolean bufferSizeFound = scrollableMainScreen.scrollTextIntoView("Per-CPU buffer size");
            assertTrue("Per-CPU buffer size element not found.", bufferSizeFound);

            boolean clearFound = scrollableMainScreen.scrollTextIntoView("Clear saved traces");
            assertTrue("Clear saved traces element not found.", clearFound);

            boolean longTraceFound = scrollableMainScreen.scrollTextIntoView("Long traces");
            assertTrue("Long traces element not found.", longTraceFound);

            boolean maxTraceSizeFound = scrollableMainScreen.scrollTextIntoView("Maximum long trace size");
            assertTrue("Maximum long trace size element not found.", maxTraceSizeFound);

            boolean maxTraceDurationFound =
                    scrollableMainScreen.scrollTextIntoView("Maximum long trace duration");
            assertTrue("Maximum long trace duration element not found.", maxTraceDurationFound);

            boolean quickSettingsFound = scrollableMainScreen.scrollTextIntoView("Show Quick Settings tile");
            assertTrue("Show Quick Settings tile switch not found.", quickSettingsFound);
        } else {
            assertNotNull("Record trace switch not found.",
                    mDevice.wait(Until.findObject(By.text("Record trace")),
                    UI_TIMEOUT_MS));
            assertNotNull("Applications element not found.",
                    mDevice.wait(Until.findObject(By.text("Trace debuggable applications")),
                    UI_TIMEOUT_MS));
            assertNotNull("Categories element not found.",
                    mDevice.wait(Until.findObject(By.text("Categories")),
                    UI_TIMEOUT_MS));
            assertNotNull("Restore default categories element not found.",
                    mDevice.wait(Until.findObject(By.text("Restore default categories")),
                    UI_TIMEOUT_MS));
            assertNotNull("Per-CPU buffer size element not found.",
                    mDevice.wait(Until.findObject(By.text("Per-CPU buffer size")),
                    UI_TIMEOUT_MS));
            assertNotNull("Clear saved traces element not found.",
                    mDevice.wait(Until.findObject(By.text("Clear saved traces")),
                    UI_TIMEOUT_MS));
            assertNotNull("Long traces element not found.",
                    mDevice.wait(Until.findObject(By.text("Long traces")),
                    UI_TIMEOUT_MS));
            assertNotNull("Maximum long trace size element not found.",
                    mDevice.wait(Until.findObject(By.text("Maximum long trace size")),
                    UI_TIMEOUT_MS));
            assertNotNull("Maximum long trace duration element not found.",
                    mDevice.wait(Until.findObject(By.text("Maximum long trace duration")),
                    UI_TIMEOUT_MS));
            assertNotNull("Show Quick Settings tile switch not found.",
                    mDevice.wait(Until.findObject(By.text("Show Quick Settings tile")),
                    UI_TIMEOUT_MS));
        }
    }

    /*
     * In this test:
     * Take a trace by toggling 'Record trace' in the UI
     * Tap the notification once the trace is saved, and verify the share dialog appears.
     */
    @Presubmit
    @Test
    public void testSuccessfulTracing() throws Exception {
        UiObject2 recordTraceSwitch = mDevice.wait(Until.findObject(By.text("Record trace")),
                UI_TIMEOUT_MS);
        assertNotNull("Record trace switch not found.", recordTraceSwitch);
        recordTraceSwitch.click();

        mDevice.waitForIdle();

        mDevice.wait(Until.hasObject(By.text("Trace is being recorded")), UI_TIMEOUT_MS);
        mDevice.wait(Until.gone(By.text("Trace is being recorded")), UI_TIMEOUT_MS);

        recordTraceSwitch = mDevice.wait(Until.findObject(By.text("Record trace")), UI_TIMEOUT_MS);
        assertNotNull("Record trace switch not found.", recordTraceSwitch);
        recordTraceSwitch.click();

        mDevice.waitForIdle();

        // Wait for the popover notification to appear and then disappear,
        // so we can reliably click the notification in the notification shade.
        mDevice.wait(Until.hasObject(By.text("Tap to share your trace")), UI_TIMEOUT_MS);
        mDevice.wait(Until.gone(By.text("Tap to share your trace")), UI_TIMEOUT_MS);

        mDevice.openNotification();
        UiObject2 shareNotification = mDevice.wait(Until.findObject(
                By.text("Tap to share your trace")),
                UI_TIMEOUT_MS);
        assertNotNull("Share notification not found.", shareNotification);
        shareNotification.click();

        mDevice.waitForIdle();

        UiObject2 shareDialog = mDevice.wait(Until.findObject(
                By.textContains("Only share system traces with people and apps you trust.")),
                UI_TIMEOUT_MS);
        assertNotNull("Share dialog not found.", shareDialog);

        // The buttons on dialogs sometimes have their capitalization manipulated by themes.
        UiObject2 shareButton = mDevice.wait(Until.findObject(
                By.text(Pattern.compile("share", Pattern.CASE_INSENSITIVE))), UI_TIMEOUT_MS);
        assertNotNull("Share button not found.", shareButton);
        shareButton.click();

        // The share sheet will not appear on AOSP builds, as there are no apps available to share
        // traces with. This checks if Gmail is installed (i.e. if the build is non-AOSP) before
        // verifying that the share sheet exists.
        try {
            Context context = InstrumentationRegistry.getContext();
            context.getPackageManager().getApplicationInfo("com.google.android.gm", 0);
            UiObject2 shareSheet = mDevice.wait(Until.findObject(
                    By.res("android:id/profile_tabhost")), UI_TIMEOUT_MS);
            assertNotNull("Share sheet not found.", shareSheet);
        } catch (PackageManager.NameNotFoundException e) {
            // Gmail is not installed, so the device is on an AOSP build.
        }
    }
}
