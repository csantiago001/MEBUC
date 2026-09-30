package co.mebuc.pin;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Descarga la APK nueva desde GitHub y la instala sobre la versión actual.
 * JS lo usa con Capacitor.nativePromise('Actualizador', '<método>', {...}).
 */
@CapacitorPlugin(name = "Actualizador")
public class ActualizadorPlugin extends Plugin {

    static ActualizadorPlugin instancia;
    static final String CARPETA = "actualizacion";

    @Override
    public void load() {
        instancia = this;
    }

    /** Versión instalada (versionCode = número de compilación de GitHub). */
    @PluginMethod
    public void info(PluginCall call) {
        try {
            Context ctx = getContext();
            PackageInfo p = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            JSObject r = new JSObject();
            r.put("versionCode", PackageInfoCompat.getLongVersionCode(p));
            r.put("versionName", p.versionName);
            call.resolve(r);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    /** ¿El usuario ya autorizó a MEBUC para instalar apps? (Android 8+) */
    @PluginMethod
    public void puedeInstalar(PluginCall call) {
        boolean ok = Build.VERSION.SDK_INT < 26 || getContext().getPackageManager().canRequestPackageInstalls();
        JSObject r = new JSObject();
        r.put("permitido", ok);
        call.resolve(r);
    }

    /** Abre la pantalla "Instalar apps desconocidas" de MEBUC. */
    @PluginMethod
    public void abrirPermisoInstalar(PluginCall call) {
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= 26) {
                i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getContext().getPackageName()));
            } else {
                i = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    /** Pide permiso de notificaciones (Android 13+) para avisar "Toca para abrir" si el sistema no deja reabrir sola la app. */
    @PluginMethod
    public void pedirNotificaciones(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getActivity() != null &&
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7301);
        }
        call.resolve();
    }

    /** Descarga la APK (emite "progreso") y lanza la instalación. */
    @PluginMethod
    public void descargarEInstalar(PluginCall call) {
        final String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("Falta la URL de la actualización");
            return;
        }
        new Thread(() -> {
            try {
                File dir = new File(getContext().getCacheDir(), CARPETA);
                borrar(dir);
                dir.mkdirs();
                File apk = new File(dir, "mebuc.apk");
                descargar(url, apk);
                emitirEstado("instalando", 100, null);
                instalar(apk);
                call.resolve();
            } catch (Exception e) {
                emitirEstado("error", 0, e.getMessage());
                call.reject("No se pudo actualizar: " + e.getMessage());
            }
        }).start();
    }

    /** Borra restos de la versión anterior: APK descargada y caché del WebView. */
    @PluginMethod
    public void limpiar(PluginCall call) {
        borrar(new File(getContext().getCacheDir(), CARPETA));
        getActivity().runOnUiThread(() -> {
            try {
                getBridge().getWebView().clearCache(true);
            } catch (Exception ignored) { }
            call.resolve();
        });
    }

    // ---------------------------------------------------------------------

    private void descargar(String direccion, File destino) throws Exception {
        String actual = direccion;
        HttpURLConnection con = null;
        // Sigue redirecciones manualmente (GitHub redirige a otro dominio)
        for (int i = 0; i < 6; i++) {
            con = (HttpURLConnection) new URL(actual).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(20000);
            con.setReadTimeout(30000);
            con.setRequestProperty("User-Agent", "MEBUC-Actualizador");
            int code = con.getResponseCode();
            if (code >= 300 && code < 400) {
                actual = new URL(new URL(actual), con.getHeaderField("Location")).toString();
                con.disconnect();
                continue;
            }
            if (code != 200) throw new Exception("Servidor respondió " + code);
            break;
        }
        long total = con.getContentLengthLong();
        long leido = 0;
        int ultimo = -1;
        try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(destino)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                leido += n;
                if (total > 0) {
                    int pct = (int) (leido * 100 / total);
                    if (pct != ultimo) {
                        ultimo = pct;
                        emitirEstado("descargando", pct, null);
                    }
                }
            }
        } finally {
            con.disconnect();
        }
        if (total > 0 && leido != total) throw new Exception("Descarga incompleta");
    }

    private void instalar(File apk) throws Exception {
        Context ctx = getContext();
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(ctx.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) {
            // A partir de la 2ª actualización Android 12+ puede instalar sin preguntar
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = s.openWrite("mebuc.apk", 0, apk.length())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent intent = new Intent(ctx, ReceptorInstalacion.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pend = PendingIntent.getBroadcast(ctx, id, intent, flags);
            s.commit(pend.getIntentSender());
        }
    }

    void emitirEstado(String estado, int porcentaje, String mensaje) {
        JSObject d = new JSObject();
        d.put("estado", estado);
        d.put("porcentaje", porcentaje);
        if (mensaje != null) d.put("mensaje", mensaje);
        notifyListeners("progreso", d);
    }

    static void borrar(File f) {
        if (f == null || !f.exists()) return;
        File[] hijos = f.listFiles();
        if (hijos != null) for (File h : hijos) borrar(h);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
