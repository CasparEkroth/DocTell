package com.doctell.app.model;

import android.graphics.Bitmap;
import android.net.Uri;

public class Book {
    private Uri uri;
    private Bitmap bitmap;
    private String title;
    private int lastPage;
    private int sentence;

    public Book(Uri uri, String title,int lastPage,int sentence, Bitmap bitmap){
        if(uri == null) throw new IllegalArgumentException("uri can´t be null ");
        this.uri = uri;
        this.title = title;
        this.sentence = sentence;
        this.lastPage = lastPage;
        this.bitmap = bitmap;
    }

    public Uri getUri() {return uri;}

    public void setUri(Uri uri) {this.uri = uri;}

    public Bitmap getBitmap(){return bitmap;}

    public void setBitmap(Bitmap bitmap){this.bitmap = bitmap;}

    public String getTitle() {return title;}

    public void setTitle(String title) {this.title = title;}

    public int getLastPage() { return lastPage; }
    public void setLastPage(int page) { this.lastPage = page; }


    public int getSentence() {
        return sentence;
    }

    public void setSentence(int sentence) {
        this.sentence = sentence;
    }

}
