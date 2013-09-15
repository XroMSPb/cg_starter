package ru.shutoff.cgstarter;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class CarMonitor extends BroadcastReceiver {

    static final String START = "ru.shutoff.cgstarter.START";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null)
            return;
        String action = intent.getAction();
        if (action == null)
            return;
        if (action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
            String state = intent.getStringExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE);
            if (state == null)
                return;
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor ed = preferences.edit();
            if (state.equals(BluetoothAdapter.STATE_CONNECTED) ||
                    state.equals(BluetoothAdapter.STATE_CONNECTING)) {
                ed.putBoolean(State.BT_CONNECTED, true);
            } else {
                ed.remove(State.BT_CONNECTED);
            }
            ed.commit();
            if (OnExitService.isRunCG(context))
                return;
            OnExitService.turnOffBT(context);
            return;
        }
        if (action.equals(Intent.ACTION_DOCK_EVENT)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (preferences.getBoolean(State.CAR_MODE, false)) {
                int dockState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE, -1);
                boolean newMode = (dockState == Intent.EXTRA_DOCK_STATE_CAR);
                boolean curMode = preferences.getBoolean(State.CAR_STATE, false);
                if (curMode != newMode) {
                    SharedPreferences.Editor ed = preferences.edit();
                    ed.putBoolean(State.CAR_STATE, newMode);
                    ed.commit();
                    if (newMode && !OnExitService.isRunCG(context)) {
                        Intent run = new Intent(context, MainActivity.class);
                        run.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(run);
                        ed.putBoolean(State.CAR_START_CG, true);
                        ed.commit();
                    }
                }
            } else if (preferences.getBoolean(State.CAR_START_CG, false)) {
                OnExitService.killCG(context);
            }
        }
        if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String interval = preferences.getString(State.POWER_TIME, "");
            if (State.inInterval(interval)) {
                if (!OnExitService.isRunCG(context)) {
                    Intent run = new Intent(context, MainActivity.class);
                    run.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(run);
                }
            }
        }
        if (action.equals(START)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String route = intent.getStringExtra("ROUTE");
            if (route == null)
                route = "";
            int n_route = intent.getIntExtra("ROUTE", 0);
            if (n_route > 0) {
                State.Point[] points = State.get(preferences);
                if (n_route > points.length)
                    return;
                State.Point p = points[n_route - 1];
                if ((p.lat.equals("")) && (p.lng.equals("")))
                    return;
                route = p.lat + "|" + p.lng;
            } else if (n_route < 0) {
                MainActivity.removeRoute(context);
                route = "";
            }
            if (!route.equals(""))
                MainActivity.createRoute(context, route);
            if (!MainActivity.setState(context, new State.OnBadGPS() {
                @Override
                public void gps_message(Context context) {
                    Toast toast = Toast.makeText(context, context.getString(R.string.no_gps_title), Toast.LENGTH_SHORT);
                    toast.show();
                }
            })) {
                MainActivity.setState(context, null);
            }
            Intent run = context.getPackageManager().getLaunchIntentForPackage(State.CG_PACKAGE);
            if (run == null) {
                Toast toast = Toast.makeText(context, context.getString(R.string.no_cg), Toast.LENGTH_SHORT);
                toast.show();
                return;
            }
            context.startActivity(run);
            Intent service = new Intent(context, OnExitService.class);
            service.setAction(OnExitService.START);
            context.startService(service);
        }
    }
}

