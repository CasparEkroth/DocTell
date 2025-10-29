package com.doctell.app.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.doctell.app.MainActivity;
import com.doctell.app.R;
import com.doctell.app.ReaderActivity;
import com.doctell.app.model.Book;
import com.doctell.app.model.BookStorage;
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

        setClickable(true);
        setLongClickable(true);
        setFocusable(true);

        setBackgroundResource(android.R.drawable.list_selector_background);

        if (book.getBitmap() != null) {
            imageView.setImageBitmap(book.getBitmap());
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_report_image);
        }

        titleView.setText(book.getTitle() != null ? book.getTitle() : "Unknown");

        OnClickListener openReader = v -> {
            Log.d("ItemView", "onClick -> opening ReaderActivity");
            openPdf(book);
        };

        @SuppressLint("ClickableViewAccessibility")
        OnLongClickListener openPopUpp = v -> {
            Context ctx = v.getContext();
            LayoutInflater inflater = LayoutInflater.from(ctx);
            View content = inflater.inflate(R.layout.activity_pop_upp, null, false);

            PopupWindow popup = new PopupWindow(
                    content,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
            );
            popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            popup.setOutsideTouchable(true);

            popup.setTouchInterceptor((view, event) ->{
                if(event.getAction() == MotionEvent.ACTION_OUTSIDE){
                    popup.dismiss();
                    return true;
                }
                return false;
            });
            android.util.Log.d("ItemView", "Long press -> showing popup");

            content.findViewById(R.id.btnClose).setOnClickListener(b -> popup.dismiss());

            content.findViewById(R.id.btnOpen).setOnClickListener(b -> openPdf(book));

            content.findViewById(R.id.btnDelete).setOnClickListener(b -> {
                boolean removed = BookStorage.delete(ctx,book);
                popup.dismiss();
                if(removed){
                    ViewGroup parent = (ViewGroup) getParent();
                    if(parent != null) parent.removeView(this);
                }else {
                    Log.w("ItemView", "Delete failed: book not found in cache");
                }
            });

            content.findViewById(R.id.btnRename).setOnClickListener( b ->{
                // to comme
            });

            popup.showAtLocation(v, Gravity.CENTER, 0, 0);
            return true;
        };


        setOnLongClickListener(openPopUpp);
        imageView.setOnLongClickListener(openPopUpp);
        setOnClickListener(openReader);
        imageView.setOnClickListener(openReader);
    }


    private void openPdf(Book book){
        Intent i = new Intent(getContext(), ReaderActivity.class);
        i.putExtra("uri", book.getUri().toString());

        if (!(getContext() instanceof android.app.Activity)) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        getContext().startActivity(i);
    }
    public void setTitle(String text) {
        titleView.setText(text);
    }

    public void setImageView(Context context, Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
    }
}
