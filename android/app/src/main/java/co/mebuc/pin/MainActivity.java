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
}
