package com.example.bernardo.evma_client;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.WindowManager;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Bernardo Graça
 * Rui Furtado
 * Bernardo Rodrigues
 */

public class Evma_Client extends Activity implements OnMapReadyCallback, LocationListener {

    protected LocationManager lManager;
    protected LocationListener locationListener;
    protected WifiManager wifiManager;
    protected MapFragment mapFragment;
    protected GoogleMap map;

    private ListView itemsDebug;
    private ArrayAdapter<String> adapterDebug;
    private ArrayList<String> items;

    private Thread wifiThread;
    private Thread clientThread;
    private ServerSocket serverSocket;

    private static Socket clientSocket;
    private static InputStreamReader inputStreamReader;
    private static BufferedReader bufferedReader;
    private static String message="";
    private Marker marker;
    private Marker ambulanceMarker;
    private Marker ambulanceMarker2;
    private HashMap<String, Marker> ambulances = new HashMap<>();
    private int counter=0;

    Handler mainHandler;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evm);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        items= new ArrayList<>();
        itemsDebug = (ListView) findViewById(R.id.listView);
        adapterDebug = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        itemsDebug.setAdapter(adapterDebug);

        mainHandler=new Handler(this.getMainLooper());


        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.mapsFragment);
        mapFragment.getMapAsync(Evma_Client.this);

        lManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Check Permissions Now
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET}, 10);

        }

        lManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0, Evma_Client.this);

        wifiManager= (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiThread = new Thread(runnable, "turn-on wifi");
        wifiThread.start();

        try {
            serverSocket = new ServerSocket(4444);  //Server socket
        } catch (IOException e) {
            Log.d("Socket","Could not listen on port: 4444");
        }
        clientThread = new Thread(client, "Client");
        clientThread.start();
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d("Latitude", ""+location.getLatitude());
        Log.d("Longitude", ""+location.getLongitude());
        addLocation(location);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d("Latitude","disable");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d("Latitude","enable");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d("Latitude","status");
    }


    private void addLocation(Location location) {
        //adapterDebug.add("Latitude: "+location.getLatitude()+" Longitude: "+ location.getLongitude());
        if(marker != null) {
            if (marker.isVisible()) {
                marker.remove();
            }
        }
        marker = map.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(), location.getLongitude())).title("I´m here"));
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 14));


    }


    public void updateMap(String[] location){
        Double latitude= Double.parseDouble(location[0]);
        Double longitude= Double.parseDouble(location[1]);
        String wifiAp=location[2];
        if(!ambulances.containsKey(wifiAp)) {
            //counter += 1;
            ambulanceMarker = map.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("Ambulance " + wifiAp).icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.market))));
            ambulances.put(wifiAp, ambulanceMarker);
        }
        else{
            Marker ambulanceMarker=ambulances.get(wifiAp);
            if (ambulanceMarker != null) {
                if (ambulanceMarker.isVisible()) {
                    ambulanceMarker.remove();
                    ambulanceMarker = map.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title("Ambulance " + wifiAp).icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.market))));
                    ambulances.put(wifiAp, ambulanceMarker);
                }
            }
        }
    }



    /*private void connectToAP() {
        WifiInfo wifiInfo;
        String networkSSID = "EVMA";


        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + networkSSID + "\"";
        conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiManager.addNetwork(conf);
        if (wifiManager.isWifiEnabled()) {
            Log.d("State","wifi-on");
        } else {
            Log.d("State","set wifi on");
            wifiManager.setWifiEnabled(true);
        }
        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration i : list) {
            if (i.SSID != null && i.SSID.equals("\"" + networkSSID + "\"")) {
                wifiManager.disconnect();
                wifiManager.enableNetwork(i.networkId, true);
                wifiManager.reconnect();
                break;
            }
        }
    }*/




    @Override
    public void onBackPressed() {
        super.onBackPressed();

    }


    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            while (true) {
                wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                if (wifiManager.isWifiEnabled()){
                    if(wifiManager.getConnectionInfo().getSSID().toString().contains("EVMA")){}
                    else{
                        //connectToAP();
                    }
                }
                else {
                    Log.d("not connected", "trying to connect");
                    //connectToAP();
                }
            }
        }
    };


    Runnable client = new Runnable() {
        String[] location;

        @Override
        public void run() {


            while (true) {
                try {
                    //Client -> Server
                    clientSocket = serverSocket.accept();   //accept the client connection
                    inputStreamReader = new InputStreamReader(clientSocket.getInputStream());
                    bufferedReader = new BufferedReader(inputStreamReader); //get the client message
                    message = bufferedReader.readLine();
                    inputStreamReader.close();
                    clientSocket.close();
                    if(!message.equals("")) {
                        Log.d("Ambulance", message);
                        location = message.split(" ");
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run(){
                                updateMap(location);
                            }
                        });
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

            }
        }
    };
}