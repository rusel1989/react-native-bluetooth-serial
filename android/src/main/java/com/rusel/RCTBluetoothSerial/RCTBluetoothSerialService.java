package com.rusel.RCTBluetoothSerial;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import static com.rusel.RCTBluetoothSerial.RCTBluetoothSerialPackage.TAG;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 *
 * This code was based on the Android SDK BluetoothChat Sample
 * $ANDROID_SDK/samples/android-17/BluetoothChat
 */
class RCTBluetoothSerialService {
    // Debugging
    private static final boolean D = true;

    // UUIDs
    private static final UUID UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private BluetoothAdapter mAdapter;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private RCTBluetoothSerialModule mModule;
    private String mState;

    // Constants that indicate the current connection state
    private static final String STATE_NONE = "none";       // we're doing nothing
    private static final String STATE_CONNECTING = "connecting"; // now initiating an outgoing connection
    private static final String STATE_CONNECTED = "connected";  // now connected to a remote device

    /**
     * Constructor. Prepares a new RCTBluetoothSerialModule session.
     * @param module Module which handles service events
     */
    RCTBluetoothSerialService(RCTBluetoothSerialModule module) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mModule = module;
    }

    /********************************************/
    /** Methods available within whole package **/
    /********************************************/

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        if (mState.equals(STATE_CONNECTING)) {
            cancelConnectThread(); // Cancel any thread attempting to make a connection
        }

        cancelConnectedThread(); // Cancel any thread currently running a connection

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Check whether service is connected to device
     * @return Is connected to device
     */
    boolean isConnected () {
        return getState().equals(STATE_CONNECTED);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    void write(byte[] out) {
        if (D) Log.d(TAG, "Write in service, state is " + STATE_CONNECTED);
        ConnectedThread r; // Create temporary object

        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (!isConnected()) return;
            r = mConnectedThread;
        }

        r.write(out); // Perform the write unsynchronized
    }

    /**
     * Stop all threads
     */
    synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        cancelConnectThread();
        cancelConnectedThread();

        setState(STATE_NONE);
    }

    /*********************/
    /** Private methods **/
    /*********************/

    /**
     * Return the current connection state.
     */
    private synchronized String getState() {
        return mState;
    }

    /**
     * Set the current state of connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(String state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    private synchronized void connectionSuccess(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected");

        cancelConnectThread(); // Cancel any thread attempting to make a connection
        cancelConnectedThread(); // Cancel any thread currently running a connection

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        mModule.onConnectionSuccess("Connected to " + device.getName());
        setState(STATE_CONNECTED);
    }


    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        mModule.onConnectionFailed("Unable to connect to device"); // Send a failure message
        RCTBluetoothSerialService.this.stop(); // Start the service over to restart listening mode
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        mModule.onConnectionLost("Device connection was lost");  // Send a failure message
        RCTBluetoothSerialService.this.stop(); // Start the service over to restart listening mode
    }

    /**
     * Cancel connect thread
     */
    private void cancelConnectThread () {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
    }

    /**
     * Cancel connected thread
     */
    private void cancelConnectedThread () {
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(UUID_SPP);
            } catch (Exception e) {
                mModule.onError(e);
                Log.e(TAG, "Socket create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            if (D) Log.d(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a successful connection or an exception
                if (D) Log.d(TAG,"Connecting to socket...");
                mmSocket.connect();
                if (D) Log.d(TAG,"Connected");
            } catch (Exception e) {
                Log.e(TAG, e.toString());
                mModule.onError(e);

                // Some 4.1 devices have problems, try an alternative way to connect
                // See https://github.com/don/RCTBluetoothSerialModule/issues/89
                try {
                    Log.i(TAG,"Trying fallback...");
                    mmSocket = (BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(mmDevice,1);
                    mmSocket.connect();
                    Log.i(TAG,"Connected");
                } catch (Exception e2) {
                    Log.e(TAG, "Couldn't establish a Bluetooth connection.");
                    mModule.onError(e2);
                    try {
                        mmSocket.close();
                    } catch (Exception e3) {
                        Log.e(TAG, "unable to close() socket during connection failure", e3);
                        mModule.onError(e3);
                    }
                    connectionFailed();
                    return;
                }
            }

            // Reset the ConnectThread because we're done
            synchronized (RCTBluetoothSerialService.this) {
                mConnectThread = null;
            }

            connectionSuccess(mmSocket, mmDevice);  // Start the connected thread

        }

        void cancel() {
            try {
                mmSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "close() of connect socket failed", e);
                mModule.onError(e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        ConnectedThread(BluetoothSocket socket) {
            if (D) Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (Exception e) {
                Log.e(TAG, "temp sockets not created", e);
                mModule.onError(e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    bytes = mmInStream.read(buffer); // Read from the InputStream
                    String data = new String(buffer, 0, bytes, "ISO-8859-1");

                    mModule.onData(data); // Send the new data String to the UI Activity
                } catch (Exception e) {
                    Log.e(TAG, "disconnected", e);
                    mModule.onError(e);
                    connectionLost();
                    RCTBluetoothSerialService.this.stop(); // Start the service over to restart listening mode
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        void write(byte[] buffer) {
            try {
                String str = new String(buffer, "UTF-8");
                if (D) Log.d(TAG, "Write in thread " + str);
                mmOutStream.write(buffer);
            } catch (Exception e) {
                Log.e(TAG, "Exception during write", e);
                mModule.onError(e);
            }
        }

        void cancel() {
            try {
                mmSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
