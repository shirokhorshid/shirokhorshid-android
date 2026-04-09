package com.psiphon3.psiphonlibrary;

import android.content.Context;
import android.text.TextUtils;

import com.psiphon3.R;
import com.psiphon3.log.MyLog;

public final class TunnelNoticeStatusEmitter {

    private TunnelNoticeStatusEmitter() {
    }

    public static void maybeEmitStatusLog(Context context, String message) {
        if (context == null || TextUtils.isEmpty(message)) {
            return;
        }
        String line = TunnelNoticeSummaryBuilder.buildStatusLine(message);
        if (line == null) {
            line = TunnelNoticeSummaryBuilder.buildFallbackRawLine(message);
        }
        if (line == null) {
            return;
        }
        MyLog.i(R.string.tunnel_log_tunnel_event, MyLog.Sensitivity.NOT_SENSITIVE, line);
    }
}
