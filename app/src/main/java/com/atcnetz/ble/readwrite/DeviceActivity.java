package com.atcnetz.ble.readwrite;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.UUID;

import com.atcnetz.ble.util.BleUtil;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import static com.atcnetz.ble.util.ByteUtil.hexToBytes;
import static java.lang.Thread.sleep;
import static com.atcnetz.ble.util.ByteUtil.bytesToString;

public class DeviceActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "BLEDevice";
    TextView tvContent;
    ScrollView scrollview;

    public static final String EXTRA_BLUETOOTH_DEVICE = "BT_DEVICE";
    public static final String EXTRA_BLUETOOTH_MODE = "BT_MODE";
    private BluetoothAdapter mBTAdapter;
    private BluetoothDevice mDevice;
    private BluetoothGatt mConnGatt;
    private int mStatus;
    private int bledevice;
    private boolean blemode;

    private Button DoDFUButton;
    private Button StartBootloaderButton;

    UUID Main_Characteristic_Write;
    UUID Main_Characteristic_Notify;
    UUID Notify_Config;
    UUID Main_Service;


    private final BluetoothGattCallback mGattcallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mStatus = newState;
                mConnGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mStatus = newState;
                runOnUiThread(new Runnable() {
                    public void run() {
                        StartBootloaderButton.setEnabled(false);
                    }
                });
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (Main_Characteristic_Notify.toString().equalsIgnoreCase(characteristic.getUuid().toString())) {
                    final String name = characteristic.getStringValue(0);

                    runOnUiThread(new Runnable() {
                        public void run() {
                            KLog(name);
                            setProgressBarIndeterminateVisibility(false);
                        }

                        ;
                    });
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (Main_Characteristic_Notify.equals(characteristic.getUuid())) {
                byte[] receiverData = characteristic.getValue();
                /*runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            KLog("onCharacteristicChanged: " + new String(receiverData,"UTF-8"));// bytesToString(receiverData));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                });*/
                int packNo = receiverData[2];
                if (packNo < 0 || ((receiverData[0] == (byte) 1 && packNo == 90) || (receiverData[2] == (byte) -127 && receiverData[4] == (byte) 2))) {
                    boolean isSendOver = true;
                    if (receiverData[0] == (byte) -116 && receiverData[1] != (byte) 0) {
                        if (receiverData[1] == (byte) 90) {
                            isSendOver = true;
                        } else if (!(receiverData[2] == (byte) -127 && receiverData[4] == (byte) -1)) {
                            isSendOver = false;
                            if (receiverData[2] == (byte) -113) {
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        KLog("Send is NOT over");
                                    }
                                });
                            }
                        }
                    }
                    if (isSendOver) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                KLog("Send is over");
                            }
                        });
                    }
                }
            }
        }



        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            for (BluetoothGattService service : gatt.getServices()) {
                if ((service == null) || (service.getUuid() == null)) {
                    continue;
                }
                if (D6UUIDs.Main_Service.toString().equalsIgnoreCase(service.getUuid().toString())) {
                    bledevice = 1;
                }else if(EspruinoUUIDs.Main_Service.toString().equalsIgnoreCase(service.getUuid().toString())){
                    bledevice = 2;

                }else if(DFUUUIDs.Main_Service.toString().equalsIgnoreCase(service.getUuid().toString())){
                    bledevice = 3;

                }else{
                    bledevice = 4;

                }
            }

            runOnUiThread(new Runnable() {
                public void run() {
                    setProgressBarIndeterminateVisibility(false);
                    if (bledevice==1){
                        Main_Service = D6UUIDs.Main_Service;
                        Notify_Config = D6UUIDs.Notify_Config;
                        Main_Characteristic_Notify = D6UUIDs.Main_Characteristic_Notify;
                        Main_Characteristic_Write = D6UUIDs.Main_Characteristic_Write;
                        blemode = false;
                        StartBootloaderButton.setEnabled(true);
                        setNotifyCharacteristic(true);
                        KLog("D6 was found in stock mode, you can bring it to Bootloader by clicking on 'Start Bootloader'");
                    }else if(bledevice==2){
                        Main_Service = EspruinoUUIDs.Main_Service;
                        Notify_Config = EspruinoUUIDs.Notify_Config;
                        Main_Characteristic_Notify = EspruinoUUIDs.Main_Characteristic_Notify;
                        Main_Characteristic_Write = EspruinoUUIDs.Main_Characteristic_Write;
                        blemode = false;
                        StartBootloaderButton.setEnabled(true);
                        setNotifyCharacteristic(true);
                        KLog("D6 was found in Espruino mode, you can bring it to Bootloader by clicking on 'Start Bootloader'");
                    }else if(bledevice==3){
                        Main_Service = DFUUUIDs.Main_Service;
                        Notify_Config = DFUUUIDs.Notify_Config;
                        Main_Characteristic_Notify = DFUUUIDs.Main_Characteristic_Notify;
                        Main_Characteristic_Write = DFUUUIDs.Main_Characteristic_Write;
                        blemode = true;
                        KLog("D6 was found in Bootloader mode you can Flash it by clicking on 'Do DFU Update'");
                    }else {
                        Main_Service = D6UUIDs.Main_Service;
                        Notify_Config = D6UUIDs.Notify_Config;
                        Main_Characteristic_Notify = D6UUIDs.Main_Characteristic_Notify;
                        Main_Characteristic_Write = D6UUIDs.Main_Characteristic_Write;
                        blemode = true;
                        KLog("No D6 Device was found, do you selected the right one? you can try to flash it anyway.");
                    }
                }
            });
        }


        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {

            runOnUiThread(new Runnable() {
                public void run() {
                    setProgressBarIndeterminateVisibility(false);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_device);

        // state
        mStatus = BluetoothProfile.STATE_DISCONNECTED;
        DoDFUButton = (Button) findViewById(R.id.DoDfu);
        DoDFUButton.setOnClickListener(this);
        StartBootloaderButton = (Button) findViewById(R.id.startBootloader);
        StartBootloaderButton.setOnClickListener(this);
        tvContent = (TextView) findViewById(R.id.tv_content);
        scrollview = (ScrollView) findViewById(R.id.scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();

        init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mConnGatt != null) {
            if ((mStatus != BluetoothProfile.STATE_DISCONNECTING)
                    && (mStatus != BluetoothProfile.STATE_DISCONNECTED)) {
                mConnGatt.disconnect();
            }
            mConnGatt.close();
            mConnGatt = null;
        }
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.DoDfu) {
            if (mConnGatt != null) {
                if ((mStatus != BluetoothProfile.STATE_DISCONNECTING)
                        && (mStatus != BluetoothProfile.STATE_DISCONNECTED)) {
                    mConnGatt.disconnect();
                }
                mConnGatt.close();
                mConnGatt = null;
            }
                Intent intent = new Intent(getApplicationContext(), DFUActivity.class);
            intent.putExtra(DeviceActivity.EXTRA_BLUETOOTH_DEVICE, mDevice);
            intent.putExtra(DeviceActivity.EXTRA_BLUETOOTH_MODE, blemode);
                startActivity(intent);
        } else if (v.getId() == R.id.startBootloader) {
            if(bledevice== 1){
            writeCharacteristic1("BT+UPGB:1");
            try {
                sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            KLog(writeCharacteristic1("BT+RESET"));
            KLog("Look at the Tracker are there 3 Arrows? Great, now click on 'Do DFU Update' to Flash it.");
        }else if (bledevice == 2){
                try {
                    writeCharacteristic1((char)3+"");
                    sleep(30);
                    writeCharacteristic1("\r");
                    sleep(30);
                    writeCharacteristic1("poke32(0x4000051c,1)");
                    sleep(30);
                    KLog(writeCharacteristic1("\r"));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                KLog("Look at the Tracker are there 3 Arrows? Great, now click on 'Do DFU Update' to Flash it.");
            }else{
                KLog("Something went totally wrong, you shouldn't be able to press this button right now.");
            }

        }
    }

    public static class D6UUIDs {
        public static final UUID Main_Characteristic_Write = UUID.fromString("00000003-0000-1000-8000-00805f9b34fb");
        public static final UUID Main_Characteristic_Notify = UUID.fromString("00000004-0000-1000-8000-00805f9b34fb");
        public static final UUID Notify_Config = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        public static final UUID Main_Service = UUID.fromString("0000190b-0000-1000-8000-00805f9b34fb");
    }

    public static class DFUUUIDs {
        public static final UUID Main_Characteristic_Write = UUID.fromString("00000005-0000-1000-8000-00805f9b34fb");
        public static final UUID Main_Characteristic_Notify = UUID.fromString("00000006-0000-1000-8000-00805f9b34fb");
        public static final UUID Notify_Config = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        public static final UUID Main_Service = UUID.fromString("0000190c-0000-1000-8000-00805f9b34fb");
    }

    public static class EspruinoUUIDs {
        public static final UUID Main_Characteristic_Write = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
        public static final UUID Main_Characteristic_Notify = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
        public static final UUID Notify_Config = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        public static final UUID Main_Service = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    }

    public void writeCharacteristic(String ddd) {
        byte[] data = hexToBytes(ddd.replaceAll(" ",""));
        if (data == null || data.length <= 0) {
            KLog("the data to be written is empty");
        } else {
            BluetoothGatt gatt = mConnGatt;
            if (gatt == null) {
                KLog(" bluetoothGatt is null");
                return;
            }
            BluetoothGattService service = gatt.getService(Main_Service);
            if (service == null) {
                KLog(" BluetoothGattService is null");
                return;
            }
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(Main_Characteristic_Write);
            if (characteristic == null || (characteristic.getProperties() & 12) == 0) {
                KLog("this characteristic not support write!");
            } else if (!characteristic.setValue(data)) {
                KLog("Updates the locally stored value of this characteristic fail");
            } else if (!gatt.writeCharacteristic(characteristic)) {
                KLog("gatt writeCharacteristic fail");
            } else {
                KLog("Wrote to Characteristic! " + bytesToString(data));
            }
        }
    }
    public String writeCharacteristic1(String data) {
        if (data == null) {
           return "the data to be written is empty";
        } else {
            BluetoothGatt gatt = mConnGatt;
            if (gatt == null) {
                return " bluetoothGatt is null";
            }
            BluetoothGattService service = gatt.getService(Main_Service);
            if (service == null) {
                return " BluetoothGattService is null";
            }
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(Main_Characteristic_Write);
            if (characteristic == null || (characteristic.getProperties() & 12) == 0) {
                return "this characteristic not support write!";
            } else if (!characteristic.setValue(data)) {
                return "Updates the locally stored value of this characteristic fail";
            } else if (!gatt.writeCharacteristic(characteristic)) {
                return "gatt writeCharacteristic fail";
            } else {
                return "Wrote to Characteristic!";
            }
        }
    }

    public void setNotifyCharacteristic(boolean enabled) {
        BluetoothGatt gatt = mConnGatt;
        if (gatt != null) {
            BluetoothGattService service = gatt.getService(Main_Service);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(Main_Characteristic_Notify);
                if (characteristic != null) {
                    boolean success = gatt.setCharacteristicNotification(characteristic, enabled);
                    if (success) {
                        KLog("Got an answer");
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(Notify_Config);
                        if (descriptor == null) {
                            KLog("descriptor is null");
                            return;
                        }
                        int properties = characteristic.getProperties();
                        //KLog("properties = " + properties);
                        if ((properties & 32) != 0) {
                            descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : new byte[]{(byte) 0, (byte) 0});
                        } else if ((properties & 16) != 0) {
                            descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[]{(byte) 0, (byte) 0});
                        } else {
                            descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[]{(byte) 0, (byte) 0});
                        }
                        gatt.writeDescriptor(descriptor);
                    }
                }
            }
        }
    }

    public void KLog(String TEXT) {
        tvContent.append("\n" + TEXT+"\n");

        scrollDown();

    }

    void scrollDown() {
        Thread scrollThread = new Thread() {
            public void run() {
                try {
                    sleep(200);
                    DeviceActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            scrollview.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        scrollThread.start();
    }

    private void init() {
        // BLE check
        if (!BleUtil.isBLESupported(this)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // BT check
        BluetoothManager manager = BleUtil.getManager(this);
        if (manager != null) {
            mBTAdapter = manager.getAdapter();
        }
        if (mBTAdapter == null) {
            Toast.makeText(this, R.string.bt_unavailable, Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }

        // check BluetoothDevice
        if (mDevice == null) {
            mDevice = getBTDeviceExtra();
            if (mDevice == null) {
                finish();
                return;
            }
        }

        StartBootloaderButton.setEnabled(false);

        // connect to Gatt
        if ((mConnGatt == null)
                && (mStatus == BluetoothProfile.STATE_DISCONNECTED)) {
            // try to connect
            mConnGatt = mDevice.connectGatt(this, false, mGattcallback);
            mStatus = BluetoothProfile.STATE_CONNECTING;
        } else {
            if (mConnGatt != null) {
                // re-connect and re-discover Services
                mConnGatt.connect();
                mConnGatt.discoverServices();
            } else {
                Log.e(TAG, "state error");
                finish();
                return;
            }
        }
        setProgressBarIndeterminateVisibility(true);
    }

    private BluetoothDevice getBTDeviceExtra() {
        Intent intent = getIntent();
        if (intent == null) {
            return null;
        }

        Bundle extras = intent.getExtras();
        if (extras == null) {
            return null;
        }

        return extras.getParcelable(EXTRA_BLUETOOTH_DEVICE);
    }

}
