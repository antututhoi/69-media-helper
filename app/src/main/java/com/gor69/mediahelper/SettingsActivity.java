package com.gor69.mediahelper;

import android.os.Bundle;
import android.text.InputType;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

/** The one settings screen used by every 69 app. */
public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.settings, rootKey);
            for (String key : new String[]{Mod69Prefs.LOW_IMAGE_SIZE, Mod69Prefs.LOW_IMAGE_PERCENT,
                    Mod69Prefs.LOW_VIDEO_HEIGHT, Mod69Prefs.COVER_WIDTH}) {
                EditTextPreference p = findPreference(key);
                if (p != null) {
                    p.setOnBindEditTextListener(e -> e.setInputType(InputType.TYPE_CLASS_NUMBER));
                }
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            Preference status = findPreference("status_ffmpeg");
            if (status != null) {
                String s = getString(FFmpeg.isAvailable() ? R.string.status_ok : R.string.status_missing)
                        + "\n" + getString(R.string.processed_count, Stats.processed(requireContext()));
                status.setSummary(s);
            }
        }
    }
}
