package com.atcnetz.patc.daatc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import com.atcnetz.patc.daatc.dfu.DfuService;
import com.atcnetz.patc.util.BleUtil;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;

import static com.atcnetz.patc.daatc.DeviceActivity.EXTRA_BLUETOOTH_DEVICE;
import static com.atcnetz.patc.daatc.DeviceActivity.EXTRA_BLUETOOTH_MODE;
import static com.atcnetz.patc.daatc.DeviceActivity.EXTRA_BLUETOOTH_MODE_NORDIC;
import static java.lang.Thread.sleep;
import static no.nordicsemi.android.dfu.internal.scanner.BootloaderScanner.ADDRESS_DIFF;

public class DFUActivity extends Activity implements View.OnClickListener {
    private static final int READ_REQUEST_CODE = 452;
    private BluetoothDevice mDevice;
    TextView tvContent;
    ScrollView scrollview;
    Button Button1;
    Uri uri = null;
    String selectedMac;
    String selectedName;
    ProgressBar progressBar;
    TextView textView;
    CheckBox checkBox;
    CheckBox checkBox1;
    String speedNow = "0,0";

    static final UUID D6_DFU_SERVICE_UUID = new UUID(0x0000190c00001000L, 0x800000805f9B34FBL);
    static final UUID D6_DFU_CONTROL_POINT_UUID = new UUID(0x0000000600001000L, 0x800000805f9B34FBL);
    static final UUID D6_DFU_PACKET_UUID = new UUID(0x0000000500001000L, 0x800000805f9B34FBL);
    static final UUID D6_DFU_VERSION_UUID = new UUID(0x0000000800001000L, 0x800000805f9B34FBL);

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                uri = resultData.getData();
                KLog("Selected file: " + getFileName(uri) + "\nFileSize: " + getFileSize(uri) + "Kb");
                runOnUiThread(() -> {
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startdfu(); }
                    }, 300);

                });
            }
        }
    }

    private final DfuProgressListener mDfuProgressListener = new DfuProgressListenerAdapter() {
        @Override
        public void onDeviceConnecting(final String deviceAddress) {
            KLog("Connected " + deviceAddress);
        }

        @Override
        public void onDfuProcessStarting(final String deviceAddress) {
            KLog("Starting flashing " + deviceAddress);
            KLog("Please wait, this takes up to 5 minutes.");
        }

        @Override
        public void onEnablingDfuMode(final String deviceAddress) {
            KLog("Enabling " + deviceAddress);
        }

        @Override
        public void onFirmwareValidating(final String deviceAddress) {
            KLog("Validating " + deviceAddress);
        }

        @Override
        public void onDeviceDisconnecting(final String deviceAddress) {
            KLog("Disconnected " + deviceAddress);
        }

        @Override
        public void onDfuCompleted(final String deviceAddress) {
            writepercent(100);
            KLog("Completed " + deviceAddress);
            KLog("---------------------");
            KLog("You can now use the Fitness Tracker with the new firmware");
            KLog("---------------------");
        }

        @Override
        public void onDfuAborted(final String deviceAddress) {
            speedNow = "0,0";
            writepercent(0);
            KLog("Aborted " + deviceAddress);
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void onProgressChanged(final String deviceAddress, final int percent, final float speed, final float avgSpeed, final int currentPart, final int partsTotal) {
            speedNow = String.format("%.2f", avgSpeed);
            writepercent(percent);
        }

        @Override
        public void onError(final String deviceAddress, final int error, final int errorType, final String message) {
            speedNow = "0,0";
            writepercent(0);
            KLog("Error " + message);
        }
    };

    @SuppressLint("SetTextI18n")
    void writepercent(int percent) {
        textView.setText(percent + "% - (" + speedNow + " kB/s)");
        progressBar.setProgress(percent);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button) {
            writepercent(0);
            startthedfu();
        }
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
                if (!result.toLowerCase().endsWith(".zip".toLowerCase())) result = result + ".zip";
            }
        }
        return result;
    }

    public String getFileSize(Uri uri) {
        String result = null;
        if (Objects.equals(uri.getScheme(), "content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = String.valueOf(cursor.getInt(cursor.getColumnIndex(OpenableColumns.SIZE)));
                }
            } finally {
                assert cursor != null;
                cursor.close();
            }
        } else result = "unknown";
        return result;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DfuServiceInitiator.createDfuNotificationChannel(this);
            final NotificationChannel channel = new NotificationChannel("connected_device_channel", "Background connections", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows a notification when a device is connected in background.");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        }
        setContentView(R.layout.activity_dfu);
        tvContent = findViewById(R.id.tv_content);
        scrollview = findViewById(R.id.scroll);
        textView = findViewById(R.id.textView);
        Button1 = findViewById(R.id.button);
        Button1.setOnClickListener(this);
        checkBox = findViewById(R.id.checkBox);
        checkBox1 = findViewById(R.id.checkBox1);
        progressBar = findViewById(R.id.progressBar);
        KLog("Please select a file you want to flash via DFU\n\nFlashing will start after the file selection\nIf you want to flash a device with nordic Bootloader please check \"Use Nordic Bootloader\"\n\n\nIf it should fail to Upload try to Enable/Disable Bluetooth and Force Close this App, you need to have GPS enabled\n");
        DfuServiceListenerHelper.registerProgressListener(this, mDfuProgressListener);
        selectedMac = getBTDeviceExtra();
        selectedName = mDevice.getName();
        KLog("Selected device: " + selectedName + " : " + selectedMac);
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
        boolean blemodenordic = extras.getBoolean(EXTRA_BLUETOOTH_MODE_NORDIC);
        if (blemodenordic) checkBox.setChecked(true);
        if (mDevice == null) {
            finish();
        }
        if (blemode) {
            return mDevice.getAddress();
        } else {
            final String firstBytes = mDevice.getAddress().substring(0, 15);
            final String lastByte = mDevice.getAddress().substring(15);
            final String lastByteIncremented = String.format(Locale.US, "%02X", (Integer.valueOf(lastByte, 16) + ADDRESS_DIFF) & 0xFF);
            return firstBytes + lastByteIncremented;
        }
    }

    void startthedfu() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        //intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DfuServiceListenerHelper.unregisterProgressListener(this, mDfuProgressListener);
    }

    @Override
    protected void onResume() {
        speedNow = "0,0";
        writepercent(0);
        super.onResume();
        DfuServiceListenerHelper.registerProgressListener(this, mDfuProgressListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        DfuServiceListenerHelper.unregisterProgressListener(this, mDfuProgressListener);
    }

    void startdfu() {
        KLog("Started DFU");

        BluetoothAdapter mBTAdapter = null;

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
        if (checkBox1.isChecked()){
            try {
                mBTAdapter.disable();
                sleep(3000);
                mBTAdapter.enable();
                sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }}

        final DfuServiceInitiator starter = new DfuServiceInitiator(selectedMac);
        starter.setDeviceName(selectedName);
        starter.setKeepBond(true);
        starter.setForceDfu(true);
        starter.setPacketsReceiptNotificationsEnabled(true);
        starter.setPacketsReceiptNotificationsValue(12);
        starter.setZip(uri, getFileName(uri));
        starter.setNumberOfRetries(3);
        if (!checkBox.isChecked())
            starter.setCustomUuidsForLegacyDfu(D6_DFU_SERVICE_UUID, D6_DFU_CONTROL_POINT_UUID, D6_DFU_PACKET_UUID, D6_DFU_VERSION_UUID);
        starter.start(this, DfuService.class);
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
