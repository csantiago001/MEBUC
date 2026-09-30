package co.mebuc.pin;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "IaPolicia")
public class IaPoliciaPlugin extends Plugin {

    private static final String URL = "https://app.ia.policia.gov.co/login";
    private static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36";
    private static final int DESKTOP_WIDTH = 1280;
    private static final int ESCALA_INICIAL = 45;
    static final int PERMISOS = 1001;

    static IaPoliciaPlugin instancia;

    private FrameLayout contenedor;
    private WebView web;
    private PermissionRequest solicitudPendiente;

    @Override
    public void load() {
        instancia = this;
    }

    @PluginMethod
    public void mostrar(PluginCall call) {
        final float tabbarTop = call.getFloat("tabbarTop", 0f);
        getActivity().runOnUiThread(() -> {
            try {
                crearSiHaceFalta();
                ubicar(tabbarTop);
                contenedor.setVisibility(View.VISIBLE);
                contenedor.bringToFront();
                web.onResume();
                call.resolve();
            } catch (Exception e) {
                call.reject(e.getMessage());
            }
        });
    }

    @PluginMethod
    public void ocultar(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (contenedor != null) {
                contenedor.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void reubicar(PluginCall call) {
        final float tabbarTop = call.getFloat("tabbarTop", 0f);
        getActivity().runOnUiThread(() -> {
            if (contenedor != null) ubicar(tabbarTop);
            call.resolve();
        });
    }

    private void ubicar(float tabbarTopCss) {
        WebView cap = getBridge().getWebView();
        float d = getContext().getResources().getDisplayMetrics().density;

        int margenSuperior = 0;
        WindowInsetsCompat wi = ViewCompat.getRootWindowInsets(cap);
        if (wi != null) {
            Insets barras = wi.getInsets(WindowInsetsCompat.Type.systemBars());
            int[] pos = new int[2];
            cap.getLocationInWindow(pos);
            margenSuperior = Math.max(0, barras.top - pos[1]);
        }

        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) contenedor.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = Math.max(0, Math.round(tabbarTopCss * d));
        lp.topMargin = cap.getTop();
        contenedor.setLayoutParams(lp);
        contenedor.setPadding(0, margenSuperior, 0, 0);
    }

    private void crearSiHaceFalta() {
        if (contenedor != null) return;

        WebView cap = getBridge().getWebView();
        ViewGroup padre = (ViewGroup) cap.getParent();

        contenedor = new FrameLayout(getActivity());
        contenedor.setBackgroundColor(Color.parseColor("#1B2F6B"));
        contenedor.setVisibility(View.GONE);

        web = new WebView(getActivity());
        contenedor.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        padre.addView(contenedor, lp);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(DESKTOP_USER_AGENT);
        web.setInitialScale(ESCALA_INICIAL);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                v.evaluateJavascript(
                        "try{" +
                        "Object.defineProperty(screen,'width',{get:function(){return " + DESKTOP_WIDTH + ";}});" +
                        "Object.defineProperty(screen,'height',{get:function(){return 800;}});" +
                        "}catch(e){}", null);
                forzarEscritorio(v);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                forzarEscritorio(v);
            }

            @Override
            public void doUpdateVisitedHistory(WebView v, String url, boolean isReload) {
                forzarEscritorio(v);
                v.postDelayed(() -> forzarEscritorio(v), 300);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                getActivity().runOnUiThread(() -> {
                    if (!tienePermisos()) {
                        solicitudPendiente = request;
                        ActivityCompat.requestPermissions(getActivity(), new String[]{
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO}, PERMISOS);
                        return;
                    }
                    request.grant(request.getResources());
                });
            }
        });

        web.loadUrl(URL);
    }

    private boolean tienePermisos() {
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    void resultadoPermisos(int requestCode, int[] resultados) {
        if (requestCode != PERMISOS || solicitudPendiente == null) return;
        boolean todos = resultados.length > 0;
        for (int r : resultados) if (r != PackageManager.PERMISSION_GRANTED) todos = false;
        if (todos) solicitudPendiente.grant(solicitudPendiente.getResources());
        else solicitudPendiente.deny();
        solicitudPendiente = null;
    }

    boolean manejarAtras() {
        if (contenedor == null || contenedor.getVisibility() != View.VISIBLE) return false;
        if (web.canGoBack()) {
            web.goBack();
        } else {
            notifyListeners("salir", new JSObject());
        }
        return true;
    }

    void pausar() {
        CookieManager.getInstance().flush();
    }

    private void forzarEscritorio(WebView v) {
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
}
