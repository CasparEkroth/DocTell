package com.doctell.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.doctell.app.model.Prefs;
import com.doctell.app.model.voice.TtsEngineStrategy;
import com.doctell.app.model.voice.notPublic.TtsEngineProvider;

public class SettingsActivity extends AppCompatActivity {

    private Spinner spLang;
    private SeekBar seekRate;
    private TextView txtRateValue;

    private int initIndex = 0;

    @SuppressLint("DefaultLocale")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        spLang = findViewById(R.id.spLang);
        seekRate = findViewById(R.id.seekRate);
        txtRateValue = findViewById(R.id.txtRateValue);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.pref_lang_entries, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLang.setAdapter(adapter);

        String[] values = getResources().getStringArray(R.array.pref_lang_values);
        String saved = getLanguage();
        setSpLangText(values,saved);

        spLang.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(position == initIndex)return;
                onLanguageSelected(values[position]);
                setSpLangText(values,values[position]);
                initIndex = position;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        float savedRate = getSharedPreferences(Prefs.DOCTELL_PREFS.toString(), MODE_PRIVATE).getFloat(Prefs.TTS_SPEED.toString(), 1.0f);
        txtRateValue.setText(String.format("%.1fx", savedRate));
        seekRate.setProgress(Math.round((savedRate - 0.5f) * 100));

        seekRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float r = 0.5f + (progress / 100f);
                txtRateValue.setText(String.format("%.1fx", r));
                if(fromUser) onRateChanged(r);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setSpLangText(String[] values, String value){
        for (int i = 0; i < values.length; i++) {
            //Log.d("TEST13", "index " + i + " " + values[i] +" comp " + value);
            if(values[i].equals(value)){
                initIndex = i;
                spLang.setSelection(i);
                break;
            }

        }
    }

    private void onLanguageSelected(String langCode) {
        getEngine().setLanguageByCode(langCode);
    }

    private String getLanguage(){
        return getEngine().getLanguage();
    }
    private void onRateChanged(float rate) {
        getEngine().setRate(rate);
    }

    private TtsEngineStrategy getEngine(){
        //return LocalTtsEngine.getInstance(getApplicationContext());
        return TtsEngineProvider.getEngine(getApplicationContext());
    }

}