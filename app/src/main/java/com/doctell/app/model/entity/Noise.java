package com.doctell.app.model.entity;

public enum Noise {
    OCEAN_OF_PDF;

    @Override
    public String toString(){
        String s = "";
        switch (this){
            case OCEAN_OF_PDF:s = "OceanofPDF.com"; break;
        }
        return s;
    }

    public static boolean isNoise(String str){
        for(Noise noise : Noise.values()){
            if(str.equals(noise.toString()))return true;
        }
        return false;
    }
}
