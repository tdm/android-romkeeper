package tdm.romkeeper;

import android.preference.PreferenceActivity;

import android.os.Bundle;

public class RomKeeperPreferenceActivity extends PreferenceActivity
{
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }
}
