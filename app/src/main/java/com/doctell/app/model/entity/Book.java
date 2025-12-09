package com.doctell.app.model.entity;
import android.net.Uri;

public class Book {
    private Uri uri;
    private String title;
    private int lastPage;
    private int sentence;
    private String localPath;
    private String thumbnailPath;

    public Book(Uri uri, String title,int lastPage,int sentence, String thumbnailPath, String localPath){
        if(uri == null) throw new IllegalArgumentException("uri can´t be null ");
        this.uri = uri;
        this.title = title;
        this.sentence = sentence;
        this.lastPage = lastPage;
        this.thumbnailPath = thumbnailPath;
        this.localPath = localPath;
    }

    public Uri getUri() {return uri;}

    public void setUri(Uri uri) {this.uri = uri;}

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String p) { this.localPath = p; }

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

    public int incrementPage(){
        lastPage++;
        return lastPage;
    }
    public int decrementPage(){
        if (lastPage > 0) {
            lastPage--;
        }
        return lastPage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Book)) return false;
        Book other = (Book) o;
        return this.getUri() != null && this.getUri().equals(other.getUri());
    }

    @Override
    public int hashCode() {
        return getUri() != null ? getUri().hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Book{" +
                "uri=" + uri +
                ", title='" + title + '\'' +
                ", lastPage=" + lastPage +
                ", sentence=" + sentence +
                ", localPath='" + localPath + '\'' +
                ", thumbnailPath='" + thumbnailPath + '\'' +
                '}';
    }
}
