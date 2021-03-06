package yiweihan.ble_advertiser_scanner;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelUuid;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "BLEP";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final String ADVERTISE_UUID_16 = "A490"; // random 16 bit UUID to emulate BLE standard

    private ParcelUuid mServiceUuid;

    private TextView mAdvertiseStatus;
    private TextView mScanStatus;
    private TextView mScanResult;

    private Button mStartAdvertiseButton;
    private Button mStopAdvertiseButton;
    private Button mStartScanButton;
    private Button mStopScanButton;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mAdvertiser;
    private BluetoothLeScanner mScanner;

    private Context mContext = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAdvertiseStatus = findViewById(R.id.advertise_status_textview);
        mScanStatus = findViewById(R.id.scan_status_textview);
        mScanResult = findViewById(R.id.scan_result_textview);

        mStartAdvertiseButton = findViewById(R.id.start_advertise_button);
        mStopAdvertiseButton = findViewById(R.id.stop_advertise_button);
        mStartScanButton = findViewById(R.id.start_scan_button);
        mStopScanButton = findViewById(R.id.stop_scan_button);

        mStartAdvertiseButton.setOnClickListener(this);
        mStopAdvertiseButton.setOnClickListener(this);
        mStartScanButton.setOnClickListener(this);
        mStopScanButton.setOnClickListener(this);

        mServiceUuid = new ParcelUuid(UUID.fromString(getString(R.string.ble_service_uuid_base, ADVERTISE_UUID_16)));

        final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null || ((mBluetoothAdapter = bluetoothManager.getAdapter()) == null)) {
            Toast.makeText(this, "Bluetooth could not be initialised", Toast.LENGTH_LONG).show();
            Log.d(TAG, "bluetooth adapter could not be initialised");
            finishAndRemoveTask();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((TextView)findViewById(R.id.device_name_textview)).setText(getString(R.string.device_name_text, mBluetoothAdapter.getName()));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(this, "Bluetooth must be enabled to continue", Toast.LENGTH_LONG).show();
            Log.d(TAG, "user refused to enable bluetooth");
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if ((id == R.id.start_advertise_button || id == R.id.start_scan_button) && !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        switch (id) {
            case R.id.start_advertise_button:
                startAdvertise();
                break;
            case R.id.stop_advertise_button:
                stopAdvertise();
                break;
            case R.id.start_scan_button:
                startScan();
                break;
            case R.id.stop_scan_button:
                stopScan();
                break;
            default:
                break;
        }
    }

    private void startScan() {
        mScanner = mBluetoothAdapter.getBluetoothLeScanner();

        List<ScanFilter> filters = new ArrayList<>();

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(mServiceUuid)
                .build();
        filters.add(filter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        mScanStatus.setText(R.string.scan_status_scanning);
        mStartScanButton.setVisibility(View.GONE);
        mStopScanButton.setVisibility(View.VISIBLE);
        mScanner.startScan(filters, settings, mScanCallback);
    }

    private void stopScan() {
        if (mScanner != null) {
            mScanner.stopScan(mScanCallback);
            Log.d(TAG, "scanning stopped");
        }
        mScanner = null;
        mScanStatus.setText(R.string.scan_status_not_scanning);
        mStartScanButton.setVisibility(View.VISIBLE);
        mStopScanButton.setVisibility(View.GONE);
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice device;
            if (result == null || (device = result.getDevice()) == null) {
                return;
            }

            String deviceName = device.getName();
            if (deviceName == null) {
                deviceName = "";
            }

            String data = "";
            ScanRecord record = result.getScanRecord();
            if (record != null) {
                List<ParcelUuid> serviceUuids = record.getServiceUuids();
                if (serviceUuids != null && serviceUuids.size() > 0) {
                    byte[] serviceData = record.getServiceData(serviceUuids.get(0));
                    if (serviceData != null) {
                        data = new String(serviceData, Charset.forName("UTF-8"));
                    }
                }
            }

            mScanResult.setText(getString(R.string.scan_result, deviceName, data));
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d(TAG, "onScanFailed code " + errorCode);
            Toast.makeText(mContext, "Scan failed: code " + errorCode, Toast.LENGTH_LONG).show();
            stopScan();
            super.onScanFailed(errorCode);
        }
    };

    private void enableAdvertiseConfigs(boolean enable) {
        mStartAdvertiseButton.setEnabled(enable);
        mStopAdvertiseButton.setEnabled(enable);
        findViewById(R.id.include_name_switch).setEnabled(enable);
        findViewById(R.id.service_data_input).setEnabled(enable);
        findViewById(R.id.include_service_switch).setEnabled(enable);
    }

    private void startAdvertise() {
        // disable advertising UI interaction while changing advertisement state
        enableAdvertiseConfigs(false);

        if ((mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser()) == null) {
            Toast.makeText(this, "BLE advertising not supported!", Toast.LENGTH_LONG).show();
            Log.d(TAG, "multi advertising not supported");
            finishAndRemoveTask();
            return;
        }

        // start advertising
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW)
                .setConnectable(true)
                .build();

        boolean includeDeviceName = ((Switch)findViewById(R.id.include_name_switch)).isChecked();

        boolean includeServiceData = ((Switch)findViewById(R.id.include_service_switch)).isChecked();
        String serviceData = includeServiceData ? ((EditText)findViewById(R.id.service_data_input)).getText().toString() : "";

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(includeDeviceName)
                .addServiceUuid(mServiceUuid)
                .addServiceData(mServiceUuid, serviceData.getBytes(Charset.forName("UTF-8")))
                .build();

        Log.d(TAG, "advertising: " + data.toString());
        mAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);
    }

    private void stopAdvertise() {
        if (mAdvertiser != null) {
            mAdvertiser.stopAdvertising(mAdvertiseCallback);
            Log.d(TAG, "advertising stopped");
        }
        mAdvertiser = null;
        mAdvertiseStatus.setText(R.string.advertise_status_not_advertising);
        mStopAdvertiseButton.setVisibility(View.GONE);
        mStartAdvertiseButton.setVisibility(View.VISIBLE);
        enableAdvertiseConfigs(true);
    }

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(TAG, "advertising onStartSuccess");

            // update ui to show advertising status
            mAdvertiseStatus.setText(R.string.advertise_status_advertising);
            mStartAdvertiseButton.setVisibility(View.GONE);
            mStopAdvertiseButton.setVisibility(View.VISIBLE);
            mStartAdvertiseButton.setEnabled(true);
            mStopAdvertiseButton.setEnabled(true);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.d(TAG, "advertising onStartFailure code " + errorCode);
            Toast.makeText(mContext, "Advertise failed: code " + errorCode, Toast.LENGTH_LONG).show();
            stopAdvertise();
            super.onStartFailure(errorCode);
        }
    };
}
