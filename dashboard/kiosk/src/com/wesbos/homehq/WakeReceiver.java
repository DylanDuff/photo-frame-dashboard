package com.wesbos.homehq;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The page's wake alarm: lights the screen and brings the dashboard back awake. */
public class WakeReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    ScreenControl.turnScreenOn(context);
    context.startActivity(new Intent(context, MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(MainActivity.EXTRA_WAKE, true));
  }
}
