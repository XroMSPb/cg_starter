package ru.shutoff.cgstarter;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.RecognizerIntent;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.ParseException;

import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

public class SearchActivity extends GpsActivity {

    PlaceholderFragment fragment;

    static boolean isVoiceSearch(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        return activities.size() > 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            fragment = new PlaceholderFragment();
            fragment.location = getLastBestLocation();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, fragment)
                    .commit();
        }

        setResult(RESULT_CANCELED);
    }

    @Override
    public void locationChanged() {
        if (fragment != null) {
            fragment.location = getLastBestLocation();
            if (fragment.location != null)
                fragment.startSearch();
        }
    }

    public static class PlaceholderFragment extends Fragment {

        Vector<Address> addr_list;
        Vector<Phrase> phrases;
        int phrase;

        ListView results;
        View progress;
        int prev_size;

        Location location;
        boolean started;

        @Override
        public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            View rootView = inflater.inflate(R.layout.list, container, false);
            progress = rootView.findViewById(R.id.progress);
            progress.setVisibility(View.GONE);
            results = (ListView) rootView.findViewById(R.id.list);
            results.setVisibility(View.GONE);

            phrases = new Vector<Phrase>();
            addr_list = new Vector<Address>();

            if (location != null)
                startSearch();

            CountDownTimer timer = new CountDownTimer(2000, 2000) {
                @Override
                public void onTick(long millisUntilFinished) {

                }

                @Override
                public void onFinish() {
                    startSearch();
                }
            };
            timer.start();

            return rootView;
        }

        void startSearch() {

            if (started)
                return;

            started = true;
            progress.setVisibility(View.VISIBLE);

            Intent data = getActivity().getIntent();

            ArrayList<String> res = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            float[] scopes = data.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES);
            if (scopes == null)
                scopes = new float[0];

            phrases = new Vector<Phrase>();
            Bookmarks.Point[] points = Bookmarks.get(getActivity());
            for (int i = 0; i < res.size(); i++) {
                String r = res.get(i);
                float scope = (i < scopes.length) ? scopes[i] : 0.01f;
                for (Bookmarks.Point p : points) {
                    float ratio = compare(p.name, r) * 10;
                    if (ratio > 4) {
                        int n = 0;
                        for (n = 0; n < addr_list.size(); n++) {
                            Address addr = addr_list.get(n);
                            if ((addr.lat == p.lat) && (addr.lon == p.lng)) {
                                addr.scope += scope * ratio;
                                break;
                            }
                        }
                        if (n >= addr_list.size()) {
                            Address address = new Address();
                            address.name = p.name;
                            address.address = "";
                            address.lat = p.lat;
                            address.lon = p.lng;
                            address.scope = scope * ratio;
                            addr_list.add(address);
                        }
                    }
                }
                if (scope == 0)
                    continue;
                Phrase phrase = new Phrase();
                phrase.phrase = r;
                phrase.scope = scope;
                phrases.add(phrase);
            }
            updateResults();
            phrase = 0;
            if (phrases.size() == 0) {
                if (getActivity() != null)
                    getActivity().finish();
                return;
            }
            new Request();
        }

        void updateResults() {
            if (prev_size == addr_list.size())
                return;
            prev_size = addr_list.size();
            if (addr_list.size() == 0)
                return;
            if (location != null) {
                for (Address addr : addr_list) {
                    if (addr.distance != 0)
                        continue;
                    addr.distance = OnExitService.calc_distance(location.getLatitude(), location.getLongitude(), addr.lat, addr.lon);
                    addr.scope /= Math.log(200 + addr.distance);
                }
                Collections.sort(addr_list, new Comparator<Address>() {
                    @Override
                    public int compare(Address lhs, Address rhs) {
                        if (lhs.scope < rhs.scope)
                            return 1;
                        if (lhs.scope > rhs.scope)
                            return -1;
                        return 0;
                    }
                });
            }

            if (results.getVisibility() == View.VISIBLE) {
                BaseAdapter adapter = (BaseAdapter) results.getAdapter();
                adapter.notifyDataSetChanged();
                return;
            }
            results.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() {
                    return addr_list.size();
                }

                @Override
                public Object getItem(int position) {
                    return addr_list.get(position);
                }

                @Override
                public long getItemId(int position) {
                    return position;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = convertView;
                    if (v == null) {
                        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(LAYOUT_INFLATER_SERVICE);
                        v = inflater.inflate(R.layout.addr_item, null);
                    }
                    Address addr = addr_list.get(position);
                    TextView tv = (TextView) v.findViewById(R.id.addr);
                    tv.setText(addr.address);
                    tv = (TextView) v.findViewById(R.id.name);
                    tv.setText(addr.name);
                    tv = (TextView) v.findViewById(R.id.dist);
                    if (addr.distance < 100) {
                        tv.setText("");
                    } else {
                        DecimalFormat df = new DecimalFormat("#.#");
                        tv.setText(df.format(addr.distance / 1000) + getString(R.string.km));
                    }
                    return v;
                }
            });
            progress.setVisibility(View.GONE);
            results.setVisibility(View.VISIBLE);
            results.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    Address addr = addr_list.get(i);
                    if (OnExitService.isRunCG(getActivity()))
                        CarMonitor.killCG(getActivity());
                    CarMonitor.startCG(getActivity(), addr.lat + "|" + addr.lon, null, addr);
                    getActivity().setResult(RESULT_OK);
                    getActivity().finish();
                }
            });
        }

        float compare(String s1, String s2) {
            String[] w1 = s1.toUpperCase().split(" ");
            String[] w2 = s2.toUpperCase().split(" ");
            float res = 0;
            for (String w : w1) {
                if (w.equals(""))
                    continue;
                for (String s : w2) {
                    if (s.equals(""))
                        continue;
                    int lfd = StringUtils.getLevenshteinDistance(w, s);
                    float ratio = ((float) lfd) / Math.min(s.length(), w.length());
                    if (ratio < 0.5)
                        res += 1 - ratio * 2;
                }
            }
            return res / Math.max(w1.length, w2.length);
        }

        class Request extends LocationRequest {

            Request() {
                Phrase p = phrases.get(phrase);
                exec(p.phrase);
            }

            @Override
            Location getLocation() {
                return location;
            }

            @Override
            void showError(String error) {

            }

            @Override
            void result(Vector<Address> result) {
                if (result.size() > 0) {
                    float scope = phrases.get(phrase).scope;
                    for (Address addr : result) {
                        addr.scope = scope;
                        addr_list.add(addr);
                    }
                    updateResults();
                }
                if (++phrase >= phrases.size()) {
                    int near_count = 0;
                    Location location = getLocation();
                    if (location != null) {
                        for (Address addr : addr_list) {
                            if (addr.distance == 0)
                                addr.distance = OnExitService.calc_distance(addr.lat, addr.lon, location.getLatitude(), location.getLongitude());
                            if (addr.distance < 10000)
                                near_count++;
                        }
                    } else {
                        near_count = addr_list.size();
                    }
                    if (near_count == 0) {
                        phrase = 0;
                        new NearRequest();
                        return;
                    }
                    BaseAdapter adapter = (BaseAdapter) results.getAdapter();
                    adapter.notifyDataSetChanged();
                    return;
                }
                new Request();
            }
        }

        class NearRequest extends PlaceRequest {

            NearRequest() {
                Phrase p = phrases.get(phrase);
                exec(p.phrase, 1000);
            }

            @Override
            Location getLocation() {
                return location;
            }

            @Override
            void showError(String error) {

            }

            @Override
            void result(Vector<Address> result) {
                if (result.size() > 0) {
                    float scope = phrases.get(phrase).scope;
                    for (Address addr : result) {
                        addr.scope = scope;
                        addr_list.add(addr);
                    }
                    updateResults();
                }
                if (++phrase >= phrases.size()) {
                    int near_count = 0;
                    Location location = getLocation();
                    if (location != null) {
                        for (Address addr : addr_list) {
                            if (addr.distance == 0)
                                addr.distance = OnExitService.calc_distance(addr.lat, addr.lon, location.getLatitude(), location.getLongitude());
                            if (addr.distance < 10000)
                                near_count++;
                        }
                    } else {
                        near_count = addr_list.size();
                    }
                    if (near_count == 0) {
                        phrase = 0;
                        new LongRequest();
                        return;
                    }
                    BaseAdapter adapter = (BaseAdapter) results.getAdapter();
                    adapter.notifyDataSetChanged();
                    return;
                }
                new NearRequest();
            }
        }

        class LongRequest extends PlaceRequest {

            LongRequest() {
                Phrase p = phrases.get(phrase);
                exec(p.phrase, 50000);
            }

            @Override
            Location getLocation() {
                return location;
            }

            @Override
            void showError(String error) {

            }

            @Override
            void result(Vector<Address> result) {
                if (result.size() > 0) {
                    float scope = phrases.get(phrase).scope;
                    for (Address addr : result) {
                        addr.scope = scope;
                        addr_list.add(addr);
                    }
                    updateResults();
                }
                if (++phrase >= phrases.size()) {
                    if (addr_list.size() == 0) {
                        getActivity().finish();
                        return;
                    }
                    BaseAdapter adapter = (BaseAdapter) results.getAdapter();
                    adapter.notifyDataSetChanged();
                    return;
                }
                new LongRequest();
            }
        }
    }

    static class Phrase {
        String phrase;
        float scope;
    }

    static class Address {
        String address;
        String name;
        double lat;
        double lon;
        double distance;
        float scope;
    }

    static abstract class PlaceRequest extends HttpTask {

        String error;

        abstract Location getLocation();

        abstract void showError(String error);

        abstract void result(Vector<Address> result);

        void exec(String addr, int radius) {
            String url = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=%1&sensor=true";
            Location location = getLocation();
            if (location != null) {
                double lat = location.getLatitude();
                double lon = location.getLongitude();
                url += "&location=" + lat + "," + lon + "&radius=" + radius;
            }
            url += "&key=AIzaSyBljQKazFWpl9nyGHp-lu8ati7QjMbwzsU";
            url += "&language=" + Locale.getDefault().getLanguage();
            execute(url, addr);
        }

        void result(String result) {
            JsonArray res = Json.parse(result).asObject().get("results").asArray();
            Vector<Address> r = new Vector<Address>();
            for (int i = 0; i < res.size(); i++) {
                JsonObject o = res.get(i).asObject();
                Address addr = new Address();
                addr.address = o.get("formatted_address").asString();
                try {
                    addr.name = o.get("name").asString();
                } catch (Exception ex) {
                    // ignore
                }
                JsonObject geo = o.get("geometry").asObject().get("location").asObject();
                addr.lat = geo.get("lat").asDouble();
                addr.lon = geo.get("lng").asDouble();
                r.add(addr);
            }
            result(r);
        }

        void error(String error) {
            showError(error);
        }

    }

    static public abstract class LocationRequest extends PlaceRequest {

        void exec(String addr) {
            String url = "http://maps.googleapis.com/maps/api/geocode/json?address=$1&sensor=true";
            Location location = getLocation();
            if (location != null) {
                double lat = location.getLatitude();
                double lon = location.getLongitude();
                url += "&bounds=" + (lat - 1.5) + "," + (lon - 1.5) + Uri.encode("|") + (lat + 1.5) + "," + (lon + 1.5);
            }
            url += "&language=" + Locale.getDefault().getLanguage();
            execute(url, addr);
        }

        void result(String data) throws ParseException {
            JsonArray res = Json.parse(data).asObject().get("results").asArray();
            Vector<Address> r = new Vector<Address>();
            for (int i = 0; i < res.size(); i++) {
                JsonObject o = res.get(i).asObject();
                Address addr = new Address();
                addr.address = o.get("formatted_address").asString();
                JsonObject geo = o.get("geometry").asObject().get("location").asObject();
                addr.lat = geo.get("lat").asDouble();
                addr.lon = geo.get("lng").asDouble();
                r.add(addr);
            }
            result(r);
        }

    }

}
