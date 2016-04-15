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
import android.os.Handler;
import android.os.Message;
import android.os.Looper;
import android.provider.Settings;
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

    // Debugging
    private static final String TAG = "BluetoothSerial";
    private static final boolean D = true;

    // Message types sent from the RCTBluetoothSerialService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_READ_RAW = 6;

    private Promise mEnabledPromise;
    private static Boolean SUBSCRIBED = false;
    private static Boolean SUBSCRIBED_RAW = false;
    private final ReactApplicationContext _reactContext;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    StringBuffer buffer = new StringBuffer();
    private String delimiter;
    private Handler mHandler;

    public RCTBluetoothSerialModule(ReactApplicationContext reactContext) {
        super(reactContext);
        _reactContext = reactContext;
        createHandler();
        Log.d(TAG, "Bluetooth module started");

        if (bluetoothAdapter == null) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (bluetoothSerialService == null) {
            bluetoothSerialService = new RCTBluetoothSerialService(mHandler);
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
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(id);

        if (device != null) {
            bluetoothSerialService.connect(device, secure);
            promise.resolve(true);
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
    public int available() {
        return buffer.length();
    }

    @ReactMethod
    public String read() {
        int length = buffer.length();
        String data = buffer.substring(0, length);
        buffer.delete(0, length);
        return data;
    }

    @ReactMethod
    public String readUntil(String c) {
        String data = "";
        int index = buffer.indexOf(c, 0);
        if (index > -1) {
            data = buffer.substring(0, index + c.length());
            buffer.delete(0, index + c.length());
        }
        return data;
    }

    @ReactMethod
    public Boolean isEnabled() {
        return bluetoothAdapter.isEnabled();
    }

    @ReactMethod
    public Boolean isConnected() {
        return bluetoothSerialService.getState() == RCTBluetoothSerialService.STATE_CONNECTED;
    }

    @ReactMethod
    public void clear() {
        buffer.setLength(0);
    }

    @ReactMethod
    public void subscribe(String delimiter) {
        delimiter = delimiter;
        SUBSCRIBED = true;
    }

    @ReactMethod
    public void unsubscribe() {
        delimiter = null;
        SUBSCRIBED = false;
    }

    @ReactMethod
    public void subscribeRaw() {
        SUBSCRIBED_RAW = true;
    }

    @ReactMethod
    public void unsubscribeRaw() {
        SUBSCRIBED_RAW = false;
    }

    @ReactMethod
    public void setName(String newName) {
        bluetoothAdapter.setName(newName);
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

    private void createHandler() {
        Thread thread = new Thread() {
            public void run() {
                Looper.prepare();
                final Handler mHandler = new Handler() {
                    public void handleMessage(Message msg) {
                        switch (msg.what) {
                            case MESSAGE_READ:
                                buffer.append((String)msg.obj);
                                if (SUBSCRIBED) {
                                    sendDataToSubscriber();
                                }
                                break;
                            case MESSAGE_READ_RAW:
                                if (SUBSCRIBED_RAW) {
                                    byte[] bytes = (byte[]) msg.obj;
                                    sendRawDataToSubscriber(bytes);
                                }
                                break;
                            case MESSAGE_STATE_CHANGE:
                                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                                switch (msg.arg1) {
                                    case RCTBluetoothSerialService.STATE_CONNECTED:
                                        Log.i(TAG, "RCTBluetoothSerialService.STATE_CONNECTED");
                                        notifyConnectionSuccess();
                                        break;
                                    case RCTBluetoothSerialService.STATE_CONNECTING:
                                        Log.i(TAG, "RCTBluetoothSerialService.STATE_CONNECTING");
                                        break;
                                    case RCTBluetoothSerialService.STATE_LISTEN:
                                        Log.i(TAG, "RCTBluetoothSerialService.STATE_LISTEN");
                                        break;
                                    case RCTBluetoothSerialService.STATE_NONE:
                                        Log.i(TAG, "RCTBluetoothSerialService.STATE_NONE");
                                        break;
                                }
                                break;
                            case MESSAGE_WRITE:
                                //  byte[] writeBuf = (byte[]) msg.obj;
                                //  String writeMessage = new String(writeBuf);
                                //  Log.i(TAG, "Wrote: " + writeMessage);
                                break;
                            case MESSAGE_DEVICE_NAME:
                                Log.i(TAG, msg.getData().getString(DEVICE_NAME));
                                break;
                            case MESSAGE_TOAST:
                                String message = msg.getData().getString(TOAST);
                                notifyConnectionLost(message);
                                break;
                        }
                    }
                };
                Looper.loop();
            }
        };
        thread.start();
    }

    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        Log.d(TAG, "Sending event");
        reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
    }

    private void notifyConnectionLost(String error) {
        WritableMap params = Arguments.createMap();
        params.putString("error", error);
        sendEvent(_reactContext, "connectionLost", params);
    }

    private void notifyConnectionSuccess() {
        WritableMap params = Arguments.createMap();
        params.putBoolean("connected", true);
        sendEvent(_reactContext, "connectionSuccess", params);
    }

    private void sendRawDataToSubscriber(byte[] data) {
        if (data != null && data.length > 0) {
            try {
                WritableMap params = Arguments.createMap();
                String msg = new String(data, "UTF-8");
                params.putString("data", msg);
                sendEvent(_reactContext, "rawData", params);
            } catch (UnsupportedEncodingException e) {
                Log.d(TAG, "Error converting to string");
            }

        }
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
