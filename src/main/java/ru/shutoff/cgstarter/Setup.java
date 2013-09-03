package ru.shutoff.cgstarter;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class Setup extends PreferenceActivity {

    SeekBarPreference autoPref;
    SeekBarPreference launchPref;
    SeekBarPreference levelPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.setup);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        autoPref = (SeekBarPreference) findPreference("auto_pause");
        autoPref.setMin(3);
        autoPref.setSummary(prefs.getInt("auto_pause", 5) + " " + getString(R.string.sec));
        autoPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                autoPref.setSummary(String.valueOf(newValue) + " " + getString(R.string.sec));
                return true;
            }
        });

        launchPref = (SeekBarPreference) findPreference("launch_pause");
        launchPref.setMin(10);
        launchPref.setSummary(prefs.getInt("launch_pause", 30) + " " + getString(R.string.sec));
        launchPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                launchPref.setSummary(String.valueOf(newValue) + " " + getString(R.string.sec));
                return true;
            }
        });

        Preference aboutPref = (Preference) findPreference("about");
        try {
            PackageManager pkgManager = getPackageManager();
            PackageInfo info = pkgManager.getPackageInfo("ru.shutoff.cgstarter", 0);
            aboutPref.setSummary(aboutPref.getSummary() + " " + info.versionName);
        } catch (Exception ex) {
            aboutPref.setSummary("");
        }
        aboutPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getBaseContext(), About.class);
                startActivity(intent);
                return true;
            }
        });

        CheckBoxPreference btPref = (CheckBoxPreference) findPreference("bt");
        if (BluetoothAdapter.getDefaultAdapter() == null)
            btPref.setEnabled(false);

        levelPref = (SeekBarPreference) findPreference("level");
        levelPref.setEnabled(prefs.getBoolean("volume", false));

        CheckBoxPreference volumePref = (CheckBoxPreference) findPreference("volume");
        volumePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean){
                    levelPref.setEnabled((Boolean)newValue);
                    return true;
                }
                return false;
            }
        });

    }
}