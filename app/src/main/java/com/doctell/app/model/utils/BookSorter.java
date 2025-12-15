package com.doctell.app.model.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.doctell.app.model.entity.Prefs;
import com.doctell.app.model.entity.Book;
import com.doctell.app.model.entity.SortOn;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BookSorter {
    private static final Collator SV_COLLATOR =
            Collator.getInstance(new Locale("sv", "SE"));
    private static SortOn sort = SortOn.NOT_DEFINED;
    private BookSorter() {}

    public static void sortBooks(int selectedSortIndex, List<Book> books){
        SortOn s = SortOn.fromValue(selectedSortIndex);
        switch (selectedSortIndex) {
            case 0: BookSorter.sortByTitleAsc(books); break;
            case 1: BookSorter.sortByTitleDesc(books); break;
            case 2: BookSorter.sortByDateNewest(books); break;
            case 3: BookSorter.sortByDateOldest(books); break;
        }
        sort = SortOn.fromValue(selectedSortIndex);
    }

    public static void sortBooksOnDefault(List<Book> books){
        if(sort == SortOn.NOT_DEFINED)return;
        sortBooks(sort.getValue(), books);
    }
    private static void sortByTitleAsc(List<Book> books) {
        ArrayList<Book> mutableBooks = new ArrayList<>(books);
        mutableBooks.sort((b1, b2) -> {
            String t1 = safeTitle(b1);
            String t2 = safeTitle(b2);
            return SV_COLLATOR.compare(t1, t2);
        });
        books.clear();
        books.addAll(mutableBooks);
    }

    private static void sortByTitleDesc(List<Book> books) {
        ArrayList<Book> mutableBooks = new ArrayList<>(books);
        mutableBooks.sort((b1, b2) -> {
            String t1 = safeTitle(b1);
            String t2 = safeTitle(b2);
            return SV_COLLATOR.compare(t2, t1);
        });
        books.clear();
        books.addAll(mutableBooks);
    }

    private static void sortByDateNewest(List<Book> books) {
        ArrayList<Book> mutableBooks = new ArrayList<>(books);
        mutableBooks.sort((b1, b2) ->
                Long.compare(b2.getLastOpenedAt(), b1.getLastOpenedAt()));
        books.clear();
        books.addAll(mutableBooks);
    }

    private static void sortByDateOldest(List<Book> books) {
        ArrayList<Book> mutableBooks = new ArrayList<>(books);
        mutableBooks.sort(Comparator.comparingLong(Book::getLastOpenedAt));
        books.clear();
        books.addAll(mutableBooks);
    }


    private static String safeTitle(Book b) {
        return b.getTitle() != null ? b.getTitle() : "";
    }

    public static void getSavedSort(Context ctx){
        SharedPreferences pref = ctx.getSharedPreferences(Prefs.DOCTELL_PREFS.toString(), Context.MODE_PRIVATE);
        sort = SortOn.fromValue(pref.getInt(Prefs.SORT_INDEX.toString(), -1));
    }

    public static void saveSortIndex(Context ctx){
        SharedPreferences pref = ctx.getSharedPreferences(Prefs.DOCTELL_PREFS.toString(), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        editor.putInt(Prefs.SORT_INDEX.toString(), sort.getValue());
    }

    public static int getIndex(){
        return sort.getValue();
    }
}
