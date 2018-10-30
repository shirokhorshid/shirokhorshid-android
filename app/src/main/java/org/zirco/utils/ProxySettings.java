package org.zirco.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.psiphon3.R;
import com.psiphon3.psiphonlibrary.WebViewProxySettings;

import net.grandcentrix.tray.AppPreferences;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;


/**
 * Utility class for setting WebKit proxy used by Android WebView
 *
 */
public class ProxySettings 
{
	private static ContentResolver _contentResolver;
	private static String _proxyString;
	private static String _proxyAddress;
	private static Integer _proxyPort;
	private static Boolean _proxyStatus = null;
	
	private static String getSystemProxyAddress(Context ctx)
    {
    	if (isSystemProxyValid(ctx))
    	{
    		_proxyAddress = _proxyString.split(":")[0];
    		return _proxyAddress;
    	}
    	else
    		return null;
    }

    private static Integer getSystemProxyPort(Context ctx)
    {
    	if (isSystemProxyValid(ctx))
    	{
    		_proxyPort = Integer.parseInt(_proxyString.split(":")[1]);
    		return _proxyPort;
    	}
    	else
    		return null;
    }
    
    private static boolean isSystemProxyValid(Context ctx)
    {
    	_contentResolver = ctx.getContentResolver();
		_proxyString = Settings.Secure.getString(_contentResolver,Settings.Secure.HTTP_PROXY);
		if (_proxyString != null && _proxyString != "" && _proxyString.contains(":"))
		{
			return true;
		}
		else
		{
			return false;
		}
    }
    
    private static boolean isSystemProxyReachable(Context ctx)
    {
    	int exitValue;
    	Runtime runtime = Runtime.getRuntime();
        Process proc;
		
        try 
		{
			proc = runtime.exec("ping -c 1   " + getSystemProxyAddress(ctx));
			proc.waitFor();
			exitValue = proc.exitValue();
			
			Log.d("ProxySettings","Ping exit value: "+ exitValue);
			
			if (exitValue == 0)
				return true;
			else
				return false;
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		}

		return false;   
    }
    
    private static boolean isInternetReachable(Context ctx)
    {
		DefaultHttpClient httpclient = new DefaultHttpClient();		
        HttpHost proxy = new HttpHost(getSystemProxyAddress(ctx),getSystemProxyPort(ctx));
        httpclient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
		
        HttpGet request;
        HttpResponse response;
        
		try 
		{
			request = new HttpGet("http://www.google.com");
			response = httpclient.execute(request);
			
			Log.d("ProxySettings", "Is internet reachable : " + response.getStatusLine().toString());
			if (response != null && response.getStatusLine().getStatusCode() == 200)
			{
				return true;
			}
			else
				return false;
		} 
		catch (ClientProtocolException e) 
		{
			e.printStackTrace();
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		
		return false;
    }
    
	private static boolean testSystemProxy(Context ctx)
	{
		String message = "";
		
		if (!isSystemProxyValid(ctx))
		{
			_proxyStatus = false;
			message = ctx.getResources().getString(R.string.ProxySettings_ErrorProxySettingsNotValid);
		}
		else if (!isSystemProxyReachable(ctx))
		{
			_proxyStatus = false;
			message = ctx.getResources().getString(R.string.ProxySettings_ErrorProxyServerNotReachable);
		}
		else if (!isInternetReachable(ctx))
		{
			_proxyStatus = false;
			message = ctx.getResources().getString(R.string.ProxySettings_ErrorProxyInternetNotReachable);
		}
		else
		{
			_proxyStatus = true;
		}

		if (!_proxyStatus)
			Toast.makeText(ctx, message, Toast.LENGTH_LONG).show();
		
		return _proxyStatus;
	}
	
	public static void setLocalProxy(Context ctx)
	{
		final AppPreferences multiProcessPreferences = new AppPreferences(ctx);
		WebViewProxySettings.initialize(ctx);
		WebViewProxySettings.setLocalProxy(ctx,
				multiProcessPreferences.getInt(ctx.getString(R.string.current_local_http_proxy_port), 0));
	}
}
