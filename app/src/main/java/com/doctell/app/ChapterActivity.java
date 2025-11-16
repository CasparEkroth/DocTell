package com.doctell.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.doctell.app.model.ChapterItem;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class ChapterActivity extends AppCompatActivity {

    private List<ChapterItem> list;

    private MaterialCardView cardView;
    private ImageButton closeChap;
    private RecyclerView viewHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chapter_list);

        ArrayList<String> titles = getIntent().getStringArrayListExtra("chapterTitles");
        ArrayList<Integer> pages   = getIntent().getIntegerArrayListExtra("chapterPages");
        ArrayList<Integer> levels  = getIntent().getIntegerArrayListExtra("chapterLevels");
        //int currentPage            = getIntent().getIntExtra("currentPage", 0);


        if (titles == null || pages == null || levels == null) {
            // defensive guard to avoid crash
            finish();
            return;
        }

        List<ChapterItem> chapterItems = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            int pageIndex = i < pages.size() ? pages.get(i) : 0;
            int level     = i < levels.size() ? levels.get(i) : 0;
            chapterItems.add(new ChapterItem(
                    titles.get(i),
                    pageIndex,
                    level
            ));
        }

        list = new ArrayList<>();
        closeChap = findViewById(R.id.btnCloseChapters);
        cardView = findViewById(R.id.cardChapters);
        viewHolder = findViewById(R.id.rvChapters);

        closeChap.setOnClickListener(v -> onClose());

        for(ChapterItem c : chapterItems){
            Log.d("TEST0",c.toString());
        }

    }

    private void onChapterClicked(ChapterItem item) {
        Intent result = new Intent();
        result.putExtra("selectedPage", item.getPageIndex());
        setResult(RESULT_OK, result);
        finish();
    }

    private void onClose(){

        finish();
    }

    public void refreshChapters(){// cal when opening a book

        //lode ChapterItem using PdfDocument.Bookmark
    }

    private void refreshViewHolder(){
        //list
        //viewHolder
    }

}
