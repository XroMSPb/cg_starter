package ru.shutoff.cgstarter;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class SendLocationActivity extends GpsActivity {

    double finish_lat;
    double finish_lon;

    TextView finish_info;

    Button current;
    TextView current_info;

    boolean location_changed;
    AddressRequest request;

    double addr_lat;
    double addr_lon;
    String address;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                State.print(ex);
            }
        });

        super.onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.location);

        try {
            File poi = Environment.getExternalStorageDirectory();
            poi = new File(poi, "CityGuide/routes.dat");
            BufferedReader reader = new BufferedReader(new FileReader(poi));
            reader.readLine();
            boolean current = false;
            while (true) {
                String line = reader.readLine();
                if (line == null)
                    break;
                String[] parts = line.split("\\|");
                if (parts.length == 0)
                    continue;
                String name = parts[0];
                if ((name.length() > 0) && (name.substring(0, 1).equals("#"))) {
                    current = name.equals("#[CURRENT]");
                    continue;
                }
                if (current && name.equals("Finish")) {
                    finish_lat = Double.parseDouble(parts[1]);
                    finish_lon = Double.parseDouble(parts[2]);
                }
            }
            reader.close();
        } catch (Exception ex) {
            // ignore
        }

        Button btn = (Button) findViewById(R.id.finish);
        finish_info = (TextView) findViewById(R.id.finish_info);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((finish_lat != 0) && (finish_lon != 0)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("sms:"));
                    intent.setType("vnd.android-dir/mms-sms");
                    intent.putExtra("sms_body", format(finish_lat) + " " + format(finish_lon));
                    startActivity(intent);
                }
            }
        });

        if ((finish_lat == 0) && (finish_lon == 0)) {
            btn.setEnabled(false);
            finish_info.setText(R.string.no_finish);
        } else {
            btn.setEnabled(true);
            finish_info.setText(format(finish_lat) + ", " + format(finish_lon) + "\n\n");
            AddressRequest req = new AddressRequest() {
                @Override
                protected void onPostExecute(String s) {
                    if (s != null)
                        finish_info.setText(format(finish_lat) + ", " + format(finish_lon) + "\n" + s);
                }
            };
            req.execute(finish_lat, finish_lon);
        }

        current = (Button) findViewById(R.id.current);
        current_info = (TextView) findViewById(R.id.current_info);
        current.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentBestLocation != null) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("sms:"));
                    intent.setType("vnd.android-dir/mms-sms");
                    intent.putExtra("sms_body", format(currentBestLocation.getLatitude()) + " " + format(currentBestLocation.getLongitude()));
                    startActivity(intent);
                }
            }
        });

    }

    @Override
    void locationChanged() {
        if (currentBestLocation == null) {
            current.setEnabled(false);
            current_info.setText(R.string.find_location);
            return;
        }
        double lat = currentBestLocation.getLatitude();
        double lon = currentBestLocation.getLongitude();
        String info = format(lat) + ", " + format(lon) + "\n";
        if (address != null)
            info += address;
        if (OnExitService.calc_distance(lat, lon, addr_lat, addr_lon) > 50) {
            location_changed = true;
            if (request != null) {
                location_changed = true;
            } else {
                if (currentBestLocation != null) {
                    addr_lat = currentBestLocation.getLatitude();
                    addr_lon = currentBestLocation.getLongitude();
                    location_changed = false;
                    request = new AddressRequest() {
                        @Override
                        protected void onPostExecute(String s) {
                            address = s;
                            locationChanged();
                        }
                    };
                    request.execute(currentBestLocation.getLatitude(), currentBestLocation.getLongitude());
                }
            }
        }
        current_info.setText(info);
    }

    static String format(double n) {
        String res = n + "";
        if (res.length() > 8)
            res = res.substring(0, 8);
        return res;
    }

}