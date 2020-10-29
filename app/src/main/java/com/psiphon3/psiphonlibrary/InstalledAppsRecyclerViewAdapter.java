/*
 * Copyright (c) 2019, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.psiphon3.psiphonlibrary;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import com.psiphon3.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class InstalledAppsRecyclerViewAdapter extends RecyclerView.Adapter<InstalledAppsRecyclerViewAdapter.ViewHolder>
        implements Filterable {
    private final LayoutInflater inflater;
    private final List<AppEntry> data;
    private List<AppEntry> dataFiltered;

    public Set<String> getSelectedApps() {
        return selectedApps;
    }

    public int getUnfilteredItemsCount() {
        return data.size();
    }

    private final Set<String> selectedApps;

    private ItemClickListener clickListener;

    InstalledAppsRecyclerViewAdapter(Context context, List<AppEntry> data, Set<String> selectedApps) {
        this.inflater = LayoutInflater.from(context);
        this.data = data;
        this.dataFiltered = data;
        this.selectedApps = selectedApps;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.preference_widget_applist_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        final AppEntry appEntry = dataFiltered.get(position);

        appEntry.getIconLoader()
                .doOnSuccess(icon -> {
                    // check to see if the adapter position matches the position of the holder
                    // if it does then set the picture
                    if (position == holder.getAdapterPosition()) {
                        holder.appIcon.setImageDrawable(icon);
                    }
                })
                .subscribe();
        holder.appName.setText(appEntry.getName());
        holder.isSelected.setChecked(selectedApps.contains(appEntry.getPackageId()));
    }

    @Override
    public int getItemCount() {
        return dataFiltered.size();
    }

    AppEntry getItem(int id) {
        return dataFiltered.get(id);
    }

    void setClickListener(ItemClickListener itemClickListener) {
        this.clickListener = itemClickListener;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                final String charString = charSequence.toString().toLowerCase();
                if (charString.isEmpty()) {
                    dataFiltered = data;
                } else {
                    List<AppEntry> filteredList = new ArrayList<>();
                    for (AppEntry row : data) {
                        if (row.getName().toLowerCase().contains(charString)) {
                                // TODO: also filter by package name too?
                                // row.getPackageId().contains(charString)
                            filteredList.add(row);
                        }
                    }

                    dataFiltered = filteredList;
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = dataFiltered;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                dataFiltered = (ArrayList<AppEntry>) filterResults.values;
                notifyDataSetChanged();
            }
        };
    }

    public interface ItemClickListener {
        void onItemClick(View view, int position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final ImageView appIcon;
        final TextView appName;
        final CheckBox isSelected;

        ViewHolder(View itemView) {
            super(itemView);

            appIcon = (ImageView) itemView.findViewById(R.id.app_list_row_icon);
            appName = (TextView) itemView.findViewById(R.id.app_list_row_name);
            isSelected = (CheckBox) itemView.findViewById(R.id.app_list_row_checkbox);

            itemView.setOnClickListener(this);
            appIcon.setOnClickListener(this);
            appName.setOnClickListener(this);
            isSelected.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (clickListener != null) {
                clickListener.onItemClick(view, getAdapterPosition());
            }

            // toggle isSelected whenever something other than isSelected is clicked
            if (view.getId() != isSelected.getId()) {
                isSelected.setChecked(!isSelected.isChecked());
            }
        }
    }
}
