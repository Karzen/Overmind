package karzen.network.overmind;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private Set<BluetoothDevice> pairedDevices;
    private BluetoothSocket btSocket;
    private BluetoothDevice currentDevice;

    private InputStream btIn;
    private OutputStream btOut;

    private LinearLayout commandLayout;
    private TextView statusView;
    private EditText macField;
    private EditText commandField;
    private Button connectBtn;
    private Button sendBtn;
    private Button ctrlBtn;


    private int PORT = 8;
    private boolean connected = false;

    private Context appContext;
    private String lineCheat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        commandLayout = (LinearLayout) findViewById(R.id.commandLayout);
        statusView = (TextView) findViewById(R.id.status);
        macField = (EditText) findViewById(R.id.addressField);
        commandField = (EditText) findViewById(R.id.commandInput);

        connectBtn = (Button) findViewById(R.id.connectButton);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                initConnection();
            }
        });
        sendBtn = (Button) findViewById(R.id.sendCommand);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendCommand();
            }
        });
        ctrlBtn = (Button) findViewById(R.id.controlBtn);
        ctrlBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                commandField.setText(commandField.getText() + "%CTRL%");
            }
        });


        this.appContext = this;

        /// Requesting access to Bluetooth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(getBaseContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        1);
            }
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        /// Getting all bonded devices
        this.pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                Log.d("Device", device.getName() + "  - " + device.getAddress());
                addDevice(device);
            }
        }

    };

    public void addDevice(final BluetoothDevice device){

        Button myButton = new Button(this);
        myButton.setText(device.getName() + " : " + device.getAddress());
        LinearLayout ll = (LinearLayout)findViewById(R.id.devicesLayout);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ll.addView(myButton, lp);

        myButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setCurrentMac(device.getAddress());
            }
        });

    }

    private void setCurrentMac(String mac){
        this.macField.setText(mac);
    }

    private boolean checkMac(String mac){
        short count = 0;
        char current;
        if(mac.length() != 17)
            return false;
        for(int i = 0; i < mac.length(); i++) {
            current = mac.charAt(i);
            if (current == ':') {
                count++;
                continue;
            }
            if(current > 'F' || (current < 'A' && current > '9') || current < '0' )
                return false;

        }
        if(count != 5)
            return false;
        return true;
    }
    private String formatMac(String mac){
        mac = String.join("", mac.split(" "));
        setCurrentMac(mac);
        return mac;
    }

    private void initConnection(){
        if(!connected) {
            String deviceMac = formatMac(this.macField.getText().toString());
            if (!checkMac(deviceMac)) {
                Toast.makeText(this, "Invalid Mac Address", Toast.LENGTH_SHORT).show();
            }
            connectToDevice(deviceMac);
        }
        else{
            endConnection();
        }

        macField.setEnabled(!connected);
        sendBtn.setEnabled(connected);
        commandField.setEnabled(connected);

    }

    private void connectToDevice(String mac){

        currentDevice = bluetoothAdapter.getRemoteDevice(mac);
        Log.d("DEBUG", currentDevice.getName());


        Method m = null;
        try {
            m = currentDevice.getClass().getMethod("createInsecureRfcommSocket", new Class[] {int.class});
            this.btSocket = (BluetoothSocket) m.invoke(currentDevice, this.PORT);
        }
        catch (Exception e) {
            Toast.makeText(this, "Failed to acquire host", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            this.btSocket.connect();
            this.btIn = btSocket.getInputStream();
            this.btOut = btSocket.getOutputStream();
            this.connected = true;
            Toast.makeText(this, "Connected Successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show();
            return;
        }

        statusView.setText("Status: Connected");
        connectBtn.setText("Disconnect");

        new Thread(new Runnable() {
            public void run() {
                receiver();
            }
        }).start();


    }

    public void receiver(){
        Log.d("RECEIVER", "STARTED");
        byte[] buffer = new byte[128];
        boolean idk = false;
        while(connected){
            try {
                Log.d("RECEIVER", "A INTRAT IN TRY");
                Log.d("RECEIVER", "LEN" + String.valueOf(btIn.available()));
                if(btIn.available() > 2) {
                    buffer = new byte[btIn.available()];
                    btIn.read(buffer);
                    idk = true;
                }
                else{
                    while(btIn.available() == 0 || btIn.available() == 2)
                        continue;
                    Log.d("RECEIVER", "A PRIMIT CEVA | LEN " + String.valueOf(btIn.available()));
                    Thread.sleep(100);
                    throw new Exception("doar sarim");
                }
                Log.d("RECEIVER", "GOT PACKET");
                Log.d("RECEIVER", new String(buffer).replace("\0", ""));
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            catch (Exception e){
                e.printStackTrace();
                continue;
            }

            Log.d("RECEIVER", "A TRECUT DE TRY");
            if(idk){
            lineCheat = new String(buffer);
            Log.d("RECEIVER-Com", " - "+ lineCheat + " - ");
            this.runOnUiThread(new Runnable() {
                                   @Override
                                   public void run() {
                                       TextView lineView = new TextView(appContext);
                                       lineView.setText(lineCheat);
                                       lineView.setTextColor(Color.parseColor("#D8DFDE"));
                                       LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                                       commandLayout.addView(lineView);
                                   }
                               });
            idk = false;
            }
        }
    }

    private void endConnection(){
        try {
            btOut.write("%%%quit%%%".getBytes());
            btOut.close();
            btIn.close();
            btSocket.close();
            connected = false;

            statusView.setText("Status: Disconnected");
            connectBtn.setText("Connect");

            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show();
        }
    }

    public void sendCommand(){
        String command = commandField.getText().toString();
        if(command.contains("%CTRL%")){
            char letter = command.charAt(command.indexOf("%CTRL%") + 6);
            if(!(letter >= 'a' && letter <= 'z')) {
                Toast.makeText(this, "INVALID CTRL ARGUMENT", Toast.LENGTH_SHORT).show();
                commandField.setText("");
                return;
            }
            command = command.substring(0, command.indexOf("%CTRL%")) + String.valueOf((char)((int)(letter - 'a' + 1))) + command.substring(command.indexOf("%CTRL%") + 7);

        }

        command += "\n";
        Log.d("SENDER", command);
        commandField.setText("");


        try {
            btOut.write(command.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    protected void onActivityResult (int requestCode,
                                     int resultCode,
                                     Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d("Bt-Request-Result",  String.valueOf(resultCode));

        if(resultCode == RESULT_CANCELED){
            this.finish();
            System.exit(0);
        }
    }

}


/*class DownloadFilesTask extends AsyncTask<String, Integer, Long> {
    protected void doInBackground(String command) { }

    protected void onProgressUpdate(Integer... progress) { }

    protected void onPostExecute(Long result) { }
}*/
