package com.doctell.app;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.doctell.app.model.TTSModel;

public class SettingsActivity extends AppCompatActivity {

    private Spinner langAndVoice;
    private TTSModel ttsM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        EdgeToEdge.enable(this);



        langAndVoice = findViewById(R.id.spLang);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.pref_lang_entries,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        langAndVoice.setAdapter(adapter);

        langAndVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String displayName = parent.getItemAtPosition(position).toString();
                String[] values = getResources().getStringArray(R.array.pref_lang_values);
                String langCode = values[position];
                ttsM.setLanguageByCode(langCode);

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });


    }
}