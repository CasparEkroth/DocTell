package com.doctell.app.model;

import androidx.annotation.NonNull;

public enum LANG {
    EN,SV,ES;

    @NonNull
    @Override
    public String toString(){
        return this.toString().toLowerCase();
    }
}
