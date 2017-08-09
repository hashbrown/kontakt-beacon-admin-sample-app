package com.kontakt.sample.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.kontakt.sdk.android.ble.configuration.ScanMode;
import com.kontakt.sdk.android.ble.configuration.ScanPeriod;
import com.kontakt.sdk.android.ble.connection.OnServiceReadyListener;
import com.kontakt.sdk.android.ble.exception.ScanError;
import com.kontakt.sdk.android.ble.filter.eddystone.EddystoneFilter;
import com.kontakt.sdk.android.ble.manager.ProximityManager;
import com.kontakt.sdk.android.ble.manager.ProximityManagerFactory;
import com.kontakt.sdk.android.ble.manager.listeners.EddystoneListener;
import com.kontakt.sdk.android.ble.manager.listeners.IBeaconListener;
import com.kontakt.sdk.android.ble.manager.listeners.ScanStatusListener;
import com.kontakt.sdk.android.ble.manager.listeners.simple.SimpleEddystoneListener;
import com.kontakt.sdk.android.ble.manager.listeners.simple.SimpleIBeaconListener;
import com.kontakt.sdk.android.common.profile.IBeaconDevice;
import com.kontakt.sdk.android.common.profile.IBeaconRegion;
import com.kontakt.sdk.android.common.profile.IEddystoneDevice;
import com.kontakt.sdk.android.common.profile.IEddystoneNamespace;
import com.kontakt.sdk.android.common.profile.RemoteBluetoothDevice;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class BackgroundScanService extends Service {

  public static final String TAG = "BackgroundScanService";
  public static final String ACTION_DEVICE_DISCOVERED = "DeviceDiscoveredAction";
  public static final String EXTRA_DEVICE = "DeviceExtra";
  public static final String EXTRA_DEVICES_COUNT = "DevicesCountExtra";

  private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(30);

  private final Handler handler = new Handler();
  private ProximityManager proximityManager;
  private boolean isRunning; // Flag indicating if service is already running.
  private int devicesCount; // Total discovered devices count

  @Override
  public void onCreate() {
    super.onCreate();
    setupProximityManager();
    isRunning = false;
  }

  private void setupProximityManager() {
    //Create proximity manager instance
    proximityManager = ProximityManagerFactory.create(this);

    ScanPeriod vitalsBackgroundPeriod = ScanPeriod.create(30000, 2200);

    //Configure proximity manager basic options
    proximityManager.configuration()
        //Using ranging for continuous scanning or MONITORING for scanning with intervals
        .scanPeriod(vitalsBackgroundPeriod)
        //Using BALANCED for best performance/battery ratio
        .scanMode(ScanMode.LOW_POWER);

    //Setting up iBeacon and Eddystone listeners

    proximityManager.filters().eddystoneFilter(new EddystoneFilter() {
      @Override
      public boolean apply(IEddystoneDevice eddystone) {
        Log.d(TAG, "Possible device=" + eddystone);
        String namespace = eddystone != null?eddystone.getNamespace():null;
        return namespace != null && namespace.equals("3d92f9630d8f584de4d9");
      }
    });
//    proximityManager.setIBeaconListener(createIBeaconListener());
    proximityManager.setEddystoneListener(createEddystoneListener());

    proximityManager.setScanStatusListener(new ScanStatusListener() {
      @Override
      public void onScanStart() {
        Log.d(TAG, "SCAN START");
      }

      @Override
      public void onScanStop() {
        Log.d(TAG, "SCAN STOP");
      }

      @Override
      public void onScanError(final ScanError scanError) {
        Log.d(TAG, "SCAN ERROR " + scanError.getMessage());
      }

      @Override
      public void onMonitoringCycleStart() {

      }

      @Override
      public void onMonitoringCycleStop() {

      }
    });
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    //Check if service is already active
    if (isRunning) {
      Toast.makeText(this, "Service is already running.", Toast.LENGTH_SHORT).show();
      return START_STICKY;
    }
    startScanning();
    isRunning = true;
    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void startScanning() {
    proximityManager.connect(new OnServiceReadyListener() {
      @Override
      public void onServiceReady() {
        proximityManager.startScanning();
        devicesCount = 0;
        Toast.makeText(BackgroundScanService.this, "Scanning service started.", Toast.LENGTH_SHORT).show();
      }
    });
//    stopAfterDelay();
  }

  private void stopAfterDelay() {
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        proximityManager.disconnect();
        stopSelf();
      }
    }, TIMEOUT);
  }

  private IBeaconListener createIBeaconListener() {
    return new SimpleIBeaconListener() {
      @Override
      public void onIBeaconDiscovered(IBeaconDevice ibeacon, IBeaconRegion region) {
        onDeviceDiscovered(ibeacon);
        Log.i(TAG, "onIBeaconDiscovered: " + ibeacon.toString());
      }
    };
  }

  private EddystoneListener createEddystoneListener() {
    return new EddystoneListener() {
      @Override
      public void onEddystoneDiscovered(IEddystoneDevice eddystone, IEddystoneNamespace namespace) {
        Log.i(TAG, "onEddystoneDiscovered: " + eddystone.toString());
      }

      @Override
      public void onEddystonesUpdated(List<IEddystoneDevice> eddystones, IEddystoneNamespace namespace) {
        Log.i(TAG, "onEddystonesUpdated: " + eddystones.size());
      }

      @Override
      public void onEddystoneLost(IEddystoneDevice eddystone, IEddystoneNamespace namespace) {
        Log.e(TAG, "onEddystoneLost: " + eddystone.toString());
      }
    };
  }

  private void onDeviceDiscovered(RemoteBluetoothDevice device) {
    devicesCount++;
    //Send a broadcast with discovered device
    Intent intent = new Intent();
    intent.setAction(ACTION_DEVICE_DISCOVERED);
    intent.putExtra(EXTRA_DEVICE, device);
    intent.putExtra(EXTRA_DEVICES_COUNT, devicesCount);
    sendBroadcast(intent);
  }

  @Override
  public void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    if (proximityManager != null) {
      proximityManager.disconnect();
      proximityManager = null;
    }
    Toast.makeText(BackgroundScanService.this, "Scanning service stopped.", Toast.LENGTH_SHORT).show();
    super.onDestroy();
  }
}
