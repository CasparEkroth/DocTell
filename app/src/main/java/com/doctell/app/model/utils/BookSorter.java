package com.doctell.app.model.utils;

import com.doctell.app.model.entity.Book;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BookSorter {
    private static final Collator SV_COLLATOR =
            Collator.getInstance(new Locale("sv", "SE"));
    private BookSorter() {}

    public static void sortByTitleAsc(List<Book> books) {
        books.sort((b1, b2) -> {
            String t1 = safeTitle(b1);
            String t2 = safeTitle(b2);
            return SV_COLLATOR.compare(t1, t2);
        });
    }

    public static void sortByTitleDesc(List<Book> books) {
        books.sort((b1, b2) -> {
            String t1 = safeTitle(b1);
            String t2 = safeTitle(b2);
            return SV_COLLATOR.compare(t2, t1);
        });
    }

    public static void sortByDateNewest(List<Book> books) {
        books.sort((b1, b2) ->
                Long.compare(b2.getLastOpenedAt(), b1.getLastOpenedAt()));
    }

    public static void sortByDateOldest(List<Book> books) {
        books.sort(Comparator.comparingLong(Book::getLastOpenedAt));
    }

    private static String safeTitle(Book b) {
        return b.getTitle() != null ? b.getTitle() : "";
    }
}
