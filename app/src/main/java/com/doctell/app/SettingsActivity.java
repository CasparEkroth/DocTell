package com.doctell.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.doctell.app.model.entity.Prefs;
import com.doctell.app.model.analytics.DocTellAnalytics;
import com.doctell.app.model.analytics.DocTellCrashlytics;
import com.doctell.app.model.voice.CloudTtsEngine;
import com.doctell.app.model.voice.TtsEngineStrategy;
import com.doctell.app.model.voice.notPublic.TtsEngineProvider;
import com.doctell.app.model.voice.notPublic.TtsEngineType;

public class SettingsActivity extends AppCompatActivity {

    private Spinner spLang, spVoice;
    private SeekBar seekRate;
    private TextView txtRateValue;
    private Switch swAnalytics, swCrashlytics;
    private int initIndex = 0;
    private int initVoice = 0;
    private Context app;

    @SuppressLint("DefaultLocale")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        app = this;
        spLang = findViewById(R.id.spLang);
        spVoice = findViewById(R.id.spVoice);
        seekRate = findViewById(R.id.seekRate);
        txtRateValue = findViewById(R.id.txtRateValue);
        swAnalytics   = findViewById(R.id.swAnalytics);
        swCrashlytics = findViewById(R.id.swCrashlytics);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.pref_lang_entries, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLang.setAdapter(adapter);

        ArrayAdapter<CharSequence> adapterV = ArrayAdapter.createFromResource(
                this, R.array.pref_voice, android.R.layout.simple_spinner_item);
        adapterV.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spVoice.setAdapter(adapterV);

        String[] voices ={TtsEngineType.LOCAL.toString(),TtsEngineType.CLOUD.toString()};
        TtsEngineType current = getEnginType();
        setSpVoiceText(voices,current.toString());

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

        spVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(position == initVoice)return;
                setSpVoiceText(voices,voices[position]);
                TtsEngineProvider.saveEngineType(convert(voices[position]),app);
                initVoice = position;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        SharedPreferences prefs =
                getSharedPreferences(Prefs.DOCTELL_PREFS.toString(), MODE_PRIVATE);

        float savedRate = prefs.getFloat(Prefs.TTS_SPEED.toString(), 1.0f);
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

        boolean analyticsEnabled   = prefs.getBoolean(Prefs.ANALYTICS_ENABLED.toString(), true);
        boolean crashlyticsEnabled = prefs.getBoolean(Prefs.CRASHLYTICS_ENABLED.toString(), true);
        swAnalytics.setChecked(analyticsEnabled);
        swCrashlytics.setChecked(crashlyticsEnabled);

        swAnalytics.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(Prefs.ANALYTICS_ENABLED.toString(), isChecked)
                        .apply();
                DocTellAnalytics.setEnable(getApplicationContext(),isChecked);
            }
        });

        swCrashlytics.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(Prefs.CRASHLYTICS_ENABLED.toString(), isChecked)
                        .apply();
                DocTellCrashlytics.setEnabled(isChecked);
            }
        });
    }

    private void setSpLangText(String[] values, String value){
        for (int i = 0; i < values.length; i++) {
            if(values[i].equals(value)){
                initIndex = i;
                spLang.setSelection(i);
                break;
            }

        }
    }

    private void setSpVoiceText(String[] values, String value){
        for (int i = 0; i < values.length; i++) {
            if(values[i].equals(value)){
                initVoice = i;
                spVoice.setSelection(i);
                break;
            }

        }
    }

    private TtsEngineType convert(String vstr){
        if(TtsEngineType.CLOUD.toString().equals(vstr))return TtsEngineType.CLOUD;
        return TtsEngineType.LOCAL;
    }

    private void onLanguageSelected(String langCode) {
        getEngine().setLanguageByCode(langCode);
    }

    private TtsEngineType getEnginType(){
        TtsEngineStrategy engine = getEngine();
        if(engine instanceof CloudTtsEngine)return TtsEngineType.CLOUD;
        return TtsEngineType.LOCAL;
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