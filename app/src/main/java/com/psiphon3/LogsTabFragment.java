package com.psiphon3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.psiphon3.psiphonlibrary.StatusList;

public class LogsTabFragment extends Fragment {
    public static final String STATUS_ENTRY_AVAILABLE = "com.psiphon3.LogsTabFragment.STATUS_ENTRY_AVAILABLE";
    private StatusList.StatusListViewManager statusListViewManager = null;
    private StatusEntryAddedReceiver statusEntryAddedBroadcastReceiver;
    private LocalBroadcastManager localBroadcastManager;
    private MainActivityViewModel viewModel;

    @Override
    public void onDestroy() {
        super.onDestroy();
        statusListViewManager.onDestroy();
        localBroadcastManager.unregisterReceiver(statusEntryAddedBroadcastReceiver);
    }

    @Override
    public void onViewCreated(@NonNull View fragmentView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(fragmentView, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity(),
                new ViewModelProvider.AndroidViewModelFactory(requireActivity().getApplication()))
                .get(MainActivityViewModel.class);

        ListView statusListView = fragmentView.findViewById(R.id.statusList);
        statusListViewManager = new StatusList.StatusListViewManager(statusListView);
        statusEntryAddedBroadcastReceiver = new StatusEntryAddedReceiver();
        localBroadcastManager = LocalBroadcastManager.getInstance(requireContext());
        localBroadcastManager.registerReceiver(statusEntryAddedBroadcastReceiver, new IntentFilter(STATUS_ENTRY_AVAILABLE));

        // Force the UI to display logs already loaded into the StatusList message history
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(new Intent(STATUS_ENTRY_AVAILABLE));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.logs_tab_layout, container, false);
    }

    public class StatusEntryAddedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (statusListViewManager != null) {
                statusListViewManager.notifyStatusAdded();
            }
            StatusList.StatusEntry statusEntry = StatusList.getLastStatusEntryForDisplay();
            if (statusEntry != null) {
                String log = requireContext().getString(statusEntry.stringId(), statusEntry.formatArgs());
                viewModel.signalLastLogEntryAdded(log);
            }
        }
    }
}
