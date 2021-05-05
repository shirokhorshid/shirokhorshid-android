package com.psiphon3;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.psiphon3.psiphonlibrary.EmbeddedValues;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class HomeTabFragment extends Fragment {
    private MainActivityViewModel viewModel;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private ViewFlipper sponsorViewFlipper;
    private ScrollView statusLayout;
    private ImageButton statusViewImage;
    private View mainView;
    private SponsorHomePage sponsorHomePage;
    private boolean isWebViewLoaded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.home_tab_layout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mainView = view;

        ((TextView) view.findViewById(R.id.versionline))
                .setText(requireContext().getString(R.string.client_version, EmbeddedValues.CLIENT_VERSION));

        sponsorViewFlipper = view.findViewById(R.id.sponsorViewFlipper);
        sponsorViewFlipper.setInAnimation(AnimationUtils.loadAnimation(requireContext(), android.R.anim.slide_in_left));
        sponsorViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(requireContext(), android.R.anim.slide_out_right));

        statusLayout = view.findViewById(R.id.statusLayout);
        statusViewImage = view.findViewById(R.id.statusViewImage);

        viewModel = new ViewModelProvider(requireActivity(),
                new ViewModelProvider.AndroidViewModelFactory(requireActivity().getApplication()))
                .get(MainActivityViewModel.class);

        // This Rx subscription observes tunnel state changes and updates the status UI,
        // it also loads sponsor home pages in the embedded web view if needed.
        compositeDisposable.add(viewModel.tunnelStateFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                // Update the connection status UI
                .doOnNext(this::updateStatusUI)
                // Check for URLs to be opened in the embedded web view.
                .doOnNext(tunnelState -> {
                    // If the tunnel is either stopped or running but not connected
                    // then stop loading the sponsor page and flip to status view.
                    if (tunnelState.isStopped() ||
                            (tunnelState.isRunning() && !tunnelState.connectionData().isConnected())) {
                        if (sponsorHomePage != null) {
                            sponsorHomePage.stop();
                        }
                        boolean isShowingWebView = sponsorViewFlipper.getCurrentView() != statusLayout;
                        if (isShowingWebView) {
                            sponsorViewFlipper.showNext();
                        }
                        // Also reset isWebViewLoaded
                        isWebViewLoaded = false;
                    }
                })
                // Only pass through if tunnel is running and connected
                .filter(tunnelState -> tunnelState.isRunning() && tunnelState.connectionData().isConnected())
                .flatMap(tunnelState -> {
                    ArrayList<String> homePages = tunnelState.connectionData().homePages();
                    if (homePages == null || homePages.size() == 0) {
                        // There's no URL to load
                        return Flowable.empty();
                    }
                    String url = homePages.get(0);

                    if (isWebViewLoaded || !MainActivity.shouldLoadInEmbeddedWebView(url)) {
                        // The embedded view has loaded the URL already or the URL should not
                        // be loaded in the embedded view
                        return Flowable.empty();
                    }
                    // Pass the URL downstream to be loaded in the embedded web view
                    return Flowable.just(url);
                })
                .doOnNext(this::loadEmbeddedWebView)
                .subscribe());

        // Observe last log entry to display.
        final TextView lastLogEntryTv = view.findViewById(R.id.lastlogline);
        compositeDisposable.add(viewModel.lastLogEntryFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(lastLogEntryTv::setText)
                .subscribe());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();
        if (sponsorHomePage != null) {
            sponsorHomePage.stop();
        }
    }

    private void updateStatusUI(TunnelState tunnelState) {
        if (tunnelState.isRunning()) {
            if (tunnelState.connectionData().isConnected()) {
                statusViewImage.setImageResource(R.drawable.status_icon_connected);
            } else {
                statusViewImage.setImageResource(R.drawable.status_icon_connecting);
            }
        } else {
            // the tunnel state is either unknown or not running
            statusViewImage.setImageResource(R.drawable.status_icon_disconnected);
        }
    }

    private void loadEmbeddedWebView(String url) {
        isWebViewLoaded = true;
        sponsorHomePage = new SponsorHomePage(mainView.findViewById(R.id.sponsorWebView),
                mainView.findViewById(R.id.sponsorWebViewProgressBar));
        sponsorHomePage.load(url);

        // Flip to the web view if it is not showing
        boolean isShowingWebView = sponsorViewFlipper.getCurrentView() != statusLayout;
        if (!isShowingWebView) {
            sponsorViewFlipper.showNext();
        }
    }

    protected class SponsorHomePage {
        private class SponsorWebChromeClient extends WebChromeClient {
            private final ProgressBar mProgressBar;

            public SponsorWebChromeClient(ProgressBar progressBar) {
                super();
                mProgressBar = progressBar;
            }

            private boolean mStopped = false;

            public void stop() {
                mStopped = true;
            }

            @Override
            public void onProgressChanged(WebView webView, int progress) {
                if (mStopped) {
                    return;
                }

                mProgressBar.setProgress(progress);
                mProgressBar.setVisibility(progress == 100 ? View.GONE : View.VISIBLE);
            }
        }

        private class SponsorWebViewClient extends WebViewClient {
            private Timer mTimer;
            private boolean mWebViewLoaded = false;
            private boolean mStopped = false;

            public void stop() {
                mStopped = true;
                if (mTimer != null) {
                    mTimer.cancel();
                    mTimer = null;
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                if (mStopped) {
                    return true;
                }

                if (mTimer != null) {
                    mTimer.cancel();
                    mTimer = null;
                }

                if (mWebViewLoaded) {
                    viewModel.signalExternalBrowserUrl(url);
                }
                return mWebViewLoaded;
            }

            @Override
            public void onPageFinished(WebView webView, String url) {
                if (mStopped) {
                    return;
                }

                if (!mWebViewLoaded) {
                    mTimer = new Timer();
                    mTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if (mStopped) {
                                return;
                            }
                            mWebViewLoaded = true;
                        }
                    }, 2000);
                }
            }
        }

        private final WebView mWebView;
        private final SponsorWebViewClient mWebViewClient;
        private final SponsorWebChromeClient mWebChromeClient;
        private final ProgressBar mProgressBar;

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        public SponsorHomePage(WebView webView, ProgressBar progressBar) {
            mWebView = webView;
            mProgressBar = progressBar;
            mWebChromeClient = new SponsorWebChromeClient(mProgressBar);
            mWebViewClient = new SponsorWebViewClient();

            mWebView.setWebChromeClient(mWebChromeClient);
            mWebView.setWebViewClient(mWebViewClient);

            WebSettings webSettings = mWebView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true);
            webSettings.setLoadWithOverviewMode(true);
            webSettings.setUseWideViewPort(true);
        }

        public void stop() {
            mWebViewClient.stop();
            mWebChromeClient.stop();
        }

        public void load(String url) {
            mProgressBar.setVisibility(View.VISIBLE);
            mWebView.loadUrl(url);
        }
    }
}
