package org.zirco.utils;

import android.content.Context;

import com.psiphon3.R;
import com.psiphon3.psiphonlibrary.WebViewProxySettings;

import net.grandcentrix.tray.AppPreferences;


/**
 * Utility class for setting WebKit proxy used by Android WebView
 *
 */
public class ProxySettings 
{
	public static void setLocalProxy(Context ctx)
	{
		final AppPreferences multiProcessPreferences = new AppPreferences(ctx);
		WebViewProxySettings.initialize(ctx);
		WebViewProxySettings.setLocalProxy(ctx,
				multiProcessPreferences.getInt(ctx.getString(R.string.current_local_http_proxy_port), 0));
	}
}
