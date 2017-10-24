package pt.ulisboa.tecnico.cmov.evma;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.WindowManager;
import android.location.LocationListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Bernardo Graça
 * Rui Furtado
 * Bernardo Rodrigues
 */

public class Evma_Ambulance extends Activity implements OnMapReadyCallback, LocationListener {

    protected LocationManager lManager;
    protected LocationListener locationListener;
    protected MapFragment mapFragment;
    protected GoogleMap map;
    private WifiManager wifiManager;
    private boolean init=false;

    private ListView itemsDebug;
    private ArrayAdapter<String> adapterDebug;
    private ArrayList<String> clientsOnAdapter;
    private ArrayList<String> items;

    private HashMap<String, Clients> clientsConnected = new HashMap<>();
    private Thread clientsSearch;
    private final int reachableTimeout = 300;
    private final boolean onlyReachable = false;
    Handler mainHandler;

    private Socket client;
    private PrintWriter printwriter;
    private String message;
    private String serverIp="";
    private Thread t;


    private Location currentLocation;
    private String currentWifiAp;
    private String ambulanceId;

    private static Socket clientSocket;
    private static InputStreamReader inputStreamReader;
    private static BufferedReader bufferedReader;
    private String messageAmbulance;
    //private static String message="";

    private ServerSocket serverSocket;
    private int isAnAmbulanceAround=0;
    private String evmaClientName;
    private Thread receiverAmbulance;

    private boolean threadClients = true;
    private boolean threadAmbulance = true;

    private WifiScanReceiver wifiReciever;
    private ArrayList<String> wifis;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evm);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);


        mainHandler = new Handler(this.getMainLooper());

        //Code to managed the lists and adapters
        items= new ArrayList<>();
        itemsDebug = (ListView) findViewById(R.id.listView);
        adapterDebug = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        itemsDebug.setAdapter(adapterDebug);
        clientsOnAdapter= new ArrayList<>();

        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.mapsFragment);
        mapFragment.getMapAsync(Evma_Ambulance.this);

        lManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Check Permissions Now
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET}, 10);
        }

        lManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0, Evma_Ambulance.this);

        wifiManager= (WifiManager) getSystemService(Context.WIFI_SERVICE);
        ambulanceId=getIntent().getStringExtra("ambulance");
        //wifis=getIntent().getStringArrayListExtra("available networks"); //PARA QUE ISTO??

        if(!wifiManager.isWifiEnabled())
        {
            wifiManager.setWifiEnabled(true);
            wifiManager.disconnect();
        }
       
        //######## esta thread só deveria acontecer depois da criação do AP
        clientsSearch = new Thread(runnable, "Clients Search");
        clientsSearch.start();

        ToggleButton toggle = (ToggleButton) findViewById(R.id.toggleButton);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled
                    Thread t1= new Thread(searchWifi, "Check Network");
                    t1.start();
                    /*try {
                        t1.join();

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/


                } else {
                    // The toggle is disabled
                    threadClients=false;
                    threadAmbulance=false;
                    unregisterReceiver(wifiReciever);
                    if(!wifiManager.isWifiEnabled())
                    {
                        wifiManager.setWifiEnabled(true);
                        wifiManager.disconnect();
                    }
                }
            }
        });
    }

    Runnable searchWifi = new Runnable() {
        @Override
        public void run() {
            wifis = new ArrayList<>();
            wifiReciever = new WifiScanReceiver();
            registerReceiver(wifiReciever, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            wifiManager.startScan();
        }
    };


    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            while (threadClients) {

                BufferedReader br = null;
                final ArrayList<Clients> clients = new ArrayList<Clients>();

                try {
                    br = new BufferedReader(new FileReader("/proc/net/arp"));
                    String line;
                    //adapterDebug.clear();
                    while ((line = br.readLine()) != null) {
                        String[] splitted = line.split(" +");
                        if ((splitted != null) && (splitted.length >= 4)) {
                            // Basic sanity check
                            String mac = splitted[3];

                            if (mac.matches("..:..:..:..:..:..")) {
                                boolean isReachable = InetAddress.getByName(splitted[0]).isReachable(reachableTimeout);

                                if (!onlyReachable || isReachable) {
                                    Clients client = new Clients(splitted[0], mac, splitted[5], isReachable);
                                    clients.add(client);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(this.getClass().toString(), e.toString());
                } finally {
                    try {
                        br.close();
                    } catch (IOException e) {
                        Log.e(this.getClass().toString(), e.getMessage());
                    }
                }
                //Get a handler that can be used to post to the main thread
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(!clients.isEmpty())
                            updateView(clients);
                    }
                });
            }
        }
    };

    private void updateView(ArrayList<Clients> clients){
        for(Clients c : clients) {
            clientsConnected.put(c.getHWAddr(), c);
            if(adapterDebug.isEmpty()) {
                adapterDebug.add("Device: " + c.getDevice() + " IP Address: " + c.getIpAddr() + " MAC Address: " + c.getHWAddr() + " Reachable: " + c.isReachable());
                clientsOnAdapter.add("Device: " + c.getDevice() + " IP Address: " + c.getIpAddr() + " MAC Address: " + c.getHWAddr() + " Reachable: " + c.isReachable());
            }
            else {
                if (!clientsOnAdapter.contains("Device: " + c.getDevice() + " IP Address: " + c.getIpAddr() + " MAC Address: " + c.getHWAddr() + " Reachable: " + c.isReachable())) {
                    adapterDebug.add("Device: " + c.getDevice() + " IP Address: " + c.getIpAddr() + " MAC Address: " + c.getHWAddr() + " Reachable: " + c.isReachable());
                    clientsOnAdapter.add("Device: " + c.getDevice() + " IP Address: " + c.getIpAddr() + " MAC Address: " + c.getHWAddr() + " Reachable: " + c.isReachable());
                }
            }
        }
    }

    private class WifiScanReceiver extends BroadcastReceiver {

        public void onReceive(Context c, Intent intent) {
            List<ScanResult> wifiScanList = wifiManager.getScanResults();
            for (int i = 0; i < wifiScanList.size(); i++) {
                if(!wifis.contains(wifiScanList.get(i).SSID.toString())) {
                    wifis.add((wifiScanList.get(i).SSID).toString());
                    Log.d("wifi network", wifiScanList.get(i).SSID.toString());
                }
            }
            //######## esta thread só deveria acontecer se uma rede evma fosse encontrada! update
            receiverAmbulance= new Thread(receiverFromAmbulance,"Receiver From Neighbor Ambulances");
            receiverAmbulance.start();
            checkIfAmbulanceAround();
        }
    }

    private void createWifiAccessPoint() {
        wifiManager = (WifiManager)getBaseContext().getSystemService(Context.WIFI_SERVICE);
        if(wifiManager.isWifiEnabled())
        {
            wifiManager.setWifiEnabled(false);
        }

        WifiConfiguration netConfig = new WifiConfiguration();

        netConfig.SSID = "EVMA:"+ambulanceId;
        netConfig.BSSID= "EVMA:"+ambulanceId;

        netConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        netConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        netConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        netConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

        try {
            Method setWifiApMethod = wifiManager.getClass().getMethod("setWifiApEnabled",  WifiConfiguration.class, boolean.class);
            boolean apstatus=(Boolean) setWifiApMethod.invoke(wifiManager, netConfig,true);

            Method isWifiApEnabledmethod = wifiManager.getClass().getMethod("isWifiApEnabled");

            while(!(Boolean)isWifiApEnabledmethod.invoke(wifiManager)){};

            Method getWifiApStateMethod = wifiManager.getClass().getMethod("getWifiApState");
            int apstate=(Integer)getWifiApStateMethod.invoke(wifiManager);
            Method getWifiApConfigurationMethod = wifiManager.getClass().getMethod("getWifiApConfiguration");
            netConfig=(WifiConfiguration)getWifiApConfigurationMethod.invoke(wifiManager);

            Log.e("CLIENT", "\nSSID:"+netConfig.SSID+"\nPassword:"+netConfig.preSharedKey+"\n");
        } catch (Exception e) {
            Log.e(this.getClass().toString(), "", e);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
    }

    @Override
    public void onLocationChanged(Location location) {
        //Log.d("Latitude", ""+location.getLatitude());
        //Log.d("Longitude", ""+location.getLongitude());
        /*if(!init){
            createWifiAccessPoint();
            clientsSearch.start();
            init=true;
        }*/
        addLocation(location);
        currentLocation=location;
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
    }

    private void addLocation(Location location){
        map.clear();
        MarkerOptions options = new MarkerOptions().position(new LatLng(location.getLatitude(), location.getLongitude())).title("Ambulance");
        options.icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.market)));
        map.addMarker(options);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(),location.getLongitude()), 16));
        sendBroadcastMessage(""+location.getLatitude(),""+location.getLongitude());
    }

    Runnable server = new Runnable() {
        @Override
        public void run() {
            try {
                Log.d("Message",message +" "+ serverIp);
                //message="Hi! I'm the ambulance";
                client = new Socket(serverIp, 4444);  //connect to server
                printwriter = new PrintWriter(client.getOutputStream(), true);
                printwriter.write(message);  //write the message to output stream
                printwriter.flush();
                printwriter.close();
                client.close();   //closing the connection
            } catch (UnknownHostException e){
                //e.printStackTrace();
            }catch(IOException e){
                //e.printStackTrace();
            }
        }
    };

    protected void communicateWithServer (String ipAddress, String latitude, String longitude){
        t=new Thread(server, "My Server");
        serverIp=ipAddress;
        if(isAnAmbulanceAround==1){
            message=latitude+" "+longitude+" "+evmaClientName;
        }
        else {
            message =latitude+" "+longitude+" "+ambulanceId;
        }
        t.start();
    }

    private void sendBroadcastMessage(String latitude, String longitude) {
        if(!clientsConnected.isEmpty()){
            int i=0;
            for(Clients c : clientsConnected.values()){
                Log.d("Client", c.getHWAddr() + " " + c.getIpAddr());
                communicateWithServer(c.getIpAddr(), latitude, longitude);
            }
        }
    }

    Runnable receiverFromAmbulance = new Runnable() {
        @Override
        public void run() {
            while (threadAmbulance) {
                try {
                    //Client -> Server
                    clientSocket = serverSocket.accept();   //accept the client connection
                    inputStreamReader = new InputStreamReader(clientSocket.getInputStream());
                    bufferedReader = new BufferedReader(inputStreamReader); //get the client message
                    messageAmbulance = bufferedReader.readLine();
                    Log.d("Ambulance", messageAmbulance);
                    inputStreamReader.close();
                    clientSocket.close();
                    if(messageAmbulance!=null) {
                        String[] coordinates = messageAmbulance.split(" ");
                        isAnAmbulanceAround = 1;
                        evmaClientName = coordinates[2];
                        sendBroadcastMessage(coordinates[0], coordinates[1]);
                        isAnAmbulanceAround = 0;
                        messageAmbulance = null;
                        evmaClientName = null;
                    }
                } catch (Exception e) {}

            }
        }
    };

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        threadClients=false;
        threadAmbulance=false;
        if(!wifiManager.isWifiEnabled())
        {
            wifiManager.setWifiEnabled(true);
        }
    }

    public void checkIfAmbulanceAround() {
        try{
            Log.d("available networks",wifis.toString());
            boolean connected = true;
            for (int i = 0; i < wifis.size(); i++) {
                String wifiAp=wifis.get(i);
                if(wifiAp.contains("EVMA")){
                    Toast.makeText(this, "Another ambulance in the area", Toast.LENGTH_SHORT).show();
                    WifiConfiguration conf = new WifiConfiguration();
                    conf.SSID = "\"" + wifiAp + "\"";
                    conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                    wifiManager.addNetwork(conf);
                    List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
                    for (WifiConfiguration wifi : list) {
                        if (wifi.SSID != null && wifi.SSID.equals("\"" + wifiAp + "\"")) {
                            wifiManager.disconnect();
                            wifiManager.enableNetwork(wifi.networkId, true);
                            wifiManager.reconnect();
                            while (connected){
                                if (wifiManager.getConnectionInfo().getSSID().equals("\"" + wifiAp + "\"")) {
                                    Toast.makeText(this, "Connected to the other ambulance", Toast.LENGTH_SHORT).show();
                                    //BUGGGG sacar ip do hotspot
                                    //currentWifiAp = String.valueOf(wifiManager.getDhcpInfo().serverAddress);
                                    //Log.d("Ambulance IP: ",currentWifiAp);
                                    //String ipAP=String.valueOf(currentWifiAp);
                                    //communicateWithServer(currentWifiAp, String.valueOf(currentLocation.getLatitude()), String.valueOf(currentLocation.getLongitude()));
                                    sendBroadcastMessage(String.valueOf(currentLocation.getLatitude()),String.valueOf(currentLocation.getLongitude()));
                                    //Toast.makeText(this, "Coordinates just sended to the other ambulance: " , Toast.LENGTH_SHORT).show();
                                    connected=false;
                                }
                            }
                            break;
                        }
                    }

                }
            }
            createWifiAccessPoint();

        }catch (Exception e){
            e.printStackTrace();
        }
    }
}