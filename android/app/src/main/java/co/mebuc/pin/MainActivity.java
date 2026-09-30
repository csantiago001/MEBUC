package co.mebuc.pin;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ActualizadorPlugin.class);
        registerPlugin(IaPoliciaPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        IaPoliciaPlugin ia = IaPoliciaPlugin.instancia;
        if (ia != null && ia.manejarAtras()) return;
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        IaPoliciaPlugin ia = IaPoliciaPlugin.instancia;
        if (ia != null) ia.resultadoPermisos(requestCode, grantResults);
    }

    @Override
    public void onPause() {
        IaPoliciaPlugin ia = IaPoliciaPlugin.instancia;
        if (ia != null) ia.pausar();
        super.onPause();
    }
}
