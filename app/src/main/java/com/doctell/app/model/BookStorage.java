package com.doctell.app.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class BookStorage {
    private static final String PREFS_NAME = "MyAppPrefs";
    private static final String KEY_BOOK_LIST = "book_list";

    public static void saveBooks(Context context, List<Book> list){
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        Gson gson = new Gson();
        String json = gson.toJson(list);
        editor.putString(KEY_BOOK_LIST,json);
        editor.apply();
    }

    public static List<Book> loadBooks(Context context){
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = prefs.getString(KEY_BOOK_LIST,null);
        Type type = new TypeToken<ArrayList<Book>>() {}.getType();
        List<Book> books = gson.fromJson(json, type);

        if (books == null) {
            books = new ArrayList<>();
        }
        return books;
    }
}
