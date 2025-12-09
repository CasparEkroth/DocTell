package com.doctell.app.model.entity;

public class ChapterItem {
    private final String title;
    private final int pageIndex;
    private final int level;

    public ChapterItem(String title, int pageIndex, int level) {
        this.title = title;
        this.pageIndex = pageIndex;
        this.level = level;
    }

    public String getTitle() {
        return title;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public String toString(){
        return String.format(title + " " + pageIndex + " " + level);
    }
}

