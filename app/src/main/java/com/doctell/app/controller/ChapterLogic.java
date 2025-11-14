package com.doctell.app.controller;

import com.doctell.app.model.ChapterItem;

import java.util.ArrayList;
import java.util.List;

public class ChapterLogic {

    private static ChapterLogic CL;
    private List<ChapterItem> list;

    public static ChapterLogic getInstance(){
        if(CL == null) CL = new ChapterLogic();
        return CL;
    }

    private ChapterLogic(){
        list = new ArrayList<>();
    }

    public void refreshChapters(){
        //lode ChapterItem using PdfDocument.Bookmark
    }

}
