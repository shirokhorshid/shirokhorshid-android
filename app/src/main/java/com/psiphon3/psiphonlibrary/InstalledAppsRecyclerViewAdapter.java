package com.psiphon3.psiphonlibrary;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.psiphon3.R;

import java.util.List;
import java.util.Set;

import io.reactivex.functions.Consumer;

public class InstalledAppsRecyclerViewAdapter extends RecyclerView.Adapter<InstalledAppsRecyclerViewAdapter.ViewHolder> {
    private final LayoutInflater inflater;
    private final List<AppEntry> data;
    private final Set<String> excludedApps;

    private ItemClickListener clickListener;

    InstalledAppsRecyclerViewAdapter(Context context, List<AppEntry> data, Set<String> excludedApps) {
        this.inflater = LayoutInflater.from(context);
        this.data = data;
        this.excludedApps = excludedApps;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.preference_widget_applist_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        final AppEntry appEntry = data.get(position);

        appEntry.getIconLoader()
                .doOnSuccess(new Consumer<Drawable>() {
                    @Override
                    public void accept(Drawable icon) {
                        // check to see if the adapter position matches the position of the holder
                        // if it does then set the picture
                        if (position == holder.getAdapterPosition()) {
                            holder.appIcon.setImageDrawable(icon);
                        }
                    }
                })
                .subscribe();
        holder.appName.setText(appEntry.getName());
        holder.isExcluded.setChecked(excludedApps.contains(appEntry.getPackageId()));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    AppEntry getItem(int id) {
        return data.get(id);
    }

    void setClickListener(ItemClickListener itemClickListener) {
        this.clickListener = itemClickListener;
    }

    public interface ItemClickListener {
        void onItemClick(View view, int position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final ImageView appIcon;
        final TextView appName;
        final CheckBox isExcluded;

        ViewHolder(View itemView) {
            super(itemView);

            appIcon = (ImageView) itemView.findViewById(R.id.app_list_row_icon);
            appName = (TextView) itemView.findViewById(R.id.app_list_row_name);
            isExcluded = (CheckBox) itemView.findViewById(R.id.app_list_row_checkbox);

            itemView.setOnClickListener(this);
            appIcon.setOnClickListener(this);
            appName.setOnClickListener(this);
            isExcluded.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (clickListener != null) {
                clickListener.onItemClick(view, getAdapterPosition());
            }

            // toggle is excluded whenever something other than isExcluded is clicked
            if (view.getId() != R.id.app_list_row_checkbox) {
                isExcluded.setChecked(!isExcluded.isChecked());
            }
        }
    }
}