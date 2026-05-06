package com.zhijing.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.zhijing.R;
import com.zhijing.graph.entity.UserContent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.ViewHolder> {

    private final List<UserContent> items;
    private final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public ContentAdapter(List<UserContent> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_content, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserContent content = items.get(position);
        String preview = content.content.length() > 80
                ? content.content.substring(0, 80) + "..."
                : content.content;
        holder.tvContent.setText(preview);
        holder.tvSource.setText(content.sourceApp);
        holder.tvTime.setText(sdf.format(new Date(content.createdAt)));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent, tvSource, tvTime;

        ViewHolder(View view) {
            super(view);
            tvContent = view.findViewById(R.id.tvContent);
            tvSource = view.findViewById(R.id.tvSource);
            tvTime = view.findViewById(R.id.tvTime);
        }
    }
}
