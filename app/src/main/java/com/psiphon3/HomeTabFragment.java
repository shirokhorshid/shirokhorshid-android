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

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
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
import com.psiphon3.psiphonlibrary.LocalizedActivities;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class HomeTabFragment extends Fragment {
    private MainActivityViewModel viewModel;
    private ViewFlipper sponsorViewFlipper;
    private ScrollView statusLayout;
    private ImageButton statusViewImage;
    private View mainView;
    private SponsorHomePage sponsorHomePage;
    private boolean isWebViewLoaded = false;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private TextView lastLogEntryTv;
    private ObjectAnimator pulseAnimator;

    // LAN proxy info views
    private LinearLayout lanProxyInfoSection;
    private TextView lanProxyHttpText;
    private TextView lanProxySocksText;

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
        // Use Lion & Sun emblem for all states; connection state shown via alpha/animation
        statusViewImage.setImageResource(R.drawable.lion_and_sun);
        statusViewImage.setImageAlpha(77); // Start dimmed (disconnected)

        lastLogEntryTv = view.findViewById(R.id.lastlogline);

        // LAN proxy info views
        lanProxyInfoSection = view.findViewById(R.id.lanProxyInfoSection);
        lanProxyHttpText = view.findViewById(R.id.lanProxyHttpText);
        lanProxySocksText = view.findViewById(R.id.lanProxySocksText);

        viewModel = new ViewModelProvider(requireActivity(),
                new ViewModelProvider.AndroidViewModelFactory(requireActivity().getApplication()))
                .get(MainActivityViewModel.class);
    }

    @Override
    public void onPause() {
        super.onPause();
        compositeDisposable.clear();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Observe last log entry to display.
        compositeDisposable.add(viewModel.lastLogEntryFlowable()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(lastLogEntryTv::setText)
                .subscribe());

        // Observes tunnel state changes and updates the status UI,
        // also loads sponsor home pages in the embedded web view if needed.
        compositeDisposable.add(((LocalizedActivities.AppCompatActivity) requireActivity())
                .getTunnelServiceInteractor().tunnelStateFlowable()
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
                // Load the embedded web view if needed
                .switchMap(tunnelState -> {
                    // Check if tunnel is connected
                    // Sponsor home pages disabled in Shir o Khorshid — these are
                    // Psiphon Inc promotional pages (e.g. "Install Conduit" prompts)
                    // that are not appropriate for this community build.
                    return Flowable.<String>empty();
                })
                .doOnNext(this::loadEmbeddedWebView)
                .subscribe());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();
        stopPulseAnimation();
        if (sponsorHomePage != null) {
            sponsorHomePage.stop();
        }
    }

    private void updateStatusUI(TunnelState tunnelState) {
        if (tunnelState.isRunning()) {
            if (tunnelState.connectionData().isConnected()) {
                // Connected: full brightness, no animation
                stopPulseAnimation();
                statusViewImage.setImageAlpha(255);
                // Show LAN proxy info if sharing is enabled
                updateLanProxyInfo(tunnelState.connectionData());
            } else {
                // Connecting: pulse animation
                startPulseAnimation();
                hideLanProxyInfo();
            }
        } else {
            // Disconnected: dim, no animation
            stopPulseAnimation();
            statusViewImage.setImageAlpha(77);
            hideLanProxyInfo();
        }
    }

    private void updateLanProxyInfo(TunnelState.ConnectionData connectionData) {
        if (lanProxyInfoSection == null) {
            return;
        }

        if (!connectionData.isLanSharingEnabled()) {
            hideLanProxyInfo();
            return;
        }

        Context context = getContext();
        if (context == null) {
            hideLanProxyInfo();
            return;
        }

        String lanIp = getLanIpAddress(context);
        if (lanIp == null) {
            hideLanProxyInfo();
            return;
        }

        int httpPort = connectionData.httpPort();
        int socksPort = connectionData.socksPort();

        if (httpPort <= 0 && socksPort <= 0) {
            hideLanProxyInfo();
            return;
        }

        lanProxyInfoSection.setVisibility(View.VISIBLE);

        if (httpPort > 0) {
            lanProxyHttpText.setText(getString(R.string.lan_proxy_http_address, lanIp, httpPort));
            lanProxyHttpText.setVisibility(View.VISIBLE);
        } else {
            lanProxyHttpText.setVisibility(View.GONE);
        }

        if (socksPort > 0) {
            lanProxySocksText.setText(getString(R.string.lan_proxy_socks_address, lanIp, socksPort));
            lanProxySocksText.setVisibility(View.VISIBLE);
        } else {
            lanProxySocksText.setVisibility(View.GONE);
        }
    }

    private void hideLanProxyInfo() {
        if (lanProxyInfoSection != null) {
            lanProxyInfoSection.setVisibility(View.GONE);
        }
    }

    /**
     * Get the device's LAN IPv4 address using a multi-strategy approach.
     * This must work reliably even when our own VPN is active — the VPN
     * obscures the real Wi-Fi/Ethernet IP in several Android APIs.
     *
     * Strategy 1 (API 23+): ConnectivityManager — iterate ALL networks
     *   looking for TRANSPORT_WIFI or TRANSPORT_ETHERNET (skip VPN/cellular),
     *   then read LinkProperties for a site-local IPv4 address.
     *
     * Strategy 2 (all levels): NetworkInterface enumeration — walk every
     *   interface, skip known non-LAN names (tun, ppp, rmnet, lo, dummy,
     *   p2p, wigig), and return the first site-local IPv4 address.
     *
     * Strategy 3 (all levels, deprecated but functional through API 34+):
     *   WifiManager.getConnectionInfo().getIpAddress() — returns the raw
     *   Wi-Fi IPv4 even with VPN active because it reads from the Wi-Fi
     *   HAL directly, not from the routing table.
     *
     * Every strategy is tried in order; the first non-null result wins.
     */
    private static String getLanIpAddress(Context context) {
        // Strategy 1: ConnectivityManager + LinkProperties (most authoritative)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String ip = getLanIpFromConnectivityManager(context);
            if (ip != null) return ip;
        }

        // Strategy 2: NetworkInterface enumeration
        String ip = getLanIpFromNetworkInterfaces();
        if (ip != null) return ip;

        // Strategy 3: WifiManager — deprecated but still functional on all
        // API levels through at least API 34. This is our most reliable
        // fallback because it reads the Wi-Fi IP directly from the HAL,
        // bypassing any VPN routing interference.
        return getLanIpFromWifiManager(context);
    }

    /**
     * Strategy 1: Walk all networks via ConnectivityManager looking for a
     * Wi-Fi or Ethernet transport that is NOT a VPN. When our VPN is active,
     * getActiveNetwork() returns the VPN network, so we must iterate
     * getAllNetworks() to find the underlying physical network.
     */
    @android.annotation.TargetApi(Build.VERSION_CODES.M)
    private static String getLanIpFromConnectivityManager(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;

        try {
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                if (capabilities == null) continue;

                // Skip VPN and cellular transports — we only want the
                // physical LAN network (Wi-Fi or Ethernet).
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    continue;
                }

                LinkProperties linkProperties = cm.getLinkProperties(network);
                if (linkProperties == null) continue;
                for (android.net.LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                    InetAddress address = linkAddress.getAddress();
                    if (address instanceof Inet4Address &&
                            !address.isLoopbackAddress() &&
                            address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // SecurityException possible on some OEMs; fall through to next strategy
        }
        return null;
    }

    /**
     * Strategy 2: Enumerate all NetworkInterfaces and return the first
     * site-local IPv4 address on a physical-looking interface. We skip
     * known virtual/tunnel interface name prefixes.
     */
    private static String getLanIpFromNetworkInterfaces() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return null;

            // Collect candidates — prefer wlan/eth over others
            String otherCandidate = null;

            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                String name = ni.getName().toLowerCase();

                // Skip known non-LAN interfaces
                if (name.startsWith("tun") || name.startsWith("ppp") ||
                        name.startsWith("rmnet") || name.startsWith("lo") ||
                        name.startsWith("dummy") || name.startsWith("p2p") ||
                        name.startsWith("wigig") || name.startsWith("v4-") ||
                        name.startsWith("clat")) {
                    continue;
                }

                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address &&
                            !addr.isLoopbackAddress() &&
                            addr.isSiteLocalAddress()) {
                        String ip = addr.getHostAddress();
                        if (name.startsWith("wlan") || name.startsWith("eth") ||
                                name.startsWith("en")) {
                            // High-confidence LAN interface — return immediately
                            return ip;
                        }
                        if (otherCandidate == null) {
                            otherCandidate = ip;
                        }
                    }
                }
            }

            // Return best candidate found
            return otherCandidate;
        } catch (SocketException ignored) {}
        return null;
    }

    /**
     * Strategy 3: WifiManager.getConnectionInfo().getIpAddress() reads the
     * IPv4 address directly from the Wi-Fi HAL, so it works correctly even
     * when a VPN is the active network. The API is deprecated since API 31
     * but remains functional and returns correct values through at least
     * Android 14 (API 34). This is the most reliable single-call fallback.
     */
    @SuppressWarnings("deprecation")
    private static String getLanIpFromWifiManager(Context context) {
        try {
            WifiManager wm = (WifiManager)
                    context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return null;
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return null;
            int ipInt = info.getIpAddress();
            if (ipInt == 0) return null;
            return String.format("%d.%d.%d.%d",
                    (ipInt & 0xff), (ipInt >> 8 & 0xff),
                    (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
        } catch (Exception ignored) {
            // SecurityException on some OEMs without ACCESS_WIFI_STATE
            return null;
        }
    }

    private void startPulseAnimation() {
        if (pulseAnimator != null && pulseAnimator.isRunning()) {
            return; // Already pulsing
        }
        pulseAnimator = ObjectAnimator.ofInt(statusViewImage, "imageAlpha", 77, 255);
        pulseAnimator.setDuration(1000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.start();
    }

    private void stopPulseAnimation() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
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
            // Disable all file:// URLs
            webSettings.setAllowFileAccess(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                webSettings.setAllowFileAccessFromFileURLs(false);
                webSettings.setAllowUniversalAccessFromFileURLs(false);
            }
            // Disable all content:// URLs
            webSettings.setAllowContentAccess(false);
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
