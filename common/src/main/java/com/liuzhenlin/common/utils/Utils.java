/*
 * Created on 2021-3-25 7:43:43 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.util.ObjectsCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;

public class Utils {
    private Utils() {
    }

    /**
     * Combines some integers under different bit masks in one integer (32 bits)
     */
    public static int combineInts(int[] ints, int[] masks) {
        int result = 0;
        for (int i = 0; i < ints.length; i++) {
            result |= (ints[i] << Integer.numberOfTrailingZeros(masks[i])) & masks[i];
        }
        return result;
    }

    /**
     * Puts an integer to the integer combining a set of integers or replace an existing one
     * under the same bit mask.
     */
    public static int putIntToCombinedInts(int ints, int val, int mask) {
        return (ints & ~mask) | ((val << Integer.numberOfTrailingZeros(mask)) & mask);
    }

    /**
     * Retrieves an integer from the integer combining a set of integers or zero if not exists.
     */
    public static int takeIntFromCombinedInts(int ints, int mask) {
        int maskShift = Integer.numberOfTrailingZeros(mask);
        int origin = (ints & mask) >>> maskShift;
        int signMaskShift = Integer.bitCount(mask) - 1;
        int singMask = 1 << signMaskShift;
        int sign = origin & singMask;
        return (sign > 0 ? ~(mask >>> maskShift) : 0) | origin;
    }

    /** Lightweight choice to {@link Math#round(float)} */
    public static int roundFloat(float value) {
        return (int) (value > 0 ? value + 0.5f : value - 0.5f);
    }

    /** Lightweight choice to {@link Math#round(double)} */
    public static long roundDouble(double value) {
        return (long) (value > 0 ? value + 0.5 : value - 0.5);
    }

    /**
     * Judges if two floating-point numbers (float) are equal, ignoring very small precision errors.
     */
    public static boolean areEqualIgnorePrecisionError(float value1, float value2) {
        return Math.abs(value1 - value2) < 0.0001f;
    }

    /**
     * Judges if two floating-point numbers (double) are equal, ignoring very small precision errors.
     */
    public static boolean areEqualIgnorePrecisionError(double value1, double value2) {
        return Math.abs(value1 - value2) < 0.0001d;
    }

    /**
     * Returns the string representation of a floating point number rounded up to 2 fraction digits.
     */
    public static String roundDecimalUpTo2FractionDigitsString(double value) {
        return roundDecimalToString(value, 2);
    }

    /**
     * See {@link #roundDecimalToString(double, int, int, boolean)
     *             roundDecimalToString(value, 0, maxFractionDigits, false)}
     */
    public static String roundDecimalToString(double value, int maxFractionDigits) {
        return roundDecimalToString(value, 0, maxFractionDigits);
    }

    /**
     * See {@link #roundDecimalToString(double, int, int, boolean)
     *             roundDecimalToString(value, minFractionDigits, maxFractionDigits, false)}
     */
    public static String roundDecimalToString(
            double value, int minFractionDigits, int maxFractionDigits) {
        return roundDecimalToString(value, minFractionDigits, maxFractionDigits, false);
    }

    /**
     * Rounds a floating point number up to {@code maxFractionDigits} fraction digits and at least
     * {@code minFractionDigits} digits, then returns it as a string.
     *
     * @param value             the decimal to be rounded half up
     * @param minFractionDigits see the parameter of {@link NumberFormat#setMinimumFractionDigits(int)}
     * @param maxFractionDigits see the parameter of {@link NumberFormat#setMaximumFractionDigits(int)}
     * @param groupingUsed      see the parameter of {@link NumberFormat#setGroupingUsed(boolean)}
     * @return the equivalent string representation of the rounded decimal.
     */
    public static String roundDecimalToString(
            double value, int minFractionDigits, int maxFractionDigits, boolean groupingUsed) {
        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setGroupingUsed(groupingUsed);
        nf.setMinimumFractionDigits(minFractionDigits);
        nf.setMaximumFractionDigits(maxFractionDigits);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        return nf.format(value);
    }

    /**
     * Waits for the given action to complete on the thread the handler targets to.
     */
    public static void runOnHandlerSync(@NonNull Handler handler, @NonNull Runnable action) {
        if (Thread.currentThread() != handler.getLooper().getThread()) {
            final Object lock = new Object();
            final boolean[] runOver = {false};

            handler.post(() -> {
                action.run();
                synchronized (lock) {
                    runOver[0] = true;
                    lock.notify();
                }
            });

            synchronized (lock) {
                while (!runOver[0]) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            action.run();
        }
    }

    /**
     * Runs the given action on the {@code handler}'s thread once the specified {@code condition}
     * meets.
     */
    public static void postTillConditionMeets(
            @NonNull Handler handler, @NonNull Runnable action, @NonNull Condition condition) {
        if (Thread.currentThread() == handler.getLooper().getThread()) {
            if (condition.meets()) {
                action.run();
                return;
            }
        }
        handler.post(() -> postTillConditionMeets(handler, action, condition));
    }

    /**
     * Checks if any notifications that have the same {@code id} and {@code tag}
     * have not yet been dismissed by the user or
     * {@link NotificationManager#cancel(String, int) cancel}ed by the app.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    public static boolean hasNotification(
            @NonNull NotificationManager nm, int id, @Nullable String tag) {
        StatusBarNotification[] ns = nm.getActiveNotifications();
        if (ns != null) {
            for (StatusBarNotification n : ns) {
                if (n.getId() == id && ObjectsCompat.equals(n.getTag(), tag)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether the service is running.
     *
     * @param cls The service class.
     * @return {@code true} if it is running, {@code false} otherwise.
     */
    public static boolean isServiceRunning(@NonNull Context context, @NonNull Class<?> cls) {
        return isServiceRunning(context, cls.getName());
    }

    /**
     * Returns whether the service is running.
     *
     * @param className The name of the service class.
     * @return {@code true} if it is running, {@code false} otherwise.
     */
    public static boolean isServiceRunning(@NonNull Context context, @NonNull String className) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> infos = am.getRunningServices(Integer.MAX_VALUE);
        if (infos == null || infos.size() == 0) {
            return false;
        }
        for (ActivityManager.RunningServiceInfo info : infos) {
            if (className.equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Copies the given plain <strong>text</strong> onto system clipboard.
     *
     * @param context The {@link Context} to get the {@link Context#CLIPBOARD_SERVICE} Service
     * @param label   User-visible label for the copied text
     * @param text    The text to copy from
     */
    public static void copyPlainTextToClipboard(
            @NonNull Context context, @Nullable String label, @Nullable String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        // 创建纯文本型ClipData
        ClipData cd = ClipData.newPlainText(label, text);
        // 将ClipData内容放到系统剪贴板里
        cm.setPrimaryClip(cd);
    }

    /**
     * Creates a new MotionEvent with {@link MotionEvent#ACTION_CANCEL} action being performed,
     * filling in a subset of the basic motion values. Those not specified here are:
     * <ul>
     * <li>down time (current milliseconds since boot)</li>
     * <li>event time (current milliseconds since boot)</li>
     * <li>x and y coordinates of this event (always 0)</li>
     * <li>
     * The state of any meta/modifier keys that were in effect when the event was generated (always 0)
     * </li>
     * </ul>
     */
    @NonNull
    public static MotionEvent obtainCancelEvent() {
        final long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
    }

    /**
     * Walks up the hierarchy for the given `view` to determine if it is inside a scrolling container.
     */
    public static boolean isInScrollingContainer(@NonNull View view) {
        ViewParent p = view.getParent();
        while (p instanceof ViewGroup) {
            if (((ViewGroup) p).shouldDelayChildPressedState()) {
                return true;
            }
            p = p.getParent();
        }
        return false;
    }

    /**
     * Indicates whether or not the view's layout direction is right-to-left.
     * This is resolved from layout attribute and/or the inherited value from its parent
     *
     * @return true if the layout direction is right-to-left
     */
    public static boolean isLayoutRtl(@NonNull View view) {
        return ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL;
    }

    /**
     * Converts script specific gravity to absolute horizontal values,
     * leaving the vertical values unchanged.
     * <p>
     * if horizontal direction is LTR, then START will set LEFT and END will set RIGHT.
     * if horizontal direction is RTL, then START will set RIGHT and END will set LEFT.
     *
     * @param parent  The parent view where to get the layout direction.
     * @param gravity The gravity to convert to absolute values.
     * @return gravity converted to absolute horizontal & original vertical values.
     */
    public static int getAbsoluteGravity(@NonNull View parent, int gravity) {
        final int layoutDirection = ViewCompat.getLayoutDirection(parent);
        return GravityCompat.getAbsoluteGravity(gravity, layoutDirection);
    }

    /**
     * Converts script specific gravity to absolute horizontal values.
     * <p>
     * if horizontal direction is LTR, then START will set LEFT and END will set RIGHT.
     * if horizontal direction is RTL, then START will set RIGHT and END will set LEFT.
     *
     * @param parent  The parent view where to get the layout direction.
     * @param gravity The gravity to convert to absolute horizontal values.
     * @return gravity converted to absolute horizontal values.
     */
    public static int getAbsoluteHorizontalGravity(@NonNull View parent, int gravity) {
        return getAbsoluteGravity(parent, gravity) & Gravity.HORIZONTAL_GRAVITY_MASK;
    }
}
