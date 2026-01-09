package com.doctell.app.model.utils;

import android.content.Context;
import android.util.Log;

import com.doctell.app.model.entity.Book;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileUtils {
    private FileUtils(){}
    public static void cleanOrphanedFiles(Context ctx, List<Book> currentBooks) {
        Set<String> validPaths = new HashSet<>();
        for (Book b : currentBooks) {
            if (b.getLocalPath() != null) validPaths.add(b.getLocalPath());
            if (b.getThumbnailPath() != null) validPaths.add(b.getThumbnailPath());
        }
        File docsDir = new File(ctx.getFilesDir(), "docs");
        File thumbsDir = new File(ctx.getFilesDir(), "thumbs");

        deleteUnknownFiles(docsDir, validPaths);
        deleteUnknownFiles(thumbsDir, validPaths);
    }

    private static void deleteUnknownFiles(File directory, Set<String> validPaths) {
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                long now = System.currentTimeMillis();
                long gracePeriod = 5 * 60 * 1000; // 5 minutes in milliseconds

                for (File file : files) {
                    boolean isValid = validPaths.contains(file.getAbsolutePath());
                    boolean isBrandNew = (now - file.lastModified()) < gracePeriod;

                    if (!isValid && !isBrandNew) {
                        boolean deleted = file.delete();
                        if (deleted) {
                            Log.i("BookStorage", "Cleanup: Deleted orphaned file " + file.getName());
                        }
                    }
                }
            }
        }
    }

}
