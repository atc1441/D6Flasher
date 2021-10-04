package com.atcnetz.patc.daatc;

import com.atcnetz.patc.util.BleUtil;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import static java.lang.Thread.sleep;

public class DeviceActivity extends Activity implements View.OnClickListener {
    private static final int READ_REQUEST_CODE = 42;
    Uri uri = null;
    byte[] loadedUpdateFile;
    private static final String TAG = "BLEDevice";
    ProgressBar progressBar;
    TextView tvContent;
    TextView percentText;
    ScrollView scrollview;

    public static final String EXTRA_BLUETOOTH_DEVICE = "BT_DEVICE";
    public static final String EXTRA_BLUETOOTH_MODE = "BT_MODE";
    public static final String EXTRA_BLUETOOTH_MODE_NORDIC = "BT_MODE_NORDIC";
    private BluetoothAdapter mBTAdapter;
    private BluetoothDevice mDevice;
    private BluetoothGatt mConnGatt;
    private int mStatus;
    private int bledevice;
    private boolean blemode;
    private boolean blemodenordic = false;

    private Button StartBootloaderButton;
    private Button doDFUButton;

    UUID Main_Characteristic_Write;
    UUID Main_Characteristic_Notify;
    UUID Notify_Config;
    UUID Main_Service;

    boolean updateStarted = false;
    boolean rebootStarted = false;
    boolean watchInUpdateMode = false;
    int currentPartPos = -1;
    int totalSizeUpdate = 0;
    byte[] currPart;
    byte[] fullSend;

    int cmdFitLength = 0;
    int receivedLength = 0;
    String fullCMD = "";
    byte[] fullCRC;

    int Fit_version = 1;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                uri = resultData.getData();
                assert uri != null;
                KLog("Selected file: " + getFileName(uri));
                try {
                    loadedUpdateFile = fullyReadFileToBytes(uri);
                    totalSizeUpdate = loadedUpdateFile.length;
                    KLog("File Size: " + totalSizeUpdate + " bytes");
                    progressBar.setProgress(0);
                    updateStarted = false;
                    rebootStarted = false;
                    watchInUpdateMode = false;
                    currentPartPos = -1;
                    currPart = new byte[0];
                    fullSend = new byte[0];
                    cmdFitLength = 0;
                    receivedLength = 0;
                    fullCMD = "";
                    fullCRC = new byte[0];
                    percentText.setVisibility(View.GONE);
                    if (totalSizeUpdate < 0x10000 || totalSizeUpdate > ((Fit_version==1)?0x2f000:0x5f000)) {
                        KLog("File size is wrong, please select the right file, needs to be between 0x10000 and 0x2f000 byte big.");
                    } else {
                        startDaBootloader(loadedUpdateFile.length);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private final BluetoothGattCallback mGattcallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (!updateStarted) tvContent.setText("");
                    }
                });
                mStatus = newState;
                mConnGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mStatus = newState;
                runOnUiThread(new Runnable() {
                    public void run() {
                        KLog("Disconnected");
                        StartBootloaderButton.setEnabled(false);
                        if (rebootStarted) {
                            rebootStarted = false;
                            KLog("Going to reconnect now, please wait");
                            init();
                        }
                    }
                });
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
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
                } else {
                    if (FitUUIDs.Characteristic_Manufacturer_Name.toString().equalsIgnoreCase(characteristic.getUuid().toString())) {
                        final String name = characteristic.getStringValue(0);
                        Log.d("Manufacturer_Name", name);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                check_fit_version(name);
                            }

                            ;
                        });
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (Main_Characteristic_Notify.equals(characteristic.getUuid())) {
                byte[] receiverData = characteristic.getValue();
                if (bledevice == 6) {
                    filterFitResponse(receiverData);
                } else {
                    filterResponse(new String(receiverData, StandardCharsets.UTF_8));
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
                    break;
                } else if (EspruinoUUIDs.Main_Service.toString().equalsIgnoreCase(service.getUuid().toString())) {
                    bledevice = 2;
                    break;
                } else if (DFUUUIDs.Main_Service.toString().equalsIgnoreCase(service.getUuid().toString())) {
                    bledevice = 3;
                    break;
                } else if (StockDFUUUIDs.Main_Service.toString().equalsIgnoreCase(service.getUuid().toString())) {
                    bledevice = 5;
                    break;
                } else if (FitUUIDs.Main_Service.toString().equalsIgnoreCase(service.getUuid().toString())) {
                    bledevice = 6;
                    break;
                } else if (StockSecureDFUUUIDs.Main_Service.toString().equalsIgnoreCase(service.getUuid().toString())) {
                    bledevice = 7;
                    break;
                } else {
                    bledevice = 4;
                }
            }

            runOnUiThread(new Runnable() {
                @SuppressLint("SetTextI18n")
                public void run() {
                    setProgressBarIndeterminateVisibility(false);
                    if (bledevice == 1) {
                        Main_Service = D6UUIDs.Main_Service;
                        Notify_Config = D6UUIDs.Notify_Config;
                        Main_Characteristic_Notify = D6UUIDs.Main_Characteristic_Notify;
                        Main_Characteristic_Write = D6UUIDs.Main_Characteristic_Write;
                        blemode = false;
                        StartBootloaderButton.setEnabled(true);
                        doDFUButton.setEnabled(true);
                        setNotifyCharacteristic(true);
                        KLog("D6 was found in stock mode, you can bring it to Bootloader by clicking on 'Start Bootloader'");
                        try {
                            sleep(300);
                            writeCharacteristic1("AT+VER\r\n");
                            sleep(30);
                            writeCharacteristic1("BT+VER\r\n");
                            KLog("Trying to get Firmware infos now...");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else if (bledevice == 2) {
                        Main_Service = EspruinoUUIDs.Main_Service;
                        Notify_Config = EspruinoUUIDs.Notify_Config;
                        Main_Characteristic_Notify = EspruinoUUIDs.Main_Characteristic_Notify;
                        Main_Characteristic_Write = EspruinoUUIDs.Main_Characteristic_Write;
                        blemode = false;
                        StartBootloaderButton.setEnabled(true);
                        doDFUButton.setEnabled(true);
                        setNotifyCharacteristic(true);
                        KLog("BLE Device was found in Espruino mode, you can bring it to Bootloader by clicking on 'Start Bootloader'");
                        try {
                            sleep(300);
                            writeCharacteristic1((char) 3 + "");
                            sleep(30);
                            writeCharacteristic1("\r");
                            sleep(30);
                            writeCharacteristic1("process.env.VERSION");
                            sleep(30);
                            writeCharacteristic1("\r");
                            KLog("Trying to get Firmware infos now...");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else if (bledevice == 3) {
                        Main_Service = DFUUUIDs.Main_Service;
                        Notify_Config = DFUUUIDs.Notify_Config;
                        Main_Characteristic_Notify = DFUUUIDs.Main_Characteristic_Notify;
                        Main_Characteristic_Write = DFUUUIDs.Main_Characteristic_Write;
                        blemode = true;
                        doDFUButton.setEnabled(true);
                        KLog("D6 was found in Bootloader mode you can Flash it by clicking on 'Do DFU Update'");
                    } else if (bledevice == 5) {
                        Main_Service = StockDFUUUIDs.Main_Service;
                        Notify_Config = StockDFUUUIDs.Notify_Config;
                        Main_Characteristic_Notify = StockDFUUUIDs.Main_Characteristic_Notify;
                        Main_Characteristic_Write = StockDFUUUIDs.Main_Characteristic_Write;
                        blemode = true;
                        blemodenordic = true;
                        doDFUButton.setEnabled(true);
                        KLog("BLE Device was found in Nordic Bootloader mode you can Flash it by clicking on 'Do DFU Update'");
                    } else if (bledevice == 6) {
                        Main_Service = FitUUIDs.Main_Service;
                        Notify_Config = FitUUIDs.Notify_Config;
                        Main_Characteristic_Notify = FitUUIDs.Main_Characteristic_Notify;
                        Main_Characteristic_Write = FitUUIDs.Main_Characteristic_Write;
                        blemode = false;
                        StartBootloaderButton.setEnabled(true);

                        BluetoothGatt gatt1 = mConnGatt;
                        if (gatt1 != null) {
                            BluetoothGattService service = gatt1.getService(FitUUIDs.Service_Decive_Information);
                            if (service != null) {
                                BluetoothGattCharacteristic characteristic = service.getCharacteristic(FitUUIDs.Characteristic_Manufacturer_Name);
                                if (characteristic != null) {
                                    Log.d("Read_Manufacturer_Info", gatt.readCharacteristic(characteristic) ? "1" : "0");
                                } else KLog("Characteristic not found");
                            } else KLog("Service not found");
                        } else KLog("Gatt not found");

                    } else if (bledevice == 7) {
                        Main_Service = StockSecureDFUUUIDs.Main_Service;
                        Notify_Config = StockSecureDFUUUIDs.Notify_Config;
                        Main_Characteristic_Notify = StockSecureDFUUUIDs.Main_Characteristic_Notify;
                        Main_Characteristic_Write = StockSecureDFUUUIDs.Main_Characteristic_Write;
                        blemode = true;
                        blemodenordic = true;
                        doDFUButton.setEnabled(true);
                        KLog("BLE Device was found in Nordic Secure Bootloader mode you can Flash it by clicking on 'Do DFU Update'");
                    } else {
                        Main_Service = D6UUIDs.Main_Service;
                        Notify_Config = D6UUIDs.Notify_Config;
                        Main_Characteristic_Notify = D6UUIDs.Main_Characteristic_Notify;
                        Main_Characteristic_Write = D6UUIDs.Main_Characteristic_Write;
                        blemode = true;
                        doDFUButton.setEnabled(true);
                        KLog("No correct Device was found, do you selected the right one? you can try to flash it anyway.");
                    }
                }
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            sendNextPart();
        }
    };

    void check_fit_version(String manu_name) {
        setNotifyCharacteristic(true);

        doDFUButton.setEnabled(false);
        StartBootloaderButton.setText("Select File");

        if (!updateStarted) {

            if (manu_name.equals("MOYOUNG-V2")) {
                KLog("V2 detected");
                Fit_version = 2;
            } else {
                KLog("V1 detected");
                Fit_version = 1;
            }
            KLog("DaFit Tracker Was found, please select the update file via 'Select File' be careful to select the right file as there is now way to verify it by this app. Do this on your own risk.");
            AlertDialog.Builder builder = new AlertDialog.Builder(DeviceActivity.this);
            builder.setTitle("Disclaimer")
                    .setMessage("Looks like you connected a DaFit Watch/Fitness Tracker.\n\nIt is important to notice that by flashing any Custom firmware or file you will lose your your warranty.\n\nEverything you do is at your own risk")
                    .setCancelable(false)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_device);
        mStatus = BluetoothProfile.STATE_DISCONNECTED;
        doDFUButton = findViewById(R.id.DoDfu);
        doDFUButton.setOnClickListener(this);
        StartBootloaderButton = findViewById(R.id.startBootloader);
        StartBootloaderButton.setOnClickListener(this);
        progressBar = findViewById(R.id.progressBar);
        percentText = findViewById(R.id.percentText);
        tvContent = findViewById(R.id.tv_content);
        scrollview = findViewById(R.id.scroll);
        StartBootloaderButton.setEnabled(false);
        doDFUButton.setEnabled(false);
    }

    boolean firstRun = false;

    @Override
    protected void onResume() {
        super.onResume();
        if (!firstRun) {
            runOnUiThread(() -> {
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        doDFUButton.setEnabled(true);
                        StartBootloaderButton.setEnabled(true);
                        init();
                    }
                }, 300);

            });
        } else {
            init();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mConnGatt != null) {
            if ((mStatus != BluetoothProfile.STATE_DISCONNECTING) && (mStatus != BluetoothProfile.STATE_DISCONNECTED)) {
                mConnGatt.disconnect();
            }
            mConnGatt.close();
            mConnGatt = null;
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.DoDfu) {
            if (bledevice != 6) {
                if (mConnGatt != null) {
                    Method refresh = null;
                    try {
                        refresh = mConnGatt.getClass().getMethod("refresh");
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    }
                    try {
                        assert refresh != null;
                        refresh.invoke(mConnGatt);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
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
                intent.putExtra(DeviceActivity.EXTRA_BLUETOOTH_MODE_NORDIC, blemodenordic);
                startActivity(intent);
            } else {
                startDaBootloader(0);
            }
        } else if (v.getId() == R.id.startBootloader) {
            if (bledevice == 1) {
                writeCharacteristic1("BT+UPGB\r\n");
                try {
                    sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                KLog(writeCharacteristic1("BT+RESET\r\n"));
                KLog("Look at the Tracker are there 3 Arrows? Great, now click on 'Do DFU Update' to Flash it.");
            } else if (bledevice == 2) {
                try {
                    writeCharacteristic1((char) 3 + "");
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
            } else if (bledevice == 6) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, READ_REQUEST_CODE);
            } else {
                KLog("Something went totally wrong, you shouldn't be able to press this button right now.");
            }

        }
    }

    public static class D6UUIDs {
        static final UUID Main_Characteristic_Write = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb");
        static final UUID Main_Characteristic_Notify = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb");
        static final UUID Notify_Config = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        static final UUID Main_Service = UUID.fromString("0000190a-0000-1000-8000-00805f9b34fb");
    }

    public static class DFUUUIDs {
        static final UUID Main_Characteristic_Write = UUID.fromString("00000005-0000-1000-8000-00805f9b34fb");
        static final UUID Main_Characteristic_Notify = UUID.fromString("00000006-0000-1000-8000-00805f9b34fb");
        static final UUID Notify_Config = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        static final UUID Main_Service = UUID.fromString("0000190c-0000-1000-8000-00805f9b34fb");
    }

    public static class StockDFUUUIDs {
        static final UUID Main_Characteristic_Write = UUID.fromString("00001532-1212-EFDE-1523-785FEABCD123");
        static final UUID Main_Characteristic_Notify = UUID.fromString("00001531-1212-EFDE-1523-785FEABCD123");
        static final UUID Notify_Config = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        static final UUID Main_Service = UUID.fromString("00001530-1212-EFDE-1523-785FEABCD123");
    }

    public static class StockSecureDFUUUIDs {
        static final UUID Main_Characteristic_Write = UUID.fromString("8ec90002-f315-4f60-9fb8-838830daea50");
        static final UUID Main_Characteristic_Notify = UUID.fromString("8ec90001-f315-4f60-9fb8-838830daea50");
        static final UUID Notify_Config = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        static final UUID Main_Service = UUID.fromString("0000fe59-0000-1000-8000-00805f9b34fb");
    }

    public static class EspruinoUUIDs {
        static final UUID Main_Characteristic_Write = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
        static final UUID Main_Characteristic_Notify = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
        static final UUID Notify_Config = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        static final UUID Main_Service = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    }

    public static class FitUUIDs {
        static final UUID Main_Characteristic_Write = UUID.fromString("0000fee2-0000-1000-8000-00805f9b34fb");
        static final UUID Main_Characteristic_Notify = UUID.fromString("0000fee3-0000-1000-8000-00805f9b34fb");
        static final UUID Service_Decive_Information = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
        static final UUID Characteristic_Manufacturer_Name = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");
        static final UUID Notify_Config = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        static final UUID Main_Service = UUID.fromString("0000feea-0000-1000-8000-00805f9b34fb");
    }


    public void filterFitResponse(byte[] data) {
        runOnUiThread(new Runnable() {
            public void run() {
                //KLog(bytesToString(data));
                if (data[0] == (byte) 0xFE && data[1] == (byte) 0xEA && data[2] == (byte) 0x10) {
                    cmdFitLength = data[3];
                    fullCMD = bytesToString(data);
                    receivedLength = data.length;
                } else if (receivedLength < cmdFitLength) {
                    receivedLength += data.length;
                    fullCMD += bytesToString(data);
                }
                if (receivedLength == cmdFitLength) {
                    //KLog("Got CMD, length: "+cmdFitLength);
                    if (updateStarted) {
                        KLog(fullCMD);
                        if (fullCMD.equals("FEEA1007630000") && !watchInUpdateMode) {
                            watchInUpdateMode = true;
                            KLog("DaFit in Bootloader mode, starting upload now");
                            doFitUpdate(0);
                        } else if (fullCMD.substring(0, 10).equals("FEEA100763") && watchInUpdateMode) {
                            int currPosOnWatch = ((data[5] & 255) << 8) + (data[6] & 255);
                            //KLog("Curr Watch Pos: "+currPosOnWatch);
                            doFitUpdate(currPosOnWatch);
                        } else if (fullCMD.substring(0, 14).equals("FEEA100963FFFF") && watchInUpdateMode) {
                            KLog("Got Last MSG with CRC: " + fullCMD.substring(14) + " File CRC: " + bytesToString(fullCRC));
                            if (fullCMD.substring(14).equals(bytesToString(fullCRC))) {
                                KLog("Update was successful, going to restart to Bootloader now.\nPlease press the Back button to reselect the Tracker that should now be in nordic Bootloader mode after it is done flashing itself.");
                                startDaBootloader(0);
                            }
                        }
                    } else {
                        KLog(fullCMD);
                    }
                }
            }
        });
    }

    public void filterResponse(String response1) {
        String response = response1.replaceAll("[^\\x20-\\x7E]", "");
        runOnUiThread(new Runnable() {
            public void run() {
                //KLog("Answer: " + response);
                if (response.length() >= 8) {
                    //KLog(response.substring(0,4));
                    if (bledevice == 1) {
                        //check if desay version string
                        if (response.substring(0, 6).equals("AT+VER"))
                            KLog("D6 Firmware Version: " + response.substring(7));
                        if (response.substring(0, 6).equals("BT+VER"))
                            KLog("D6 Bootloader Version: " + response.substring(7));
                    } else if (bledevice == 2) {
                        //check if espruino version string
                        if (response.substring(0, 4).equals("=\"2v"))
                            KLog("Espruino Version: " + response.substring(2, response.length() - 2));
                    } else if (bledevice == 7) {
                        //there shouldnt be any messages from the Nordic secure DFU Bootloader
                    } else if (bledevice == 3) {
                        //there shouldnt be any messages from the Nordic DFU Bootloader
                    } else {
                        //nothing to do here, we dont know the BLE device
                    }
                }
            }
        });
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

    public void startDaBootloader(int size) {
        updateStarted = false;
        rebootStarted = false;
        watchInUpdateMode = false;
        currentPartPos = -1;
        currPart = new byte[0];
        fullSend = new byte[0];
        cmdFitLength = 0;
        receivedLength = 0;
        fullCMD = "";
        progressBar.setProgress(0);
        fullCRC = new byte[0];
        percentText.setVisibility(View.GONE);
        try {
            sendFitCMD((byte) 0x63, intToByteArray(size));
            if (size > 0) rebootStarted = true;
            updateStarted = true;
            percentText.setVisibility(View.VISIBLE);
            fullCRC = crc16(loadedUpdateFile);
            if (size > 0) KLog("Send Bootloader Start");
            sleep(30);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void sendFitCMD(byte cmd, byte[] data) {
        byte[] startBytes = {(byte) 0xFE, (byte) 0xEA, ((Fit_version == 1) ? (byte) 0x10 : (byte) 0x20), (byte) 0x00, (byte) 0x00};
        startBytes[3] = (byte) (startBytes.length + data.length);
        startBytes[4] = cmd;
        byte[] c = new byte[startBytes.length + data.length];
        System.arraycopy(startBytes, 0, c, 0, startBytes.length);
        System.arraycopy(data, 0, c, startBytes.length, data.length);
        //KLog(bytesToString(c));
        String temp = writeCharacteristic2(c);
    }

    public String writeCharacteristic2(byte[] data) {
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

    @SuppressLint("SetTextI18n")
    public void doFitUpdate(int currWatchPos) {
        if (currWatchPos > currentPartPos) {
            currentPartPos = currWatchPos;
            if (loadedUpdateFile.length > 256) {
                currPart = Arrays.copyOfRange(loadedUpdateFile, 0, 256);
                loadedUpdateFile = Arrays.copyOfRange(loadedUpdateFile, 256, loadedUpdateFile.length);
            } else {
                currPart = loadedUpdateFile;
                loadedUpdateFile = new byte[0];
            }
        }
        int updatePercent = ((totalSizeUpdate - loadedUpdateFile.length) * 100 / totalSizeUpdate * 100) / 100;
        percentText.setText(updatePercent + "% " + (totalSizeUpdate - loadedUpdateFile.length) + " from " + totalSizeUpdate + " bytes");
        progressBar.setProgress(updatePercent);
        sendFitDFU(currPart);
    }

    public void sendFitDFU(byte[] data) {
        byte[] startBytes = {(byte) 0xFE, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        byte[] crc16 = crc16(data);
        startBytes[1] = crc16[0];
        startBytes[2] = crc16[1];
        startBytes[3] = (byte) data.length;
        byte[] c = new byte[startBytes.length + data.length];
        System.arraycopy(startBytes, 0, c, 0, startBytes.length);
        System.arraycopy(data, 0, c, startBytes.length, data.length);
        //KLog(bytesToString(c));
        String temp = writeCharacteristicDFUpre(c);
    }

    public static byte[] crc16(final byte[] buffer) {
        int crc = 0xFEEA;
        for (byte b : buffer) {
            crc = ((crc >>> 8) | (crc << 8)) & 0xffff;
            crc ^= (b & 0xff);
            crc ^= ((crc & 0xff) >> 4);
            crc ^= (crc << 12) & 0xffff;
            crc ^= ((crc & 0xFF) << 5) & 0xffff;
        }
        crc &= 0xffff;
        return new byte[]{(byte) ((crc >> 8) & 255), (byte) (crc & 255)};
    }

    public String writeCharacteristicDFUpre(byte[] data) {
        String answer = "";
        if (data.length > 20) {
            fullSend = Arrays.copyOfRange(data, 20, data.length);
            byte[] dataWrite = Arrays.copyOfRange(data, 0, 20);
            answer = writeCharacteristicDFU(dataWrite);
        } else {
            answer = writeCharacteristicDFU(data);
        }
        return answer;
    }

    public void sendNextPart() {
        if (fullSend != null) {
            if (fullSend.length > 0) if (fullSend.length > 20) {
                byte[] dataWrite = Arrays.copyOfRange(fullSend, 0, 20);
                fullSend = Arrays.copyOfRange(fullSend, 20, fullSend.length);
                writeCharacteristicDFU(dataWrite);
            } else {
                byte[] dataWrite = Arrays.copyOfRange(fullSend, 0, fullSend.length);
                fullSend = new byte[0];
                writeCharacteristicDFU(dataWrite);
            }
        }
    }

    public String writeCharacteristicDFU(byte[] data) {
        final UUID DFU_Characteristic_Write = UUID.fromString("0000fee5-0000-1000-8000-00805f9b34fb");
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
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(DFU_Characteristic_Write);
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

    public static byte[] intToByteArray(int a) {
        byte[] ret = new byte[4];
        ret[3] = (byte) (a & 0xFF);
        ret[2] = (byte) ((a >> 8) & 0xFF);
        ret[1] = (byte) ((a >> 16) & 0xFF);
        ret[0] = (byte) ((a >> 24) & 0xFF);
        return ret;
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
                        if (!updateStarted) KLog("BLE connection successful");
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
        tvContent.append("\n" + TEXT + "\n");
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
        if (!BleUtil.isBLESupported(this)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
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
    /*    if(!firstRun) {
            firstRun = true;
            try {
                mBTAdapter.disable();
                sleep(3000);
                mBTAdapter.enable();
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }*/
        if (mDevice == null) {
            mDevice = getBTDeviceExtra();
            if (mDevice == null) {
                finish();
                return;
            }
        }

        StartBootloaderButton.setEnabled(false);

        if ((mConnGatt == null) && (mStatus == BluetoothProfile.STATE_DISCONNECTED)) {
            mConnGatt = mDevice.connectGatt(this, false, mGattcallback);
            mStatus = BluetoothProfile.STATE_CONNECTING;
        } else {
            if (mConnGatt != null) {
                Method refresh = null;
                try {
                    refresh = mConnGatt.getClass().getMethod("refresh");
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }
                try {
                    assert refresh != null;
                    refresh.invoke(mConnGatt);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
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


    public static String bytesToString(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder(bytes.length);
        int length = bytes.length;
        for (byte aByte : bytes) {
            stringBuilder.append(String.format("%02X", aByte));
        }
        return stringBuilder.toString();
    }

    byte[] fullyReadFileToBytes(Uri f) throws IOException {
        InputStream fis = getContentResolver().openInputStream(f);
        assert fis != null;
        int size = fis.available();
        byte[] bytes = new byte[size];
        byte[] tmpBuff = new byte[size];
        try {

            int read = fis.read(bytes, 0, size);
            if (read < size) {
                int remain = size - read;
                while (remain > 0) {
                    read = fis.read(tmpBuff, 0, remain);
                    System.arraycopy(tmpBuff, 0, bytes, size - remain, read);
                    remain -= read;
                }
            }
        } finally {
            fis.close();
        }
        return bytes;
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (Objects.equals(uri.getScheme(), "content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                assert cursor != null;
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            assert result != null;
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}