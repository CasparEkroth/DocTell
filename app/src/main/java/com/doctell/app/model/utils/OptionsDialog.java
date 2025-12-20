package com.doctell.app.model.utils;

import static com.doctell.app.ReaderActivity.REQ_SELECT_CHAPTER;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.doctell.app.ChapterActivity;
import com.doctell.app.R;
import com.doctell.app.model.entity.ChapterItem;

import java.util.ArrayList;
import java.util.List;

public class OptionsDialog {
    private OptionsDialog(){}

    public static void openOptionsDialog(Activity activity, List<ChapterItem> chapters){
        LayoutInflater inflater = LayoutInflater.from(activity);
        View content = inflater.inflate(R.layout.dialog_options, null, false);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(content)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT)
            );
        }

        content.findViewById(R.id.btnChapters).setOnClickListener(v ->{
            dialog.dismiss();
            openChapterActivity(activity,chapters);
        });

        content.findViewById(R.id.btnExit).setOnClickListener(v -> {
            //set step
            dialog.dismiss();
        });

        content.findViewById(R.id.btnStepPage).setOnClickListener(v -> {
            dialog.dismiss();
        });

        content.findViewById(R.id.btnStepSentences).setOnClickListener(v -> {
            //set step
            dialog.dismiss();
        });

        dialog.show();
    }


    private static void openChapterActivity(Activity activity, List<ChapterItem> chapters){
        if(chapters == null){
            Toast.makeText(activity, "No chapters found.", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<Integer> pages = new ArrayList<>();
        ArrayList<Integer> levels = new ArrayList<>();

        for (ChapterItem c : chapters) {
            titles.add(c.getTitle());
            pages.add(c.getPageIndex());
            levels.add(c.getLevel());
        }
        Intent intent = new Intent(activity, ChapterActivity.class);
        intent.putStringArrayListExtra("chapterTitles", titles);
        intent.putIntegerArrayListExtra("chapterPages", pages);
        intent.putIntegerArrayListExtra("chapterLevels", levels);
        //intent.putExtra("currentPage", currentPageIndex); // highlight

        activity.startActivityForResult(intent, REQ_SELECT_CHAPTER);
    }
}
