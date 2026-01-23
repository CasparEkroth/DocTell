package com.doctell.app.view;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.doctell.app.R;
import com.doctell.app.model.entity.ChapterItem;

import java.util.List;

public class ChapterAdapter extends RecyclerView.Adapter<ChapterAdapter.ChapterViewHolder> {

    private final List<ChapterItem> items;
    private final OnItemClickListener listener;
    private int selectedIndex = -1;

    @SuppressLint("NotifyDataSetChanged")//refresh
    public void setSelectedIndex(int targetIndex) {
        selectedIndex = targetIndex;
        notifyDataSetChanged();
    }

    public interface OnItemClickListener{
        void onItemClick(ChapterItem item, int pos);
    }

    public ChapterAdapter(List<ChapterItem> items, OnItemClickListener listener){
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChapterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chapter, parent, false);
        return new ChapterViewHolder(view);
    }

    @SuppressLint("ResourceAsColor")
    @Override
    public void onBindViewHolder(@NonNull ChapterViewHolder holder, int position) {
        ChapterItem item = items.get(position);
        holder.bind(item,listener);
        if (position == selectedIndex) {
            int color = holder.itemView.getContext().getColor(R.color.dt_surface);
            holder.itemView.setBackgroundColor(color);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ChapterViewHolder extends RecyclerView.ViewHolder{
        private TextView textTitle,pageIndicator;

        public ChapterViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.textTitle);
            pageIndicator = itemView.findViewById(R.id.pageIndicator);
        }

        public void bind(final ChapterItem item, final OnItemClickListener listener){
            textTitle.setText(item.getTitle());
            pageIndicator.setText(String.valueOf(item.getPageIndex()));
            itemView.setOnClickListener(v ->
                listener.onItemClick(item, getAdapterPosition())
            );
        }
    }
}
