package co.mebuc.pin;

import android.content.Intent;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Abre la pantalla IA Policía desde la pestaña de MEBUC. */
@CapacitorPlugin(name = "IaPolicia")
public class IaPoliciaPlugin extends Plugin {
    @PluginMethod
    public void abrir(PluginCall call) {
        getContext().startActivity(new Intent(getContext(), IaPoliciaActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        call.resolve();
    }
}
