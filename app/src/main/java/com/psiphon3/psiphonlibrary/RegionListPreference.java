/*
 * Copyright (c) 2020, Psiphon Inc.
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
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.psiphon3.R;

import net.grandcentrix.tray.AppPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RegionListPreference extends Preference {
    private final AppPreferences multiProcessPreferences;
    private Region currentRegion;
    private ImageView flagView;

    public static final String KNOWN_REGIONS_PREFERENCE = "knownRegionsPreference";
    private RegionRecyclerViewAdapter adapter;
    private onRegionSelectedListener regionSelectedListener;

    public RegionListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        multiProcessPreferences = new AppPreferences(context);
        setWidgetLayoutResource(R.layout.region_selector_pref_widget_layout);

        setCurrentRegionFromPreferences();
    }

    public RegionListPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onClick() {
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());

        View view = layoutInflater.inflate(R.layout.dialog_list_preference, null);

        new AlertDialog.Builder(getContext())
                .setView(view)
                .setTitle(R.string.region_selector)
                .setPositiveButton(R.string.label_ok, (dialog, which) -> {
                    if (adapter != null && regionSelectedListener != null) {
                        regionSelectedListener.onRegionSelected(adapter.selectedRegionCode);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setCancelable(true)
                .show();
        loadRegionSelectorView(view);
    }

    private void loadRegionSelectorView(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        String knownRegions = multiProcessPreferences.getString(KNOWN_REGIONS_PREFERENCE, "");

        ArrayList<String> regionCodes = new ArrayList<>();
        regionCodes.add(PsiphonConstants.REGION_CODE_ANY);
        for (String regionCode : SharedPreferenceUtils.deserializeSet(knownRegions)) {
            if (allRegions.containsKey(regionCode)) {
                regionCodes.add(regionCode);
            }
        }

        String selectedRegionCode;
        if (currentRegion != null) {
            selectedRegionCode = currentRegion.code;
        } else {
            selectedRegionCode = PsiphonConstants.REGION_CODE_ANY;
        }

        adapter = new RegionRecyclerViewAdapter(getContext(), regionCodes, selectedRegionCode);
        recyclerView.setAdapter(adapter);
        view.findViewById(R.id.recycler_view).setVisibility(View.VISIBLE);
        view.findViewById(R.id.progress_overlay).setVisibility(View.GONE);
        int position = regionCodes.indexOf(selectedRegionCode);
        if (position >= 0) {
            recyclerView.scrollToPosition(position);
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        flagView = (ImageView) holder.findViewById(R.id.flagView);
        if (currentRegion != null) {
            flagView.post(() -> flagView.setImageResource(currentRegion.flagResourceId));
        }
    }

    public void setCurrentRegionFromPreferences() {
        String egressRegionPreference = multiProcessPreferences
                .getString(getContext().getString(R.string.egressRegionPreference),
                        PsiphonConstants.REGION_CODE_ANY);
        currentRegion = allRegions.get(egressRegionPreference);
        if (currentRegion != null) {
            setSummary(currentRegion.nameResourceId);
        }
    }

    private static class Region {
        String code;
        int nameResourceId;
        int flagResourceId;

        Region(String code, int nameResourceId, int flagResourceId) {
            this.code = code;
            this.nameResourceId = nameResourceId;
            this.flagResourceId = flagResourceId;
        }
    }

    private static final HashMap<String, Region> allRegions;

    static {
        allRegions = new HashMap<>();
        allRegions.put(PsiphonConstants.REGION_CODE_ANY, new Region(PsiphonConstants.REGION_CODE_ANY,
                R.string.region_name_any, R.drawable.flag_any));
        allRegions.put("AE", new Region("AE", R.string.region_name_ae, R.drawable.flag_ae));
        allRegions.put("AR", new Region("AR", R.string.region_name_ar, R.drawable.flag_ar));
        allRegions.put("AT", new Region("AT", R.string.region_name_at, R.drawable.flag_at));
        allRegions.put("AU", new Region("AU", R.string.region_name_au, R.drawable.flag_au));
        allRegions.put("BE", new Region("BE", R.string.region_name_be, R.drawable.flag_be));
        allRegions.put("BG", new Region("BG", R.string.region_name_bg, R.drawable.flag_bg));
        allRegions.put("BR", new Region("BR", R.string.region_name_br, R.drawable.flag_br));
        allRegions.put("CA", new Region("CA", R.string.region_name_ca, R.drawable.flag_ca));
        allRegions.put("CH", new Region("CH", R.string.region_name_ch, R.drawable.flag_ch));
        allRegions.put("CL", new Region("CL", R.string.region_name_cl, R.drawable.flag_cl));
        allRegions.put("CO", new Region("CO", R.string.region_name_co, R.drawable.flag_co));
        allRegions.put("CZ", new Region("CZ", R.string.region_name_cz, R.drawable.flag_cz));
        allRegions.put("DE", new Region("DE", R.string.region_name_de, R.drawable.flag_de));
        allRegions.put("DK", new Region("DK", R.string.region_name_dk, R.drawable.flag_dk));
        allRegions.put("EE", new Region("EE", R.string.region_name_ee, R.drawable.flag_ee));
        allRegions.put("ES", new Region("ES", R.string.region_name_es, R.drawable.flag_es));
        allRegions.put("FI", new Region("FI", R.string.region_name_fi, R.drawable.flag_fi));
        allRegions.put("FR", new Region("FR", R.string.region_name_fr, R.drawable.flag_fr));
        allRegions.put("GB", new Region("GB", R.string.region_name_gb, R.drawable.flag_gb));
        allRegions.put("GR", new Region("GR", R.string.region_name_gr, R.drawable.flag_gr));
        allRegions.put("HK", new Region("HK", R.string.region_name_hk, R.drawable.flag_hk));
        allRegions.put("HU", new Region("HU", R.string.region_name_hu, R.drawable.flag_hu));
        allRegions.put("HR", new Region("HR", R.string.region_name_hr, R.drawable.flag_hr));
        allRegions.put("ID", new Region("ID", R.string.region_name_id, R.drawable.flag_id));
        allRegions.put("IE", new Region("IE", R.string.region_name_ie, R.drawable.flag_ie));
        allRegions.put("IN", new Region("IN", R.string.region_name_in, R.drawable.flag_in));
        allRegions.put("IS", new Region("IS", R.string.region_name_is, R.drawable.flag_is));
        allRegions.put("IT", new Region("IT", R.string.region_name_it, R.drawable.flag_it));
        allRegions.put("JP", new Region("JP", R.string.region_name_jp, R.drawable.flag_jp));
        allRegions.put("KE", new Region("KE", R.string.region_name_ke, R.drawable.flag_ke));
        allRegions.put("KR", new Region("KR", R.string.region_name_kr, R.drawable.flag_kr));
        allRegions.put("LT", new Region("LT", R.string.region_name_lt, R.drawable.flag_lt));
        allRegions.put("LV", new Region("LV", R.string.region_name_lv, R.drawable.flag_lv));
        allRegions.put("MX", new Region("MX", R.string.region_name_mx, R.drawable.flag_mx));
        allRegions.put("MY", new Region("MY", R.string.region_name_my, R.drawable.flag_my));
        allRegions.put("NL", new Region("NL", R.string.region_name_nl, R.drawable.flag_nl));
        allRegions.put("NO", new Region("NO", R.string.region_name_no, R.drawable.flag_no));
        allRegions.put("NZ", new Region("NZ", R.string.region_name_nz, R.drawable.flag_nz));
        allRegions.put("PL", new Region("PL", R.string.region_name_pl, R.drawable.flag_pl));
        allRegions.put("PT", new Region("PT", R.string.region_name_pt, R.drawable.flag_pt));
        allRegions.put("RO", new Region("RO", R.string.region_name_ro, R.drawable.flag_ro));
        allRegions.put("RS", new Region("RS", R.string.region_name_rs, R.drawable.flag_rs));
        allRegions.put("SE", new Region("SE", R.string.region_name_se, R.drawable.flag_se));
        allRegions.put("SG", new Region("SG", R.string.region_name_sg, R.drawable.flag_sg));
        allRegions.put("SK", new Region("SK", R.string.region_name_sk, R.drawable.flag_sk));
        allRegions.put("TW", new Region("TW", R.string.region_name_tw, R.drawable.flag_tw));
        allRegions.put("UA", new Region("UA", R.string.region_name_ua, R.drawable.flag_ua));
        allRegions.put("US", new Region("US", R.string.region_name_us, R.drawable.flag_us));
        allRegions.put("ZA", new Region("ZA", R.string.region_name_za, R.drawable.flag_za));
    }


    private class RegionRecyclerViewAdapter extends RecyclerView.Adapter<ViewHolder> {
        private final LayoutInflater inflater;
        private final List<String> regionCodes;
        private String selectedRegionCode;

        public RegionRecyclerViewAdapter(Context context, List<String> regionCodes, String selectedRegionCode) {
            this.inflater = LayoutInflater.from(context);
            this.regionCodes = regionCodes;
            this.selectedRegionCode = selectedRegionCode;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.preference_region_selection_row, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, final int position) {
            final String regionCode = regionCodes.get(position);
            final Region region = allRegions.get(regionCode);

            holder.itemView.setOnClickListener(null);
            holder.regionName.setText(region.nameResourceId);
            holder.flagImage.setImageResource(region.flagResourceId);
            holder.isSelected.setChecked(selectedRegionCode.equals(regionCode));
            holder.itemView.findViewById(R.id.regionSelectionRow).setOnClickListener(v -> {
                selectedRegionCode = region.code;
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return regionCodes != null ? regionCodes.size() : 0;
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView flagImage;
        final TextView regionName;
        final RadioButton isSelected;

        ViewHolder(View itemView) {
            super(itemView);
            flagImage = itemView.findViewById(R.id.regionRowImage);
            regionName = itemView.findViewById(R.id.regionRowText);
            isSelected = itemView.findViewById(R.id.regionRowRadioButton);
        }
    }

    public void setOnRegionSelectedListener(onRegionSelectedListener listener) {
        regionSelectedListener = listener;
    }

    public interface onRegionSelectedListener {
        void onRegionSelected(String regionCode);
    }
}
