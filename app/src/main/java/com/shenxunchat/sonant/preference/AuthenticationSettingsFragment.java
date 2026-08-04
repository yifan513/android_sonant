package com.shenxunchat.sonant.preference;

import android.os.Bundle;
import com.shenxunchat.sonant.R;

public class AuthenticationSettingsFragment extends MumlaPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_authentication, rootKey);
    }
}
