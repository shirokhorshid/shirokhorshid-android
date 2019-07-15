package com.psiphon3.psiphonlibrary;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class LocaleChangeBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Get the language,
        String languageCode = intent.getStringExtra(MoreOptionsPreferenceActivity.INTENT_EXTRA_LOCALE_CHANGED_NEW_LANGUAGE_CODE);
        if (languageCode == null || languageCode.equals("")) {
            languageCode = LocaleManager.USE_SYSTEM_LANGUAGE_VAL;
        }

        // TODO: Determine a good way to tell which one to broadcast to as we can't do it to the non running
        Intent vpnIntent = new Intent(Intent.ACTION_LOCALE_CHANGED, null, context, TunnelService.class);
        vpnIntent.putExtra(MoreOptionsPreferenceActivity.INTENT_EXTRA_LOCALE_CHANGED_NEW_LANGUAGE_CODE, languageCode);
        context.startService(vpnIntent);

//        if (Utils.hasVpnService()) {
//            vpnIntent = new Intent(Intent.ACTION_LOCALE_CHANGED, null, context, TunnelVpnService.class);
//            vpnIntent.putExtra(MoreOptionsPreferenceActivity.INTENT_EXTRA_LOCALE_CHANGED_NEW_LANGUAGE_CODE, languageCode);
//            context.startService(vpnIntent);
//        }
    }
}
