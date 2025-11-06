package com.doctell.app.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.doctell.app.R;
import com.doctell.app.ReaderActivity;
import com.doctell.app.model.Book;
import com.doctell.app.model.BookStorage;
import com.doctell.app.model.PdfPreviewHelper;


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

        Bitmap bmp = PdfPreviewHelper.loadThumbBitmap(book.getThumbnailPath());

        if (bmp != null) {
            imageView.setImageBitmap(bmp);
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_report_image);
            // maybe add my own
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

            content.findViewById(R.id.btnOpen).setOnClickListener(b -> {
                openPdf(book);
                popup.dismiss();
            });

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

            content.findViewById(R.id.btnRename).setOnClickListener(b -> {
                // close the actions popup
                popup.dismiss();
                View editContent = inflater.inflate(R.layout.activity_rename, null, false);
                final PopupWindow renamePopup = new PopupWindow(
                        editContent,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true
                );
                renamePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                renamePopup.setOutsideTouchable(true);

                // wire fields
                EditText etName = editContent.findViewById(R.id.etName);
                Button btnCancel = editContent.findViewById(R.id.btnCancel);
                Button btnSave = editContent.findViewById(R.id.btnSave);

                String cur = book.getTitle() != null ? book.getTitle() : "";
                etName.setText(cur);
                etName.setSelection(cur.length());

                btnCancel.setOnClickListener(v2 -> renamePopup.dismiss());
                btnSave.setOnClickListener(v2 -> {
                    String newTitle = etName.getText().toString().trim();
                    if (newTitle.isEmpty()) {
                        etName.setError("Enter a title");
                        return;
                    }
                    book.setTitle(newTitle);
                    BookStorage.updateBook(book, getContext());
                    setTitle(newTitle);

                    renamePopup.dismiss();
                });

                renamePopup.showAtLocation(this, Gravity.CENTER, 0, 0);


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
