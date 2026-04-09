/*
 * Copyright (c) 2022, Psiphon Inc.
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

package com.psiphon3;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.psiphon3.log.LoggingContentProvider;
import com.psiphon3.log.MyLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.disposables.CompositeDisposable;

public class LogsTabFragment extends Fragment {
    private LogsListAdapter pagingAdapter;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private MainActivityViewModel viewModel;
    private int lastItemCount;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.logs_tab_layout, container, false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt("lastItemCount", lastItemCount);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState != null) {
            lastItemCount = savedInstanceState.getInt("lastItemCount", 0);
        }

        viewModel = new ViewModelProvider(requireActivity(),
                new ViewModelProvider.AndroidViewModelFactory(requireActivity().getApplication()))
                .get(MainActivityViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setReverseLayout(true);
        layoutManager.setStackFromEnd(true);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(null);

        pagingAdapter = new LogsListAdapter(new LogsListAdapter.LogEntryComparator());
        recyclerView.setAdapter(pagingAdapter);

        pagingAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int currentItemCount = pagingAdapter.getItemCount();
                if (currentItemCount != lastItemCount) {
                    recyclerView.scrollToPosition(0);
                }
                lastItemCount = currentItemCount;
            }
        });

        view.findViewById(R.id.btnClearLogs).setOnClickListener(v -> clearLogs());
        view.findViewById(R.id.btnShareLogs).setOnClickListener(v -> saveLogs());
    }

    private void clearLogs() {
        Uri deleteUri = LoggingContentProvider.CONTENT_URI.buildUpon()
                .appendPath("delete")
                .appendPath(String.valueOf(System.currentTimeMillis() + 1000))
                .build();
        requireContext().getContentResolver().delete(deleteUri, null, null);
    }

    private void saveLogs() {
        executor.execute(() -> {
            Uri allLogsUri = LoggingContentProvider.CONTENT_URI.buildUpon()
                    .appendPath("all")
                    .appendPath(String.valueOf(System.currentTimeMillis() + 1000))
                    .build();

            StringBuilder sb = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);

            try (Cursor cursor = requireContext().getContentResolver()
                    .query(allLogsUri, null, null, null, "timestamp ASC")) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long ts = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));
                        String logJson = cursor.getString(cursor.getColumnIndexOrThrow("logjson"));
                        boolean isDiagnostic = cursor.getInt(
                                cursor.getColumnIndexOrThrow("is_diagnostic")) != 0;

                        String msg;
                        if (isDiagnostic) {
                            try {
                                JSONObject obj = new JSONObject(logJson);
                                String m = obj.getString("msg");
                                JSONObject data = obj.optJSONObject("data");
                                msg = data == null ? m : m + ":" + data.toString();
                            } catch (JSONException e) {
                                msg = logJson;
                            }
                        } else {
                            msg = MyLog.getStatusLogMessageForDisplay(logJson, requireContext());
                        }

                        if (msg != null && !msg.isEmpty()) {
                            sb.append(sdf.format(new Date(ts)))
                                    .append("  ")
                                    .append(msg)
                                    .append("\n");
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            String text = sb.toString();
            String ts2 = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            String filename = "ShirOKhorshid-" + ts2 + ".txt";

            try {
                File dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (dir != null) {
                    dir.mkdirs();
                    File file = new File(dir, filename);
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(text);
                    }
                    Uri fileUri = FileProvider.getUriForFile(
                            requireContext(),
                            "com.shirokhorshid.vpn.UpgradeFileProvider",
                            file);
                    requireActivity().runOnUiThread(() -> {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, filename);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(shareIntent,
                                getString(R.string.share_logs_button)));
                    });
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        compositeDisposable.add(viewModel.logsPagedListFlowable()
                .doOnNext(logEntries -> pagingAdapter.submitList(logEntries))
                .subscribe());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();
        executor.shutdown();
    }

    @Override
    public void onPause() {
        super.onPause();
        compositeDisposable.clear();
    }
}
