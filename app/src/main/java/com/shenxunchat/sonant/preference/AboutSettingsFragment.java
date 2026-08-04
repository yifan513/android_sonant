package com.shenxunchat.sonant.preference;

import static java.util.Objects.requireNonNull;

import android.os.Bundle;

import androidx.preference.Preference;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import com.shenxunchat.sonant.BuildConfig;
import com.shenxunchat.sonant.R;

public class AboutSettingsFragment extends MumlaPreferenceFragment {
    private static final String VERSION_KEY = "version";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_about, rootKey);

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        String summary = String.format("%s\nBuilt %s UTC",
                BuildConfig.VERSION_NAME, df.format(new Date(BuildConfig.TIMESTAMP)));
        Preference versionPreference = getPreferenceScreen().findPreference(VERSION_KEY);
        requireNonNull(versionPreference).setSummary(summary);
    }
}
