package com.doctell.app.model.entity;

public enum SortOn {
    NOT_DEFINED(-1),
    TITLE_ASC(0),
    TITLE_DESC(1),
    DATE_NEWEST(2),
    DATE_OLDEST(3);

    private final int value;

    SortOn(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static SortOn fromValue(int value) {
        for (SortOn s : values()) {
            if (s.value == value) return s;
        }
        return NOT_DEFINED;
    }

}
