package com.doctell.app.model;

import androidx.annotation.NonNull;
import java.util.Locale;

public enum LANG {
    EN,SV,ES;

    @NonNull
    @Override
    public String toString(){
        return this.toString().toLowerCase(Locale.ROOT);
    }
}
