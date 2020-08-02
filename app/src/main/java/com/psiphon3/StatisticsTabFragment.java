package com.psiphon3;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.psiphon3.psiphonlibrary.DataTransferStats;
import com.psiphon3.psiphonlibrary.Utils;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.util.ArrayList;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class StatisticsTabFragment extends Fragment {
    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private TextView elapsedConnectionTimeView;
    private TextView totalSentView;
    private TextView totalReceivedView;
    private DataTransferGraph slowSentGraph;
    private DataTransferGraph slowReceivedGraph;
    private DataTransferGraph fastSentGraph;
    private DataTransferGraph fastReceivedGraph;

    private void updateStatisticsUICallback(boolean isConnected) {
        DataTransferStats.DataTransferStatsForUI dataTransferStats = DataTransferStats.getDataTransferStatsForUI();
        elapsedConnectionTimeView.setText(isConnected ? getString(R.string.connected_elapsed_time,
                Utils.elapsedTimeToDisplay(dataTransferStats.getElapsedTime())) : getString(R.string.disconnected));
        totalSentView.setText(Utils.byteCountToDisplaySize(dataTransferStats.getTotalBytesSent(), false));
        totalReceivedView.setText(Utils.byteCountToDisplaySize(dataTransferStats.getTotalBytesReceived(), false));
        slowSentGraph.update(dataTransferStats.getSlowSentSeries());
        slowReceivedGraph.update(dataTransferStats.getSlowReceivedSeries());
        fastSentGraph.update(dataTransferStats.getFastSentSeries());
        fastReceivedGraph.update(dataTransferStats.getFastReceivedSeries());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();
    }

    @Override
    public void onViewCreated(@NonNull View fragmentView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(fragmentView, savedInstanceState);

        MainActivityViewModel viewModel = new ViewModelProvider(requireActivity(),
                new ViewModelProvider.AndroidViewModelFactory(requireActivity().getApplication()))
                .get(MainActivityViewModel.class);

        elapsedConnectionTimeView = fragmentView.findViewById(R.id.elapsedConnectionTime);
        totalSentView = fragmentView.findViewById(R.id.totalSent);
        totalReceivedView = fragmentView.findViewById(R.id.totalReceived);

        slowSentGraph = new DataTransferGraph(fragmentView, R.id.slowSentGraph);
        slowReceivedGraph = new DataTransferGraph(fragmentView, R.id.slowReceivedGraph);
        fastSentGraph = new DataTransferGraph(fragmentView, R.id.fastSentGraph);
        fastReceivedGraph = new DataTransferGraph(fragmentView, R.id.fastReceivedGraph);

        compositeDisposable.add(viewModel.dataStatsFlowable()
                .startWith(Boolean.FALSE)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(this::updateStatisticsUICallback)
                .subscribe());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.statistics_tab_layout, container, false);
    }

    private class DataTransferGraph {
        private final LinearLayout m_graphLayout;
        private GraphicalView m_chart;
        private final XYMultipleSeriesDataset m_chartDataset;
        private final XYMultipleSeriesRenderer m_chartRenderer;
        private final XYSeries m_chartCurrentSeries;
        private final XYSeriesRenderer m_chartCurrentRenderer;

        DataTransferGraph(View containerView, int layoutId) {
            m_graphLayout = containerView.findViewById(layoutId);
            m_chartDataset = new XYMultipleSeriesDataset();
            m_chartRenderer = new XYMultipleSeriesRenderer();
            m_chartRenderer.setGridColor(Color.GRAY);
            m_chartRenderer.setShowGrid(true);
            m_chartRenderer.setShowLabels(false);
            m_chartRenderer.setShowLegend(false);
            m_chartRenderer.setShowAxes(false);
            m_chartRenderer.setPanEnabled(false, false);
            m_chartRenderer.setZoomEnabled(false, false);

            // Make the margins transparent.
            // Note that this value is a bit magical. One would expect
            // android.graphics.Color.TRANSPARENT to work, but it doesn't.
            // Nor does 0x00000000. Ref:
            // http://developer.android.com/reference/android/graphics/Color.html
            m_chartRenderer.setMarginsColor(0x00FFFFFF);

            m_chartCurrentSeries = new XYSeries("");
            m_chartDataset.addSeries(m_chartCurrentSeries);
            m_chartCurrentRenderer = new XYSeriesRenderer();
            m_chartCurrentRenderer.setColor(Color.YELLOW);
            m_chartRenderer.addSeriesRenderer(m_chartCurrentRenderer);
        }

        public void update(ArrayList<Long> data) {
            m_chartCurrentSeries.clear();
            for (int i = 0; i < data.size(); i++) {
                m_chartCurrentSeries.add(i, data.get(i));
            }
            if (m_chart == null) {
                m_chart = ChartFactory.getLineChartView(StatisticsTabFragment.this.requireActivity(), m_chartDataset, m_chartRenderer);
                m_graphLayout.addView(m_chart);
            } else {
                m_chart.repaint();
            }
        }
    }
}
