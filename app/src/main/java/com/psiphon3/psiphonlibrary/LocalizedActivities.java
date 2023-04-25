package com.psiphon3.psiphonlibrary;

import static android.content.pm.PackageManager.GET_META_DATA;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Menu;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.psiphon3.R;
import com.psiphon3.log.MyLog;

// Activities that inherit from these activities will correctly handle locale changes
public abstract class LocalizedActivities {
    public static abstract class AppCompatActivity extends androidx.appcompat.app.AppCompatActivity {
        private static final int REQUEST_CODE_PREPARE_VPN = 100;

        private TunnelServiceInteractor tunnelServiceInteractor;
        private StartServiceListener startServiceListener = null;

        public TunnelServiceInteractor getTunnelServiceInteractor() {
            return tunnelServiceInteractor;
        }

        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            return super.onCreateOptionsMenu(menu);
        }

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            tunnelServiceInteractor = new TunnelServiceInteractor(this, true);
            Utils.resetActivityTitle(this);
        }

        @Override
        protected void onPause() {
            super.onPause();
            tunnelServiceInteractor.onStop(this);
        }

        @Override
        protected void onResume() {
            super.onResume();
            tunnelServiceInteractor.onStart(this);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            tunnelServiceInteractor.onDestroy(this);
        }

        @Override
        protected void attachBaseContext(Context newBase) {
            LocaleManager localeManager = LocaleManager.getInstance(newBase);
            super.attachBaseContext(localeManager.setLocale(newBase));
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            if (requestCode == REQUEST_CODE_PREPARE_VPN) {
                if (resultCode == RESULT_OK) {
                    getTunnelServiceInteractor().startTunnelService(this);
                    if (startServiceListener != null) {
                        startServiceListener.onServiceStartOk();
                    }
                } else if (resultCode == RESULT_CANCELED) {
                    showVpnAlertDialog(R.string.StatusActivity_VpnPromptCancelledTitle,
                            R.string.StatusActivity_VpnPromptCancelledMessage);
                    if (startServiceListener != null) {
                        startServiceListener.onServiceStartCancelled();
                    }
                }
                startServiceListener = null;
            }
            super.onActivityResult(requestCode, resultCode, data);
        }

        protected void showVpnAlertDialog(int titleId, int messageId) {
            new AlertDialog.Builder(this)
                    .setCancelable(true)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(titleId)
                    .setMessage(messageId)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }

        public void startTunnel() {
            startTunnel(null);
        }

        public void startTunnel(@Nullable StartServiceListener listener) {
            startServiceListener = listener;
            try {
                Intent intent = VpnService.prepare(this);
                if (intent != null) {
                    // start service will be called in onActivityResult
                    startActivityForResult(intent, REQUEST_CODE_PREPARE_VPN);
                } else {
                    onActivityResult(REQUEST_CODE_PREPARE_VPN, RESULT_OK, null);
                }
            } catch (Exception e) {
                MyLog.e(R.string.tunnel_whole_device_exception, MyLog.Sensitivity.NOT_SENSITIVE);
            }
        }
    }

    private static class Utils {
        static void resetActivityTitle(android.app.Activity activity) {
            try {
                ActivityInfo info = activity.getPackageManager().getActivityInfo(activity.getComponentName(), GET_META_DATA);
                if (info.labelRes != 0) {
                    activity.setTitle(info.labelRes);
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public interface StartServiceListener {
        void onServiceStartOk();

        void onServiceStartCancelled();
    }
}
