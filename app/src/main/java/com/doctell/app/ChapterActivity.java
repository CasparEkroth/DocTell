package com.doctell.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.doctell.app.model.entity.ChapterItem;
import com.doctell.app.view.ChapterAdapter;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class ChapterActivity extends AppCompatActivity {


    private MaterialCardView cardView;
    private ImageButton closeChap;
    private RecyclerView viewHolder;
    private ChapterAdapter adapter;
    private List<ChapterItem> chapterItems;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chapter_list);

        ArrayList<String> titles = getIntent().getStringArrayListExtra("chapterTitles");
        ArrayList<Integer> pages   = getIntent().getIntegerArrayListExtra("chapterPages");
        ArrayList<Integer> levels  = getIntent().getIntegerArrayListExtra("chapterLevels");
        //int currentPage            = getIntent().getIntExtra("currentPage", 0);


        if (titles == null || pages == null || levels == null) {
            // defensive guard to avoid crash
            finish();
            return;
        }

        chapterItems = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            int pageIndex = i < pages.size() ? pages.get(i) : 0;
            int level     = i < levels.size() ? levels.get(i) : 0;
            chapterItems.add(new ChapterItem(
                    titles.get(i),
                    pageIndex,
                    level
            ));
        }

        closeChap = findViewById(R.id.btnCloseChapters);
        cardView = findViewById(R.id.cardChapters);
        closeChap.setOnClickListener(v -> onClose());

        viewHolder = findViewById(R.id.rvChapters);
        viewHolder.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ChapterAdapter(chapterItems, ((item, pos) -> {
            //Toast.makeText(this, "Clicked: " + item.getTitle(), Toast.LENGTH_SHORT).show();
            onChapterClicked(item);
        }));

        viewHolder.setAdapter(adapter);


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


}
