package com.wesbos.homehq;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Screen sleep, wake and brightness for the page bridge (see the kiosk README).
 * - Off: the backlight really turns off, through device admin lockNow(). Only once the owner has
 *   made Home HQ a device admin over adb; the CPU may then sleep and stop the page's timers.
 * - Dim: a black cover at minimum window brightness. The page keeps running, a tap wakes it.
 * - A wake time is a native alarm (WakeReceiver), so it works in either state.
 */
class ScreenControl {
  static final String AWAKE = "awake", DIM = "dim", OFF = "off";
  static final long WAKE_LOCK_MS = 10000;

  interface Listener {
    void onScreenState(String state);
  }

  private final Activity activity;
  private final Listener listener;
  private final View cover;
  private volatile String state = AWAKE;
  private float brightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;

  ScreenControl(Activity activity, FrameLayout root, Listener listener) {
    this.activity = activity;
    this.listener = listener;
    cover = new View(activity);
    cover.setBackgroundColor(Color.BLACK);
    cover.setVisibility(View.GONE);
    cover.setOnClickListener(v -> wake());
    cover.setFocusable(false); // a click listener alone makes it focusable on Android 8+
    root.addView(cover);
  }

  String state() {
    return state;
  }

  boolean canTurnOff() {
    DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
    return dpm != null && dpm.isAdminActive(new ComponentName(activity, AdminReceiver.class));
  }

  /** Turns the screen off if allowed, otherwise dims it. wakeAt is epoch ms; 0 sets no alarm. */
  @SuppressWarnings("deprecation")
  void sleep(long wakeAt, boolean allowOff) {
    scheduleWake(wakeAt);
    if (allowOff && canTurnOff()) {
      // Without a lock screen these do nothing; with one, the dashboard comes back above it.
      activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
          | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
      setState(OFF);
      ((DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE)).lockNow();
      return;
    }
    cover.setVisibility(View.VISIBLE);
    applyBrightness(0f);
    setState(DIM);
  }

  void wake() {
    cancelWake();
    turnScreenOn(activity); // also when the power button, not sleep(), turned it off
    cover.setVisibility(View.GONE);
    applyBrightness(brightness);
    setState(AWAKE);
  }

  /** 0 to 1, or below 0 for the system setting. Only applies while Home HQ is in front. */
  void setBrightness(float level) {
    brightness = level < 0 ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : Math.min(1f, level);
    if (!DIM.equals(state)) applyBrightness(brightness);
  }

  /** The screen came back on by other means (power button, alarm): the page is awake again. */
  void onResume() {
    if (OFF.equals(state)) wake();
  }

  private void applyBrightness(float level) {
    WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
    attrs.screenBrightness = level;
    activity.getWindow().setAttributes(attrs);
  }

  private void setState(String next) {
    if (next.equals(state)) return;
    state = next;
    Log.i(MainActivity.TAG, "Screen " + next);
    listener.onScreenState(next);
  }

  private void scheduleWake(long wakeAt) {
    cancelWake();
    if (wakeAt <= System.currentTimeMillis()) return;
    alarms().setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeAt, wakeIntent());
    Log.i(MainActivity.TAG, "Wake alarm in " + (wakeAt - System.currentTimeMillis()) / 1000 + " s");
  }

  private void cancelWake() {
    alarms().cancel(wakeIntent());
  }

  private AlarmManager alarms() {
    return (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
  }

  private PendingIntent wakeIntent() {
    return PendingIntent.getBroadcast(activity, 0, new Intent(activity, WakeReceiver.class),
        PendingIntent.FLAG_UPDATE_CURRENT);
  }

  /** Lights the screen; FLAG_KEEP_SCREEN_ON keeps it on once the lock times out. */
  @SuppressWarnings("deprecation")
  static void turnScreenOn(Context context) {
    PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
        | PowerManager.ON_AFTER_RELEASE, "homehq:wake").acquire(WAKE_LOCK_MS);
  }
}
