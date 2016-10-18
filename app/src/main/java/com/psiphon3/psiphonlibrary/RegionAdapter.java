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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RegionAdapter extends ArrayAdapter<Integer>
{
    public static final String KNOWN_REGIONS_PREFERENCE = "knownRegionsPreference";

    private static class Region
    {
        String code;
        int nameResourceId;
        int flagResourceId;
        AtomicBoolean serverExists;
        
        Region(String code, int nameResourceId, int flagResourceId, boolean serverExists)
        {
            this.code = code;
            this.nameResourceId = nameResourceId;
            this.flagResourceId = flagResourceId;
            this.serverExists = new AtomicBoolean(serverExists);
        }
    }
    
    private static Region[] regions =
    {
        new Region(PsiphonConstants.REGION_CODE_ANY, R.string.region_name_any, R.drawable.flag_unknown, true),
        new Region("CA", R.string.region_name_ca, R.drawable.flag_ca, false),
        new Region("DE", R.string.region_name_de, R.drawable.flag_de, false),
        new Region("ES", R.string.region_name_es, R.drawable.flag_es, false),
        new Region("GB", R.string.region_name_gb, R.drawable.flag_gb, false),
        new Region("HK", R.string.region_name_hk, R.drawable.flag_hk, false),
        new Region("IN", R.string.region_name_in, R.drawable.flag_in, false),
        new Region("JP", R.string.region_name_jp, R.drawable.flag_jp, false),
        new Region("NL", R.string.region_name_nl, R.drawable.flag_nl, false),
        new Region("SG", R.string.region_name_sg, R.drawable.flag_sg, false),
        new Region("US", R.string.region_name_us, R.drawable.flag_us, false),
    };
    
    private static boolean initialized = false;
    
    public static void initialize(Context context)
    {
        // Restore a list of known regions that's cached as a preferences
        // value. This workaround done because the JSON parsing/processing
        // of the ServerEntry list that's done in ServerInterface is slow:
        // up to 2-3 seconds on a fast device with a realistic-sized server
        // list. That's too long to block the UI thread. (We will still
        // hit that parsing in the UI thread on the first run with this
        // feature).
        
        // NOTE: this caching assumes regions are only added, not removed.
        
        // Would use get/setStringSet, but that requires API 11.
        // Since JSON parsing is what we want to avoid, we store the list
        // of ISO 3166-1 alpha-2 region codes as a simple comma delimited
        // string (without escaping).
        
        // NOTE: retaining this with tunnel-core, as this allows region
        // display, selection, and restore before the tunnel-core is run
        // and emits AvailableEgressRegions. The original assumption about
        // regions only being added, not removed, still applies.

        String knownRegions = new AppPreferences(context).getString(KNOWN_REGIONS_PREFERENCE, "");
        for (String region : knownRegions.split(","))
        {
            setServerExists(context, region, true);
        }
        
        initialized = true;
    }

    public static void setServersExist(Context context, List<String> regionCodes)
    {
        if (regionCodes == null)
        {
            return;
        }
        for (String regionCode : regionCodes)
        {
            setServerExists(context, regionCode, false);
        }
    }

    public static void setServerExists(Context context, String regionCode, boolean restoringKnownRegions)
    {
        // TODO: may want to replace linear lookup once there are many regions

        boolean newRegion = false;
        
        for (Region region : regions)
        {
            if (region.code.equals(regionCode))
            {
                newRegion = !region.serverExists.get(); 
                region.serverExists.set(true);
                break;
            }
        }
        
        if (newRegion && !restoringKnownRegions)
        {
            StringBuilder knownRegions = new StringBuilder();

            for (Region region : regions)
            {
                if (region.serverExists.get())
                {
                    if (knownRegions.length() > 0)
                    {
                        knownRegions.append(",");
                    }
                    knownRegions.append(region.code);
                }
            }

            new AppPreferences(context).put(KNOWN_REGIONS_PREFERENCE, knownRegions.toString());
        }
    }
    
    Context m_context;

    public RegionAdapter(Context context)
    {
        super(context, R.layout.region_row);
        m_context = context;
        if (!initialized)
        {
            throw new RuntimeException("failed to call RegionAdapter.initialize");
        }
        populate();
    }
    
    public void populate()
    {
        // Only change/redraw if the existing region set has changed. This logic
        // assumes regions can only be added (via setServerExists), not removed.
        int count = 0;
        for (int index = 0; index < regions.length; index++)
        {
            if (regions[index].serverExists.get())
            {
                count++;
            }
        }        
        if (count == getCount())
        {
            return;
        }

        clear();
        for (int index = 0; index < regions.length; index++)
        {
            if (regions[index].serverExists.get())
            {
                add(index);
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
        
        int index = getItem(position);

        ImageView icon = (ImageView)row.findViewById(R.id.regionRowImage);
        icon.setImageResource(regions[index].flagResourceId);

        TextView label = (TextView)row.findViewById(R.id.regionRowText);
        label.setText(m_context.getString(regions[index].nameResourceId));

        return row;
    }
    
    public String getSelectedRegionCode(int position)
    {
        int index = getItem(position);

        return regions[index].code;
    }

    
    public int getPositionForRegionCode(String regionCode)
    {
        for (int index = 0; index < regions.length; index ++)
        {
            if (regionCode.equals(regions[index].code))
            {
                return getPosition(index);
            }
        }

        // Default to ANY. Might happen if persistent selection is used
        // on different client version which doesn't have a corresponding Region
        assert(regions[0].code.equals(PsiphonConstants.REGION_CODE_ANY));
        return 0;
    }
}
