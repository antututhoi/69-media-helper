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
        setContentView(R.layout.activity_settings);
        final android.view.View root = findViewById(R.id.root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets b = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            v.setPadding(b.left, b.top, b.right, b.bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
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
            Preference picker = findPreference("mod69_file_picker");
            if (picker != null) {
                picker.setSummary(Mod69Picker.choiceName(requireContext()));
                picker.setOnPreferenceClickListener(x -> {
                    Mod69Picker.showSettingDialog(requireContext(),
                            () -> picker.setSummary(Mod69Picker.choiceName(requireContext())));
                    return true;
                });
            }
            Preference status = findPreference("status_ffmpeg");
            if (status != null) {
                boolean ok = FFmpeg.isAvailable();
                String s = getString(ok ? R.string.status_ok : R.string.status_missing)
                        + (ok ? " " + FFmpeg.version : "\n" + FFmpeg.problem)
                        + "\n" + getString(R.string.processed_count, Stats.processed(requireContext()));
                status.setSummary(s);
            }
            Preference jobs = findPreference("recent_jobs");
            if (jobs != null) {
                String j = Stats.jobs(requireContext());
                jobs.setSummary(j.isEmpty() ? getString(R.string.recent_jobs_none) : j);
            }
        }
    }
}
