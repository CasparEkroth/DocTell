package com.doctell.app.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.doctell.app.R;
import com.doctell.app.model.Book;
import com.doctell.app.model.PdfPreviewHelper;

import java.io.IOException;

@SuppressLint("ViewConstructor")
public class ItemView extends LinearLayout {

    private ImageView icon;
    private TextView title;

    public ItemView(Context context, AttributeSet attrs, Book book) {
        super(context, attrs);
        init(context, book);
    }

    private void init(Context context, Book book){
        inflate(context, R.layout.item_view, this);
        icon = findViewById(R.id.pdfPreviewImage);
        title = findViewById(R.id.title);
        setTitle(book.getTitle());
        setPdfPreview(context, book.getBitmap());
    }

    public void setTitle(String text) {
        title.setText(text);
    }

    public void setPdfPreview(Context context, Bitmap bitmap) {
        icon.setImageBitmap(bitmap);
    }
}
