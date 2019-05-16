package com.atcnetz.ble.readwrite;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.support.annotation.RequiresApi;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import com.atcnetz.ble.readwrite.dfu.DfuService;


import java.util.Locale;

import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;

import static com.atcnetz.ble.readwrite.DeviceActivity.EXTRA_BLUETOOTH_DEVICE;
import static com.atcnetz.ble.readwrite.DeviceActivity.EXTRA_BLUETOOTH_MODE;
import static no.nordicsemi.android.dfu.internal.scanner.BootloaderScanner.ADDRESS_DIFF;

public class DFUActivity  extends Activity implements View.OnClickListener {
    private static final int READ_REQUEST_CODE = 42;
    private BluetoothDevice mDevice;
    TextView tvContent;
    ScrollView scrollview;
    Button Button1;
    Uri uri = null;
    String selectedMac;
    String selectedName;
    ProgressBar progressBar;
    TextView textView;
    RadioButton radioButton;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                uri = resultData.getData();
                KLog("Selected file: " + getFileName(uri));
                startdfu();
            }
        }
    }
    private final DfuProgressListener mDfuProgressListener = new DfuProgressListenerAdapter() {
        @Override
        public void onDeviceConnecting(final String deviceAddress) {
            KLog("Connected "+deviceAddress);
        }

        @Override
        public void onDfuProcessStarting(final String deviceAddress) {
            KLog("Starting flashing "+deviceAddress);
            KLog("Please wait, this takes up to 5 minutes.");
        }

        @Override
        public void onEnablingDfuMode(final String deviceAddress) {
            KLog("Enabling "+deviceAddress);
        }

        @Override
        public void onFirmwareValidating(final String deviceAddress) {
            KLog("Validating "+deviceAddress);
        }

        @Override
        public void onDeviceDisconnecting(final String deviceAddress) {
            KLog("Disconnected "+deviceAddress);
        }

        @Override
        public void onDfuCompleted(final String deviceAddress) {
            writepercent(100);
            KLog("Completed "+deviceAddress);
            KLog("---------------------");
            KLog("You can now use the D6 Tracker with the new firmware");
            KLog("---------------------");
        }

        @Override
        public void onDfuAborted(final String deviceAddress) {
            writepercent(0);
            KLog("Aborted "+deviceAddress);
        }

        @Override
        public void onProgressChanged(final String deviceAddress, final int percent, final float speed, final float avgSpeed, final int currentPart, final int partsTotal) {
            writepercent(percent);
        }

        @Override
        public void onError(final String deviceAddress, final int error, final int errorType, final String message) {
            writepercent(0);
            KLog("Error "+message);
        }
    };

    void writepercent(int percent){
        textView.setText(percent+"%");
        progressBar.setProgress(percent);
    }
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button) {
            writepercent(0);
            startthedfu();
        }}

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dfu);
        tvContent = findViewById(R.id.tv_content);
        scrollview = findViewById(R.id.scroll);
        textView = findViewById(R.id.textView);
        Button1 = findViewById(R.id.button);
        Button1.setOnClickListener(this);
        radioButton = findViewById(R.id.radioButton);
        progressBar = findViewById(R.id.progressBar);
        KLog("Please select a file you want to flash via DFU\n\n flashing will start after the flashing after file selection");
        DfuServiceListenerHelper.registerProgressListener(this, mDfuProgressListener);
        selectedMac = getBTDeviceExtra();
        selectedName = mDevice.getName();
        KLog("Selectet device: " + selectedName + " : " + selectedMac);
    }

    private String getBTDeviceExtra() {
        Intent intent = getIntent();
        if (intent == null) {
            return null;
        }

        Bundle extras = intent.getExtras();
        if (extras == null) {
            return null;
        }
        mDevice = extras.getParcelable(EXTRA_BLUETOOTH_DEVICE);
        boolean blemode = extras.getBoolean(EXTRA_BLUETOOTH_MODE);
        if (mDevice == null) {
            finish();
        }
        if (blemode) {
            return mDevice.getAddress();
        }else{
            final String firstBytes = mDevice.getAddress().substring(0, 15);
            final String lastByte = mDevice.getAddress().substring(15);
            final String lastByteIncremented = String.format(Locale.US, "%02X", (Integer.valueOf(lastByte, 16) + ADDRESS_DIFF) & 0xFF);
            return firstBytes + lastByteIncremented;
        }
    }

    void startthedfu(){
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, READ_REQUEST_CODE);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        DfuServiceListenerHelper.unregisterProgressListener(this, mDfuProgressListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        DfuServiceListenerHelper.registerProgressListener(this, mDfuProgressListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        DfuServiceListenerHelper.unregisterProgressListener(this, mDfuProgressListener);
    }


    void startdfu(){
        KLog("Started DFU");
        final DfuServiceInitiator starter = new DfuServiceInitiator(selectedMac)
                .setDeviceName(selectedName).setKeepBond(false)
                .setForceDfu(false).setPacketsReceiptNotificationsEnabled(true)
                .setPacketsReceiptNotificationsValue(12);
        starter.setZip(uri , uri.getPath());
        if(radioButton.isChecked())
            starter.setScope(DfuServiceInitiator.SCOPE_SYSTEM_COMPONENTS);

        starter.start(this, DfuService.class);
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
                    DFUActivity.this.runOnUiThread(new Runnable() {
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
}
