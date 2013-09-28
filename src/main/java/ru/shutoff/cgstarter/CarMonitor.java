package ru.shutoff.cgstarter;

import android.app.UiModeManager;
import android.bluetooth.BluetoothDevice;
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
        State.appendLog(action);
        if (action.equals(UiModeManager.ACTION_ENTER_CAR_MODE)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (preferences.getBoolean(State.CAR_MODE, false)) {
                setCarMode(context, true);
                abortBroadcast();
            }
        }
        if (action.equals(Intent.ACTION_DOCK_EVENT)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (preferences.getBoolean(State.CAR_MODE, false)) {
                int dockState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE, -1);
                setCarMode(context, (dockState == Intent.EXTRA_DOCK_STATE_CAR));
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
        if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String devices = preferences.getString(State.BT_DEVICES, "");
            if (devices.equals(""))
                return;
            SharedPreferences.Editor ed = preferences.edit();
            ed.putString(State.BT_DEVICES, devices + ";" + device.getAddress());
            ed.commit();
        }
        if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            OnExitService.turnOffBT(context, device.getAddress());
        }
        if (action.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
            OnExitService.call_number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
        }
        if (action.equals(START)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String route = intent.getStringExtra("ROUTE");
            String route_points = intent.getStringExtra("POINTS");
            if (route == null)
                route = "";
            if (route_points == null)
                route_points = "";
            int n_route = intent.getIntExtra("ROUTE", 0);
            if (n_route > 0) {
                State.Point[] points = State.get(preferences);
                if (n_route > points.length)
                    return;
                State.Point p = points[n_route - 1];
                if ((p.lat.equals("")) && (p.lng.equals("")))
                    return;
                route = p.lat + "|" + p.lng;
                route_points = p.points;
            } else if (n_route < 0) {
                MainActivity.removeRoute(context);
                route = "";
            }
            if (!route.equals(""))
                MainActivity.createRoute(context, route, route_points);
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

    void setCarMode(Context context, boolean newMode) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean curMode = preferences.getBoolean(State.CAR_STATE, false);
        if (curMode != newMode) {
            SharedPreferences.Editor ed = preferences.edit();
            ed.putBoolean(State.CAR_STATE, newMode);
            ed.commit();
            if (newMode) {
                if (!OnExitService.isRunCG(context)) {
                    Intent run = new Intent(context, MainActivity.class);
                    run.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(run);
                    ed.putBoolean(State.CAR_START_CG, true);
                    ed.commit();
                }
            } else {
                if (preferences.getBoolean(State.CAR_START_CG, false))
                    OnExitService.killCG(context);
            }
        }
    }
}

