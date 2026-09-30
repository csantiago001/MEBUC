package co.mebuc.pin;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Pestaña "IA Policía": abre app.ia.policia.gov.co en modo escritorio,
 * con cámara y micrófono. Es el mismo comportamiento de la app IA Policía original.
 */
public class IaPoliciaActivity extends Activity {

    private static final String URL = "https://app.ia.policia.gov.co/login";

    private static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36";

    // Ancho virtual de escritorio (px)
    private static final int DESKTOP_WIDTH = 1280;
    private static final int PERMISOS = 1001;

    private WebView webView;
    private PermissionRequest solicitudPendiente;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Contenedor vertical: barra "Volver a MEBUC" + página
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1B2F6B"));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });

        LinearLayout barra = new LinearLayout(this);
        barra.setOrientation(LinearLayout.HORIZONTAL);
        barra.setGravity(Gravity.CENTER_VERTICAL);
        barra.setPadding(dp(8), 0, dp(12), 0);

        TextView volver = new TextView(this);
        volver.setText("‹  MEBUC");
        volver.setTextColor(Color.parseColor("#F9A825"));
        volver.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        volver.setTypeface(Typeface.DEFAULT_BOLD);
        volver.setPadding(dp(8), dp(8), dp(16), dp(8));
        volver.setOnClickListener(v -> finish());

        TextView titulo = new TextView(this);
        titulo.setText("IA Policía");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);

        barra.addView(volver);
        barra.addView(titulo);
        root.addView(barra, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Simula Chrome en un computador.
        settings.setUserAgentString(DESKTOP_USER_AGENT);

        // Escala inicial. Si se corta a los lados prueba 40; si se ve chico, 50.
        webView.setInitialScale(45);

        // Cookies necesarias para iniciar sesión (se conservan entre aperturas).
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                v.evaluateJavascript(
                        "try{" +
                        "Object.defineProperty(screen,'width',{get:function(){return " + DESKTOP_WIDTH + ";}});" +
                        "Object.defineProperty(screen,'height',{get:function(){return 800;}});" +
                        "}catch(e){}", null);
                forceDesktop(v);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                forceDesktop(v);
            }

            @Override
            public void doUpdateVisitedHistory(WebView v, String url, boolean isReload) {
                // Se dispara también cuando la SPA cambia de sección sin recargar.
                forceDesktop(v);
                v.postDelayed(() -> forceDesktop(v), 300);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= 23 && !tienePermisos()) {
                        // Se pide el permiso y se responde a la página cuando el usuario conteste
                        solicitudPendiente = request;
                        requestPermissions(new String[]{
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO}, PERMISOS);
                        return;
                    }
                    request.grant(request.getResources());
                });
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(URL);
        }
    }

    private boolean tienePermisos() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISOS || solicitudPendiente == null) return;
        boolean todos = grantResults.length > 0;
        for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) todos = false;
        if (todos) solicitudPendiente.grant(solicitudPendiente.getResources());
        else solicitudPendiente.deny();
        solicitudPendiente = null;
    }

    // Vuelve a forzar el viewport de escritorio cada vez que la página lo cambie.
    private void forceDesktop(WebView v) {
        String js =
                "(function(){" +
                "function fix(){" +
                "var m=document.querySelector('meta[name=viewport]');" +
                "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                "if(m.getAttribute('content')!=='width=" + DESKTOP_WIDTH + ", user-scalable=yes'){" +
                "m.setAttribute('content','width=" + DESKTOP_WIDTH + ", user-scalable=yes');}" +
                "}" +
                "fix();" +
                "if(!window.__deskObs){" +
                "window.__deskObs=new MutationObserver(fix);" +
                "window.__deskObs.observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['content']});" +
                "setInterval(fix,500);" +
                "}" +
                "})();";
        v.evaluateJavascript(js, null);
    }

    private int dp(int valor) {
        return (int) (valor * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        // Guarda la sesión para no tener que iniciar sesión cada vez
        CookieManager.getInstance().flush();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed(); // vuelve a MEBUC
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
