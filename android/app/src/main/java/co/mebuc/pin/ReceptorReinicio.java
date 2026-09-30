package co.mebuc.pin;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** Se ejecuta justo después de que Android reemplaza la app por la versión nueva. */
public class ReceptorReinicio extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;

        // Restos de la versión anterior
        ActualizadorPlugin.borrar(new java.io.File(ctx.getCacheDir(), ActualizadorPlugin.CARPETA));

        Intent abrir = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (abrir == null) return;
        abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // 1) Intentar abrir la app directamente
        try { ctx.startActivity(abrir); } catch (Exception ignored) { }

        // 2) Respaldo: algunas versiones de Android bloquean abrir apps en segundo plano,
        //    así que dejamos una notificación "Toca para abrir".
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) return;
            String canal = "actualizaciones";
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(canal, "Actualizaciones", NotificationManager.IMPORTANCE_HIGH);
                ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
            }
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, abrir,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, canal)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("MEBUC actualizada")
                    .setContentText("Toca para abrir la nueva versión")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setTimeoutAfter(10 * 60 * 1000)
                    .setContentIntent(pi);
            NotificationManagerCompat.from(ctx).notify(7302, b.build());
        } catch (Exception ignored) { }
    }
}
