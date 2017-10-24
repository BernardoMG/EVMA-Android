package pt.ulisboa.tecnico.cmov.evma;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Bernardo Graça
 * Rui Furtado
 * Bernardo Rodrigues
 */

public class MainActivity extends Activity {

    private String ambulanceId="";
    private EditText id;
    private WifiManager wifiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        wifiManager= (WifiManager) getSystemService(Context.WIFI_SERVICE);

        if(wifiManager.isWifiEnabled())
        {
            wifiManager.setWifiEnabled(false);
        }

        //Button to go to Sign In
        id =(EditText) findViewById(R.id.editText);

        ImageButton sign_in = (ImageButton) findViewById(R.id.imageButton);
        assert sign_in != null;
        sign_in.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ambulanceId=id.getText().toString();
                Intent goSign_In = new Intent(v.getContext(), Evma_Ambulance.class);
                goSign_In.putExtra("ambulance",ambulanceId);
                startActivity(goSign_In);
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        System.exit(0);
    }

}