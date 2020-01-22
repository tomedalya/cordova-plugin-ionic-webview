package com.myApp.webview;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import com.ionicframework.cordova.webview.IonicWebViewEngine;
import com.ionicframework.cordova.webview.WebViewLocalServer;

import org.apache.cordova.ConfigXmlParser;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPreferences;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaWebViewEngine;
import org.apache.cordova.NativeToJsMessageQueue;
import org.apache.cordova.PluginManager;
import org.apache.cordova.engine.SystemWebViewClient;
import org.apache.cordova.engine.SystemWebViewEngine;

import java.lang.reflect.Field;

public class MyWebViewEngine extends IonicWebViewEngine {

    public static final String TAG = "IonicWebViewEngine";

    private WebViewLocalServer localServer;
    private String CDV_LOCAL_SERVER;

    /**
     * Used when created via reflection.
     */
    public MyWebViewEngine(Context context, CordovaPreferences preferences) {
        super(context, preferences);
    }

    @Override
    public void init(CordovaWebView parentWebView, CordovaInterface cordova, final CordovaWebViewEngine.Client client,
                     CordovaResourceApi resourceApi, PluginManager pluginManager,
                     NativeToJsMessageQueue nativeToJsMessageQueue) {

        super.init(parentWebView, cordova, client, resourceApi, pluginManager, nativeToJsMessageQueue);

        ConfigXmlParser parser = new ConfigXmlParser();
        parser.parse(cordova.getActivity());

        String hostname = preferences.getString("Hostname", "localhost");
        String scheme = preferences.getString("Scheme", "http");
        CDV_LOCAL_SERVER = scheme + "://" + hostname;

        try {
            Field f = this.getClass().getSuperclass().getDeclaredField("localServer");
            f.setAccessible(true);
            localServer = (WebViewLocalServer) f.get(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create my webview engine. ", e);
        }
        webView.setWebViewClient(new MyWebViewClient(this, parser));
    }

    public class MyWebViewClient extends SystemWebViewClient {

        private ConfigXmlParser parser;

        public MyWebViewClient(SystemWebViewEngine parentEngine, ConfigXmlParser parser) {
            super(parentEngine);
            this.parser = parser;
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return localServer.shouldInterceptRequest(request.getUrl(), request);
        }

        @TargetApi(Build.VERSION_CODES.KITKAT)
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return localServer.shouldInterceptRequest(Uri.parse(url), null);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            String launchUrl = parser.getLaunchUrl();
            if (!launchUrl.contains(WebViewLocalServer.httpsScheme) && !launchUrl.contains(WebViewLocalServer.httpScheme) && url.equals(launchUrl)) {
                view.stopLoading();
                // When using a custom scheme the app won't load if server start url doesn't end in /
                String startUrl = CDV_LOCAL_SERVER;
                if (!CDV_LOCAL_SERVER.startsWith(WebViewLocalServer.httpsScheme) && !CDV_LOCAL_SERVER.startsWith(WebViewLocalServer.httpScheme)) {
                    startUrl += "/";
                }
                view.loadUrl(startUrl);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            view.loadUrl("javascript:(function() { " +
                    "window.WEBVIEW_SERVER_URL = '" + CDV_LOCAL_SERVER + "';" +
                    "})()");
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            // after calling super method we could be able to implement our own logic.
            super.onReceivedSslError(view, handler, error);
            Context ctx = view.getContext();
            final String packageName = ctx.getPackageName();
            final PackageManager pm = ctx.getPackageManager();
            ApplicationInfo appInfo;
            try {
                appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
                if ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
                    // debug = false
                    getCordovaWebView().postMessage("onReceivedSslError", error.getPrimaryError());
                }
            } catch (PackageManager.NameNotFoundException e) {
                // When it doubt, lock it out!
                getCordovaWebView().postMessage("onReceivedSslError", error.getPrimaryError());
            }
        }
    }
}
