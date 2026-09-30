package co.mebuc.pin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Recibe el resultado de la instalación de la actualización. */
public class ReceptorInstalacion extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        int estado = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        if (estado == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Android pide confirmación: mostramos su ventana "¿Actualizar esta app?"
            @SuppressWarnings("deprecation")
            Intent confirmar = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmar != null) {
                confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { ctx.startActivity(confirmar); } catch (Exception ignored) { }
            }
            return;
        }
        if (estado == PackageInstaller.STATUS_SUCCESS) return; // la app se reinicia sola (ReceptorReinicio)

        ActualizadorPlugin p = ActualizadorPlugin.instancia;
        if (p != null) {
            String texto = estado == PackageInstaller.STATUS_FAILURE_ABORTED
                    ? "Instalación cancelada"
                    : (msg != null ? msg : "Falló la instalación (código " + estado + ")");
            p.emitirEstado(estado == PackageInstaller.STATUS_FAILURE_ABORTED ? "cancelado" : "error", 0, texto);
        }
    }
}
