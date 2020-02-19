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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;

import com.psiphon3.R;

public class ListPreference extends android.preference.ListPreference {
    private int clickedIndex;

    public ListPreference(Context context) {
        super(context);
    }

    public ListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateDialogView() {
        Context context = getContext();

        View view = View.inflate(context, R.layout.dialog_list_preference, null);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        TextListRecyclerViewAdapter adapter = new TextListRecyclerViewAdapter(context, getEntries());
        adapter.setClickListener(new TextListRecyclerViewAdapter.ItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                clickedIndex = position;

                Dialog dialog = getDialog();
                ListPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                dialog.dismiss();
            }
        });
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        return view;
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        builder.setPositiveButton(null, null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult && clickedIndex >= 0 && getEntryValues() != null) {
            String value = getEntryValues()[clickedIndex].toString();
            if (callChangeListener(value)) {
                setValue(value);
            }
        }
    }
}