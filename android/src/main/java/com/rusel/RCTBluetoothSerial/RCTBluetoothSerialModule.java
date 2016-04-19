package com.rusel.RCTBluetoothSerial;

import java.io.UnsupportedEncodingException;
import java.util.Set;
import javax.annotation.Nullable;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class RCTBluetoothSerialModule extends ReactContextBaseJavaModule {

    private BluetoothAdapter bluetoothAdapter;
    private RCTBluetoothSerialService bluetoothSerialService;
    private final ReactApplicationContext _reactContext;

    // Debugging
    private static final String TAG = "BluetoothSerial";
    private static final boolean D = true;

    // Message types sent from the RCTBluetoothSerialService
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_CONN_SUCCESS = 4;
    public static final int MESSAGE_CONN_FAILED = 5;
    public static final int MESSAGE_CONN_LOST = 6;

    // Other stuff
    private Promise mEnabledPromise;
    private Promise mConnectedPromise;
    private static Boolean SUBSCRIBED = false;
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private StringBuffer buffer = new StringBuffer();
    private String delimiter;

    public RCTBluetoothSerialModule(ReactApplicationContext reactContext) {
        super(reactContext);
        _reactContext = reactContext;

        Log.d(TAG, "Bluetooth module started");

        if (bluetoothAdapter == null) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (bluetoothSerialService == null) {
            bluetoothSerialService = new RCTBluetoothSerialService(this);
        }
    }

    @Override
    public String getName() {
        return "BluetoothSerial";
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {

            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "User enabled Bluetooth");
                if (mEnabledPromise != null) {
                    mEnabledPromise.resolve(true);
                }
            } else {
                Log.d(TAG, "User did *NOT* enable Bluetooth");
                if (mEnabledPromise != null) {
                    mEnabledPromise.reject("User did not enable Bluetooth");
                }
            }

            mEnabledPromise = null;
        }
    }

    public void onDestroy() {
        if (bluetoothSerialService != null) {
            bluetoothSerialService.stop();
        }
    }

    // Methods Available from JS
    @ReactMethod
    public void list(Promise promise) {
        WritableArray deviceList = Arguments.createArray();
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device : bondedDevices) {
            deviceList.pushMap(deviceToWritableMap(device));
        }
        promise.resolve(deviceList);
    }

    @ReactMethod
    public void discoverUnpairedDevices(final Promise promise) {
        Log.d(TAG, "Discover unpaired called");

        final BroadcastReceiver discoverReceiver = new BroadcastReceiver() {

            private WritableArray unpairedDevices = Arguments.createArray();
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "onReceive called");
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    WritableMap d = deviceToWritableMap(device);
                    unpairedDevices.pushMap(d);
                    // throws com.facebook.react.bridge.ObjectAlreadyConsumedException: Map to push already consumed
                    // not sure why, needs further investigation
                    //Log.d(TAG, "About to send event");
                    //try {
                    //    sendEvent(_reactContext, "deviceDiscovered", d);
                    //} catch (Exception e){
                    //    Log.d(TAG, "Sending event failed", e);
                    //}
                    //Log.d(TAG, "Event sent");
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    Log.d(TAG, "Discovery finished");

                    promise.resolve(unpairedDevices);
                    getCurrentActivity().unregisterReceiver(this);
                }
            }
        };

        Activity activity = getCurrentActivity();
        activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        bluetoothAdapter.startDiscovery();
    }

    @ReactMethod
    public void enable(Promise promise) {
        mEnabledPromise = promise;
        Activity currentActivity = getCurrentActivity();
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        currentActivity.startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH);
    }

    @ReactMethod
    public void connect(String id, boolean secure, Promise promise) {
        mConnectedPromise = promise;
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(id);
        if (device != null) {
            bluetoothSerialService.connect(device, secure);
        } else {
            promise.reject("Could not connect to " + id);
        }
    }

    @ReactMethod
    public void disconnect(Promise promise) {
        bluetoothSerialService.stop();
        promise.resolve(true);
    }

    @ReactMethod
    public void write(String message, Promise promise) {
        byte[] data = message.getBytes();
        bluetoothSerialService.write(data);
        promise.resolve(true);
    }

    @ReactMethod
    public void available(Promise promise) {
        promise.resolve(buffer.length());
    }

    @ReactMethod
    public void read(Promise promise) {
        int length = buffer.length();
        String data = buffer.substring(0, length);
        buffer.delete(0, length);
        promise.resolve(data);
    }

    @ReactMethod
    public void readUntil(String c, Promise promise) {
        String data = "";
        int index = buffer.indexOf(c, 0);
        if (index > -1) {
            data = buffer.substring(0, index + c.length());
            buffer.delete(0, index + c.length());
        }
        promise.resolve(data);
    }

    @ReactMethod
    public void isEnabled(Promise promise) {
        promise.resolve(bluetoothAdapter.isEnabled());
    }

    @ReactMethod
    public Boolean isConnected(Promise promise) {
        promise.resolve(bluetoothSerialService.getState() == RCTBluetoothSerialService.STATE_CONNECTED);
    }

    @ReactMethod
    public void clear(Promise promise) {
        buffer.setLength(0);
        promise.resolve(true)
    }

    @ReactMethod
    public void subscribe(String delimiter, Promise promise) {
        delimiter = delimiter;
        SUBSCRIBED = true;
        promise.resolve(true)
    }

    @ReactMethod
    public void unsubscribe(Promise promise) {
        delimiter = null;
        SUBSCRIBED = false;
        promise.resolve(true)
    }

    @ReactMethod
    public void setAdapterName(String newName, Promise promise) {
        bluetoothAdapter.setName(newName);
        promise.resolve(true)
    }

    // Private methods
    private WritableMap deviceToWritableMap(BluetoothDevice device) {
        WritableMap params = Arguments.createMap();
        params.putString("name", device.getName());
        params.putString("address", device.getAddress());
        params.putString("id", device.getAddress());
        if (device.getBluetoothClass() != null) {
            params.putInt("class", device.getBluetoothClass().getDeviceClass());
        }
        return params;
    }


    public void receiveMessage(int msgType, String msg) {
        if(D) Log.i(TAG, "MESSAGE: " + msg);
        switch (msgType) {
            case MESSAGE_CONN_SUCCESS:
                notifyConnectionSuccess(msg);
                break;
            case MESSAGE_CONN_FAILED:
                notifyConnectionFailed(msg);
                break;
            case MESSAGE_CONN_LOST:
                notifyConnectionLost(msg);
                break;
            case MESSAGE_READ:
                buffer.append((String)msg);
                if (SUBSCRIBED == true) {
                    sendDataToSubscriber();
                }
                break;
            case MESSAGE_WRITE:
                //  byte[] writeBuf = (byte[]) msg.obj;
                //  String writeMessage = new String(writeBuf);
                //  Log.i(TAG, "Wrote: " + writeMessage);
                break;
            case MESSAGE_STATE_CHANGE:
                break;
        }
    }

    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        Log.d(TAG, "Sending event");
        reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
    }

    private void notifyConnectionSuccess(String msg) {
        WritableMap params = Arguments.createMap();
        params.putString("message", msg);
        if (mConnectedPromise != null) {
            mConnectedPromise.resolve(params);
        }
    }

    private void notifyConnectionFailed(String msg) {
        if (mConnectedPromise != null) {
            mConnectedPromise.reject(msg);
        }
    }

    private void notifyConnectionLost(String msg) {
        WritableMap params = Arguments.createMap();
        params.putString("message", msg);
        sendEvent(_reactContext, "connectionLost", params);
    }

    private void sendDataToSubscriber() {
        String data = readUntil(delimiter);
        if (data != null && data.length() > 0) {
            WritableMap params = Arguments.createMap();
            params.putString("data", data);
            sendEvent(_reactContext, "data", params);
        }
    }
}
