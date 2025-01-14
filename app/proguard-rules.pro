# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/android-sdk-linux/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve native methods in Tun2SocksJniLoader
-keep class ca.psiphon.Tun2SocksJniLoader {
    native <methods>;
}

# Keep the logTun2Socks method in VpnManager, as it is called from native code
-keep class com.psiphon3.VpnManager {
    public static void logTun2Socks(java.lang.String, java.lang.String, java.lang.String);
}
