package ru.shutoff.cgstarter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class CarMonitor extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null)
            return;
        String action = intent.getAction();
        if (action == null)
            return;
        if (action.equals(Intent.ACTION_DOCK_EVENT)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (preferences.getBoolean("carmode", false)) {
                int dockState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE, -1);
                boolean newMode = (dockState == Intent.EXTRA_DOCK_STATE_CAR);
                boolean curMode = preferences.getBoolean("car_state", false);
                if (curMode != newMode) {
                    SharedPreferences.Editor ed = preferences.edit();
                    ed.putBoolean("car_state", newMode);
                    ed.commit();
                    if (newMode && !OnExitService.isRunCG(context)) {
                        Intent run = new Intent(context, MainActivity.class);
                        run.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(run);
                    }
                }
            }
        }
        if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (preferences.getBoolean("powermode", false) && !preferences.getBoolean("power_state", false)) {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putBoolean("power_state", true);
                ed.commit();
                if (!OnExitService.isRunCG(context)) {
                    Intent run = new Intent(context, MainActivity.class);
                    run.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(run);
                }
            }
        }
        if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (preferences.getBoolean("powermode", false) && preferences.getBoolean("power_state", false)) {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putBoolean("power_state", false);
                ed.putBoolean("car_state", false);
                ed.commit();
            }
        }
    }
}

