package com.doctell.app.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;

import com.doctell.app.R;
import com.doctell.app.ReaderActivity;
import com.doctell.app.model.entity.Book;
import com.doctell.app.model.repository.BookStorage;
import com.doctell.app.model.pdf.PdfPreviewHelper;

import java.io.File;

@SuppressLint("ViewConstructor")
public class ItemView extends LinearLayout {

    private ImageView imageView;
    private TextView titleView;

    public ItemView(Context context, AttributeSet attrs, Book book) {
        super(context, attrs);
        inflate(context, R.layout.item_view, this);

        imageView = findViewById(R.id.pdfPreviewImage);
        titleView = findViewById(R.id.title);

        loadThumbnailAsync(book.getThumbnailPath());

        titleView.setText(book.getTitle() != null ? book.getTitle() : "Unknown");

        CardView cardView = findViewById(R.id.cardContainer);
        cardView.setOnClickListener(v -> openPdf(book));
        cardView.setOnLongClickListener(v -> {
            showDocumentOptionsDialog(getContext(), book);
            return true;
        });

    }

    private void loadThumbnailAsync(String thumbnailPath) {
        new Thread(() -> {
            Bitmap bmp = PdfPreviewHelper.loadThumbBitmap(thumbnailPath);
            post(() -> {
                if (bmp != null) {
                    imageView.setImageBitmap(bmp);
                } else {
                    imageView.setImageResource(android.R.drawable.ic_menu_report_image);
                }
            });
        }).start();
    }
    // ---------- New dialog helpers ----------

    private void showDocumentOptionsDialog(Context ctx, Book book) {
        LayoutInflater inflater = LayoutInflater.from(ctx);
        View content = inflater.inflate(R.layout.activity_pop_upp, null, false);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setView(content)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT)
            );
        }
        content.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());

        content.findViewById(R.id.btnOpen).setOnClickListener(v -> {
            openPdf(book);
            dialog.dismiss();
        });

        content.findViewById(R.id.btnDelete).setOnClickListener(v -> {
            boolean removed = BookStorage.delete(ctx, book);
            dialog.dismiss();
            if (removed) {
                ViewGroup parent = (ViewGroup) getParent();
                if (parent != null) parent.removeView(this);
            } else {
                Log.w("ItemView", "Delete failed: book not found in cache");
            }
        });

        content.findViewById(R.id.btnRename).setOnClickListener(v -> {
            dialog.dismiss();
            showRenameDialog(ctx, book);
        });

        dialog.show();
    }

    private void showRenameDialog(Context ctx, Book book) {
        LayoutInflater inflater = LayoutInflater.from(ctx);
        View editContent = inflater.inflate(R.layout.activity_rename, null, false);

        AlertDialog renameDialog = new AlertDialog.Builder(ctx)
                .setView(editContent)
                .create();

        if (renameDialog.getWindow() != null) {
            renameDialog.getWindow().setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT)
            );
        }

        EditText etName = editContent.findViewById(R.id.etName);
        Button btnCancel = editContent.findViewById(R.id.btnCancel);
        Button btnSave = editContent.findViewById(R.id.btnSave);

        String cur = book.getTitle() != null ? book.getTitle() : "";
        etName.setText(cur);
        etName.setSelection(cur.length());

        btnCancel.setOnClickListener(v -> renameDialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String newTitle = etName.getText().toString().trim();
            if (newTitle.isEmpty()) {
                etName.setError(ctx.getString(R.string.rename_error_empty_title));
                return;
            }
            book.setTitle(newTitle);
            BookStorage.updateBook(book, getContext());
            setTitle(newTitle);

            renameDialog.dismiss();
        });

        renameDialog.show();
    }

    // ---------- Existing helpers ----------

    private void openPdf(Book book) {
        if (book.getLocalPath() != null && new File(book.getLocalPath()).exists()) {
            Intent i = new Intent(getContext(), ReaderActivity.class);
            i.putExtra("uri", book.getUri().toString());
            if (!(getContext() instanceof android.app.Activity)) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            getContext().startActivity(i);
            return;
        }
        try {
            if (getContext().getContentResolver().openInputStream(book.getUri()) == null) {
                throw new java.io.FileNotFoundException("Stream is null");
            }
        } catch (Exception e) {
            Toast.makeText(
                    getContext(),
                    "File not found. It may have been moved or deleted.",
                    android.widget.Toast.LENGTH_LONG
            ).show();

            Log.e("ItemView", "Cannot open book: " + book.getUri(), e);return;
        }

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
