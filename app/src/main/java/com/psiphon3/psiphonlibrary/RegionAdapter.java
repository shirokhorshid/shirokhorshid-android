/*
 * Copyright (c) 2016, Psiphon Inc.
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.psiphon3.R;

import net.grandcentrix.tray.AppPreferences;

import java.util.HashMap;

public class RegionAdapter extends ArrayAdapter<String>
{
    public static final String KNOWN_REGIONS_PREFERENCE = "knownRegionsPreference";

    private static class Region
    {
        String code;
        int nameResourceId;
        int flagResourceId;

        Region(String code, int nameResourceId, int flagResourceId)
        {
            this.code = code;
            this.nameResourceId = nameResourceId;
            this.flagResourceId = flagResourceId;
        }
    }
    
    private static final HashMap<String, Region> regions;
    static {
        regions = new HashMap<>();
        regions.put(PsiphonConstants.REGION_CODE_ANY, new Region(PsiphonConstants.REGION_CODE_ANY, R.string.region_name_any, R.drawable.flag_unknown));
        regions.put("AT", new Region("AT", R.string.region_name_at, R.drawable.flag_at));
        regions.put("BE", new Region("BE", R.string.region_name_be, R.drawable.flag_be));
        regions.put("BG", new Region("BG", R.string.region_name_bg, R.drawable.flag_bg));
        regions.put("CA", new Region("CA", R.string.region_name_ca, R.drawable.flag_ca));
        regions.put("CH", new Region("CH", R.string.region_name_ch, R.drawable.flag_ch));
        regions.put("CZ", new Region("CZ", R.string.region_name_cz, R.drawable.flag_cz));
        regions.put("DE", new Region("DE", R.string.region_name_de, R.drawable.flag_de));
        regions.put("DK", new Region("DK", R.string.region_name_dk, R.drawable.flag_dk));
        regions.put("ES", new Region("ES", R.string.region_name_es, R.drawable.flag_es));
        regions.put("FR", new Region("FR", R.string.region_name_fr, R.drawable.flag_fr));
        regions.put("GB", new Region("GB", R.string.region_name_gb, R.drawable.flag_gb));
        regions.put("HK", new Region("HK", R.string.region_name_hk, R.drawable.flag_hk));
        regions.put("HU", new Region("HU", R.string.region_name_hu, R.drawable.flag_hu));
        regions.put("IN", new Region("IN", R.string.region_name_in, R.drawable.flag_in));
        regions.put("IT", new Region("IT", R.string.region_name_it, R.drawable.flag_it));
        regions.put("JP", new Region("JP", R.string.region_name_jp, R.drawable.flag_jp));
        regions.put("NL", new Region("NL", R.string.region_name_nl, R.drawable.flag_nl));
        regions.put("NO", new Region("NO", R.string.region_name_no, R.drawable.flag_no));
        regions.put("PL", new Region("PL", R.string.region_name_pl, R.drawable.flag_pl));
        regions.put("RO", new Region("RO", R.string.region_name_ro, R.drawable.flag_ro));
        regions.put("SE", new Region("SE", R.string.region_name_se, R.drawable.flag_se));
        regions.put("SG", new Region("SG", R.string.region_name_sg, R.drawable.flag_sg));
        regions.put("SK", new Region("SK", R.string.region_name_sk, R.drawable.flag_sk));
        regions.put("US", new Region("US", R.string.region_name_us, R.drawable.flag_us));
    }

    Context m_context;
    String m_lastKnownRegionsPreference;

    public RegionAdapter(Context context)
    {
        super(context, R.layout.region_row);
        m_context = context;
        m_lastKnownRegionsPreference = "";
        updateRegionsFromPreferences();
    }
    
    public void updateRegionsFromPreferences() {
        String knownRegions = new AppPreferences(m_context).getString(KNOWN_REGIONS_PREFERENCE, "");

        // Only change/redraw if the existing region set has changed.
        if (knownRegions.length() > 0 && knownRegions.equals(m_lastKnownRegionsPreference)) {
            return;
        }
        m_lastKnownRegionsPreference = knownRegions;

        clear();

        // Add 'Best Performance' first
        add(PsiphonConstants.REGION_CODE_ANY);

        if(knownRegions.length() > 0) {
            for (String regionCode : m_lastKnownRegionsPreference.split(",")) {
                add(regionCode);
            }
        }

        notifyDataSetChanged();
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent)
    {
        return getCustomView(position, convertView, parent);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        return getCustomView(position, convertView, parent);
    }

    public View getCustomView(int position, View convertView, ViewGroup parent)
    {
        LayoutInflater inflater = (LayoutInflater)m_context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View row = inflater.inflate(R.layout.region_row, parent, false);
        
        String regionCode = getItem(position);
        Region region = regions.get(regionCode);

        if(region != null) {
            ImageView icon = (ImageView) row.findViewById(R.id.regionRowImage);
            icon.setImageResource(regions.get(regionCode).flagResourceId);

            TextView label = (TextView) row.findViewById(R.id.regionRowText);
            label.setText(m_context.getString(regions.get(regionCode).nameResourceId));
        }

        return row;
    }
}
