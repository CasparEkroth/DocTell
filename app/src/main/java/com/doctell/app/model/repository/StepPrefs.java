package com.doctell.app.model.repository;

import android.content.Context;

import com.doctell.app.model.entity.StepLength;
import com.doctell.app.model.entity.Prefs;

public final class StepPrefs {
    private static final String KEY_STEP = Prefs.STEP_LENGTH.toString();

    public static void setStepLength(Context ctx, StepLength step) {
        ctx.getSharedPreferences(Prefs.DOCTELL_PREFS.toString(), Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STEP, step.name())
                .apply();
    }

    public static StepLength getStepLength(Context ctx) {
        String v = ctx.getSharedPreferences(Prefs.DOCTELL_PREFS.toString(), Context.MODE_PRIVATE)
                .getString(KEY_STEP, StepLength.PAGE.name());
        try { return StepLength.valueOf(v); } catch (Exception e) { return StepLength.PAGE; }
    }
}