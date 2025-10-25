package com.doctell.app.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.doctell.app.R;
import com.doctell.app.model.Book;
import com.doctell.app.model.PdfPreviewHelper;

import java.io.IOException;

@SuppressLint("ViewConstructor")
public class ItemView extends LinearLayout {

    private ImageView imageView;
    private TextView titleView;

    public ItemView(Context context, AttributeSet attrs, Book book) {
        super(context, attrs);
        inflate(context, R.layout.item_view, this);

        imageView = findViewById(R.id.pdfPreviewImage);
        titleView = findViewById(R.id.title);

        if (book.getBitmap() != null) {
            imageView.setImageBitmap(book.getBitmap());
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_report_image);
        }

        titleView.setText(book.getTitle() != null ? book.getTitle() : "Unknown");
        setOnClickListener(v -> Log.d("ItemView", "Clicked: " + book.getUri()));
    }

    public void setTitle(String text) {
        titleView.setText(text);
    }

    public void setImageView(Context context, Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
    }
}
