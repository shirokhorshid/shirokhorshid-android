/*
 * Copyright (c) 2013, Psiphon Inc.
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

package com.psiphon3.psiphonlibrary;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Proxy;
import android.os.Build;
import android.os.Parcelable;
import android.util.ArrayMap;

import com.psiphon3.psiphonlibrary.Utils.MyLog;

import org.apache.http.HttpHost;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class WebViewProxySettings
{
    private static boolean mIsLocalProxySet = false;
    private static boolean mIsInitialized = false;
    private static List<String> mReceiversList;

    public static boolean isLocalProxySet() {return mIsLocalProxySet;}


    private static List<Object> getCurrentReceiversSet(Context ctx) {
        Context appContext = ctx.getApplicationContext();
        List<Object> receiversList = new ArrayList();

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return receiversList;
        }

        try {
            Class applicationClass = Class.forName("android.app.Application");
            Field mLoadedApkField = applicationClass.getDeclaredField("mLoadedApk");
            mLoadedApkField.setAccessible(true);
            Object mloadedApk = mLoadedApkField.get(appContext);
            Class loadedApkClass = Class.forName("android.app.LoadedApk");
            Field mReceiversField = loadedApkClass.getDeclaredField("mReceivers");
            mReceiversField.setAccessible(true);
            ArrayMap receivers = (ArrayMap) mReceiversField.get(mloadedApk);
            for (Object receiverMap : receivers.values()) {
                for (Object receiver : ((ArrayMap) receiverMap).keySet()) {
                    if (receiver == null) {
                        continue;
                    }
                    receiversList.add(receiver);
                }
            }
        } catch (ClassNotFoundException e) {
            MyLog.d("Exception initializing WebViewProxySettings: " + e.toString());
        } catch (NoSuchFieldException e) {
            MyLog.d("Exception initializing WebViewProxySettings: " + e.toString());
        } catch (IllegalAccessException e) {
            MyLog.d("Exception initializing WebViewProxySettings: " + e.toString());
        }
        finally {
            return receiversList;
        }
    }

    // Must call once early in the application lifecycle, e.g. in Activity.onResume() or in
    // Activity.onStart(), before dynamic module loader has a chance to load intent receivers that
    // may cause the app to close if an unexpected intent is received when setWebkitProxyLollipop
    // is called.
    public static void initialize(Context ctx) {
        if(mIsInitialized) {
            return;
        }

        mIsInitialized = true;
        mReceiversList = new ArrayList<>();

        for (Object receiver : getCurrentReceiversSet(ctx)) {
            mReceiversList.add(receiver.getClass().getName());
        }

    }

    public static void resetLocalProxy(Context ctx)
    {
        UpstreamProxySettings.ProxySettings systemProxySettings = UpstreamProxySettings.getOriginalSystemProxySettings(ctx);
        if (systemProxySettings == null) {
            systemProxySettings = new UpstreamProxySettings.ProxySettings();
        }
        setProxy(ctx, systemProxySettings.proxyHost, systemProxySettings.proxyPort);
        mIsLocalProxySet = false;
    }

    private static boolean proxySettingsAreEmpty(String host, int port)
    {
        return (host == null ||
                host.length() == 0 ||
                port <= 0);
    }

    public static void setLocalProxy(Context ctx, int port)
    {
        setProxy(ctx, "localhost", port);
        mIsLocalProxySet = true;
    }

    /*
    Proxy setting code taken directly from Orweb, with some modifications.
    (...And some of the Orweb code was taken from an earlier version of our code.)
    See: https://github.com/guardianproject/Orweb/blob/master/src/org/torproject/android/OrbotHelper.java#L39
    Note that we tried and abandoned doing feature detection by trying the
    newer (>= ICS) proxy setting, catching, and then failing over to the older
    approach. The problem was that on Android 3.0, an exception would be thrown
    *in another thread*, so we couldn't catch it and the whole app would force-close.
    Orweb has always been doing an explicit version check, and it seems to work,
    so we're so going to switch to that approach.
    */
    public static boolean setProxy (Context ctx, String host, int port)
    {
        if (!mIsInitialized) {
            throw new AssertionError("Assertion error: WebViewProxySettings is not initialized!");
        }

        UpstreamProxySettings.saveSystemProxySettings(ctx);

        boolean worked = false;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        {
            worked = setWebkitProxyGingerbread(ctx, host, port);
        }
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
        {
            worked = setWebkitProxyICS(ctx, host, port);
        }
        else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT)
        {
            worked = setWebkitProxyKitKat(ctx.getApplicationContext(), host, port);
        }
        else
        {
            worked = setWebkitProxyLollipop(ctx.getApplicationContext(), host, port);
        }

        return worked;
    }

    private static boolean setWebkitProxyGingerbread(Context ctx, String host, int port)
    {
        try
        {
            Object requestQueueObject = getRequestQueue(ctx);
            if (requestQueueObject != null) {
                //Create Proxy config object and set it into request Q
                HttpHost httpHost = null;
                if (!proxySettingsAreEmpty(host, port)){
                    httpHost = new HttpHost(host, port, "http");
                }
                setDeclaredField(requestQueueObject, "mProxyHost", httpHost);

                return true;
            }
        }
        catch (Throwable e)
        {
            // Failed. Fall through to false return.
        }

        return false;
    }

    @SuppressWarnings("rawtypes")
    private static boolean setWebkitProxyICS(Context ctx, String host, int port)
    {
        try
        {
            Class webViewCoreClass = Class.forName("android.webkit.WebViewCore");

            Class proxyPropertiesClass = Class.forName("android.net.ProxyProperties");
            if (webViewCoreClass != null && proxyPropertiesClass != null)
            {
                Method m = webViewCoreClass.getDeclaredMethod("sendStaticMessage", Integer.TYPE, Object.class);
                if (proxySettingsAreEmpty(host, port))
                {
                    if (m != null)
                    {
                        m.setAccessible(true);

                        // android.webkit.WebViewCore.EventHub.PROXY_CHANGED = 193;
                        m.invoke(null, 193, null);
                        return true;
                    }
                }
                else
                {
                    Constructor c = proxyPropertiesClass.getConstructor(String.class, Integer.TYPE, String.class);

                    if (m != null && c != null)
                    {
                        m.setAccessible(true);
                        c.setAccessible(true);
                        Object properties = c.newInstance(host, port, null);

                        // android.webkit.WebViewCore.EventHub.PROXY_CHANGED = 193;
                        m.invoke(null, 193, properties);
                        return true;
                    }
                }
            }
        }
        catch (Exception e)
        {
            MyLog.d("Exception setting WebKit proxy through android.webkit.Network: " + e.toString());
        }
        catch (Error e)
        {
            MyLog.d("Exception setting WebKit proxy through android.webkit.Network: " + e.toString());
        }

        return false;
    }

    // http://stackoverflow.com/questions/19979578/android-webview-set-proxy-programatically-kitkat
    // http://src.chromium.org/viewvc/chrome/trunk/src/net/android/java/src/org/chromium/net/ProxyChangeListener.java
    @TargetApi(Build.VERSION_CODES.KITKAT)
    @SuppressWarnings("rawtypes")
    private static boolean setWebkitProxyKitKat(Context appContext, String host, int port)
    {
        if (proxySettingsAreEmpty(host, port))
        {
            System.clearProperty("http.proxyHost");
            System.clearProperty("http.proxyPort");
            System.clearProperty("https.proxyHost");
            System.clearProperty("https.proxyPort");
        }
        else
        {
            System.setProperty("http.proxyHost", host);
            System.setProperty("http.proxyPort", port + "");
            System.setProperty("https.proxyHost", host);
            System.setProperty("https.proxyPort", port + "");
        }
        try
        {
            for (Object receiver : getCurrentReceiversSet(appContext))
            {
                Class receiverClass = receiver.getClass();
                if (receiverClass.getName().contains("ProxyChangeListener"))
                {
                    Method onReceiveMethod = receiverClass.getDeclaredMethod("onReceive", Context.class, Intent.class);
                    Intent intent = new Intent(Proxy.PROXY_CHANGE_ACTION);

                    final String CLASS_NAME = "android.net.ProxyProperties";
                    Class proxyPropertiesClass = Class.forName(CLASS_NAME);
                    Constructor constructor = proxyPropertiesClass.getConstructor(String.class, Integer.TYPE, String.class);
                    constructor.setAccessible(true);
                    Object proxyProperties = constructor.newInstance(host, port, null);
                    intent.putExtra("proxy", (Parcelable) proxyProperties);

                    onReceiveMethod.invoke(receiver, appContext, intent);
                }
            }
            return true;
        }
        catch (ClassNotFoundException e)
        {
            MyLog.d("Exception setting WebKit proxy on KitKat through ProxyChangeListener: " + e.toString());
        }
        catch (IllegalAccessException e)
        {
            MyLog.d("Exception setting WebKit proxy on KitKat through ProxyChangeListener: " + e.toString());
        }
        catch (IllegalArgumentException e)
        {
            MyLog.d("Exception setting WebKit proxy on KitKat through ProxyChangeListener: " + e.toString());
        }
        catch (NoSuchMethodException e)
        {
            MyLog.d("Exception setting WebKit proxy on KitKat through ProxyChangeListener: " + e.toString());
        }
        catch (InvocationTargetException e)
        {
            MyLog.d("Exception setting WebKit proxy on KitKat through ProxyChangeListener: " + e.toString());
        }
        catch (InstantiationException e)
        {
            MyLog.d("Exception setting WebKit proxy on KitKat through ProxyChangeListener: " + e.toString());
        }
        return false;
    }

    // http://stackanswers.com/questions/25272393/android-webview-set-proxy-programmatically-on-android-l
    @TargetApi(Build.VERSION_CODES.KITKAT) // for android.util.ArrayMap methods
    @SuppressWarnings("rawtypes")
    private static boolean setWebkitProxyLollipop(Context appContext, String host, int port)
    {
        if (proxySettingsAreEmpty(host, port))
        {
            System.clearProperty("http.proxyHost");
            System.clearProperty("http.proxyPort");
            System.clearProperty("https.proxyHost");
            System.clearProperty("https.proxyPort");
        }
        else
        {
            System.setProperty("http.proxyHost", host);
            System.setProperty("http.proxyPort", port + "");
            System.setProperty("https.proxyHost", host);
            System.setProperty("https.proxyPort", port + "");
        }
        try {
            for (Object receiver : getCurrentReceiversSet(appContext))
            {
                Class clazz = receiver.getClass();

                // Check if receiver class name is in the list of
                // receivers names we stored during initialization
                if (!mReceiversList.contains(clazz.getName())) {
                    continue;
                }

                // NOTE: as of Chrome 67 the ProxyChangeListener now has an obfuscated name,
                // so we are unable to identify the receiver by name. Instead we'll send the
                // PROXY_CHANGE intent to all receivers.
                Method onReceiveMethod = clazz.getDeclaredMethod("onReceive", Context.class, Intent.class);
                Intent intent = new Intent(Proxy.PROXY_CHANGE_ACTION);

                final String CLASS_NAME = "android.net.ProxyInfo";
                Class proxyInfoClass = Class.forName(CLASS_NAME);
                Constructor constructor = proxyInfoClass.getConstructor(String.class, Integer.TYPE, String.class);
                constructor.setAccessible(true);
                Object proxyInfo = constructor.newInstance(host, port, null);
                intent.putExtra("android.intent.extra.PROXY_INFO", (Parcelable) proxyInfo);

                try {
                    onReceiveMethod.invoke(receiver, appContext, intent);
                } catch (InvocationTargetException e) {
                    // This receiver may throw on an unexpected intent, continue to the next one
                }
            }
            return true;
        }
        catch (ClassNotFoundException e)
        {
            MyLog.d("Exception setting WebKit proxy on Lollipop through ProxyChangeListener: " + e.toString());
        }
        catch (IllegalAccessException e)
        {
            MyLog.d("Exception setting WebKit proxy on Lollipop through ProxyChangeListener: " + e.toString());
        }
        catch (NoSuchMethodException e)
        {
            MyLog.d("Exception setting WebKit proxy on Lollipop through ProxyChangeListener: " + e.toString());
        }
        catch (InvocationTargetException e)
        {
            MyLog.d("Exception setting WebKit proxy on Lollipop through ProxyChangeListener: " + e.toString());
        }
        catch (InstantiationException e)
        {
            MyLog.d("Exception setting WebKit proxy on Lollipop through ProxyChangeListener: " + e.toString());
        }
        return false;
    }

    @SuppressWarnings("rawtypes")
    private static Object GetNetworkInstance(Context ctx) throws ClassNotFoundException
    {
        Class networkClass = Class.forName("android.webkit.Network");
        return networkClass;
    }

    private static Object getRequestQueue(Context ctx) throws Exception
    {
        Object ret = null;
        Object networkClass = GetNetworkInstance(ctx);
        if (networkClass != null)
        {
            Object networkObj = invokeMethod(networkClass, "getInstance", new Object[]{ctx}, Context.class);
            if (networkObj != null)
            {
                ret = getDeclaredField(networkObj, "mRequestQueue");
            }
        }
        return ret;
    }

    private static Object getDeclaredField(Object obj, String name)
            throws SecurityException, NoSuchFieldException,
            IllegalArgumentException, IllegalAccessException
    {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object out = f.get(obj);
        return out;
    }

    private static void setDeclaredField(Object obj, String name, Object value)
            throws SecurityException, NoSuchFieldException,
            IllegalArgumentException, IllegalAccessException
    {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    @SuppressWarnings("rawtypes")
    private static Object invokeMethod(Object object, String methodName, Object[] params, Class... types) throws Exception
    {
        Object out = null;
        Class c = object instanceof Class ? (Class) object : object.getClass();

        if (types != null)
        {
            Method method = c.getMethod(methodName, types);
            out = method.invoke(object, params);
        }
        else
        {
            Method method = c.getMethod(methodName);
            out = method.invoke(object);
        }
        return out;
    }
}
