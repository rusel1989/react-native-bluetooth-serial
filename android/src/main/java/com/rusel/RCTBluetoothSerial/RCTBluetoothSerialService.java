package com.rusel.RCTBluetoothSerial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.util.Base64;
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

    // Trace: for verbose output (raw messages being sent and received, etc.)
    private static final boolean T = false;
    private final WebsocketBridge websocketBridge;

    // UUIDs

    // Member fields
    private BluetoothAdapter mAdapter;

    private RCTBluetoothSerialModule mModule;

    // Constants that indicate the current connection state
    private static final String STATE_CONNECTING = "connecting"; // now initiating an outgoing connection
    private static final String STATE_CONNECTED = "connected";  // now connected to a remote device

    // A map of the bluetooth devices that we have connections open for
    private final Map<String, ConnectedThread> connectedDevices = new HashMap<>();

    private ServerListenThread mServerListenThread = null;

    /**
     * Constructor. Prepares a new RCTBluetoothSerialModule session.
     * @param module Module which handles service events
     */
    RCTBluetoothSerialService(RCTBluetoothSerialModule module) throws UnknownHostException {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mModule = module;

        // Hardcode for now
        UUID uuid = UUID.fromString("b0b2e90d-0cda-4bb0-8e4b-fb165cd17d48");

        this.websocketBridge = new WebsocketBridge(5666, mAdapter, uuid);
        this.websocketBridge.start();
    }

    /********************************************/
    /** Methods available within whole package **/
    /********************************************/

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    synchronized void connect(BluetoothDevice device, String serviceUUID) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Start the thread to connect with the given device
        ConnectThread connectThread = new ConnectThread(device, serviceUUID);
        connectThread.start();
    }

    /**
     * Creates a server connection to listen for incoming connections.
     * return true if a server was not running and a new server was started, false is a server was already running.
     */
    public synchronized boolean startServerSocket(String serviceName, UUID serviceUUID) throws IOException {

        if (mServerListenThread == null) {
    
            BluetoothServerSocket bluetoothServerSocket = BluetoothAdapter
                    .getDefaultAdapter()
                    .listenUsingRfcommWithServiceRecord(serviceName, serviceUUID);
    
            // Listen for incoming connections on a new thread and put new entries into the
            // connected devices map
            mServerListenThread = new ServerListenThread(bluetoothServerSocket);
            mServerListenThread.start();
            return true;

        } else {
           if (D) Log.d(TAG, "Already listening for incoming connections");
           return false;
        }
        
    }

    /**
     * Stop accepting connections on the server socket.
     *
     * Synchronized for exclusive access to the mServerListenThread object
     *
     * @throws IOException
     */
    public synchronized void stopServerSocket() throws IOException {

        // Close the listen socket;
        mServerListenThread.closeListenSocket();

        // Stop the thread
        mServerListenThread.interrupt();

        mServerListenThread = null;
    }

    /**
     * End all ongoing bluetooth connections.
     *
     * Synchronized for exclusive access to the connected devices map.
     * 
     * @throws IOException
     */
    public synchronized void endAllConnections() {

        Set<String> devices = connectedDevices.keySet();

        for (String device: devices) {
            connectedDevices.get(device).interrupt();
        }

        connectedDevices.clear();
    }



    /**
     * Write to the ConnectedThread
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    void write(String deviceAddress, byte[] out) {

        ConnectedThread connectedThread = getConnectedDevice(deviceAddress);

        if (connectedThread != null) {
            connectedThread.write(out);
        } else {
            if (D) Log.d(TAG, "Tried to write to " + deviceAddress + " but device not in the connected devices map.");
        }

    }

    /**
     * Returns the device connection thread if we are connected to the device, otherwise returns null.
     * @param deviceAddress
     * @return the connected device thread if it exists in the map, otherwise null
     */
    private ConnectedThread getConnectedDevice(String deviceAddress) {
        return connectedDevices.get(deviceAddress);
    }

    /*********************/
    /** Private methods **/
    /*********************/

    private synchronized void connectionSuccess(BluetoothSocket socket, boolean isIncoming) {
        if (D) Log.d(TAG, "connected");

        mModule.onConnectionSuccess(socket.getRemoteDevice().getAddress(),"Connected to " + socket.getRemoteDevice().getName(), isIncoming);
    }


    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed(String deviceAddress) {
        mModule.onConnectionFailed(deviceAddress, "Unable to connect to device"); // Send a failure message
    }

    /**
     * Notify the UI thread that the connection to a remote device was lost.
     *
     * Synchronized to prevent races if connecting to a remote address multiple times as multiple
     * socket threads negotiating connections at once are possible.
     *
     * @param connectedThread the connection thread.
     */
    private synchronized void connectionLost(ConnectedThread connectedThread) {
        String address = connectedThread.getRemoteAddress();
        removeConnectedDevice(address);
        mModule.onConnectionLost(address,"Device connection to " + address +  " was lost");  // Send a failure message
    }

    private void removeConnectedDevice(String address) {
        if (connectedDevices.containsKey(address)) {
            connectedDevices.remove(address);
        } else if (connectedDevices.containsKey(address)) {
            connectedDevices.remove(address);
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

        ConnectThread(BluetoothDevice device, String serviceUUID) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(serviceUUID));

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
                    connectionFailed(mmDevice.getAddress());
                    return;
                }
            }

            connectionSuccess(mmSocket, false);  // Start the connected thread

            // We no longer need this thread
            this.interrupt();
        }
    }

    /**
     * This thread listens for new incoming
     */
    private class ServerListenThread extends Thread {

        private final BluetoothServerSocket serverSocket;
        private boolean stopped = false;

        ServerListenThread(BluetoothServerSocket serverSocket) {
            if (D) Log.d(TAG, "Created server listen thread");

            this.serverSocket = serverSocket;
        }

        @Override
        public void run() {
            while (true) {
                // Block until there is a new incoming connection, then add it to the connected devices
                // then block again until there is a new connection. This loop exits when the thread is
                // stopped and an interrupted exception is thrown
                try {

                    if (D) Log.d(TAG, "Awaiting a new incoming connection");

                    final BluetoothSocket newConnection = this.serverSocket.accept();

                    if (D) Log.d(TAG, "New connection from: " + newConnection.getRemoteDevice().getAddress());

                    if (newConnection.getRemoteDevice().getBondState() != BluetoothDevice.BOND_BONDED)
                    {
                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which){
                                    case DialogInterface.BUTTON_POSITIVE:
                                        if (D) Log.d(TAG, "Accepted incoming connection from: " + newConnection.getRemoteDevice().getAddress() + " bond state " + newConnection.getRemoteDevice().getBondState() );

                                        websocketBridge.createIncomingServerConnection(newConnection);
                                        connectionSuccess(newConnection, true);
                                        break;

                                    case DialogInterface.BUTTON_NEGATIVE:
                                        //No button clicked
                                        if (D) Log.d(TAG, "User did not accept the incoming connection. Closing socket.");
                                        try {
                                            newConnection.close();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        break;
                                }
                            }
                        };

                        String message = "Accept incoming connection from: " + newConnection.getRemoteDevice().getName() + "(" + newConnection.getRemoteDevice().getAddress() + ")";
                        mModule.showYesNoDialog(message, dialogClickListener);
                    } else {
                        String address = newConnection.getRemoteDevice().getAddress();
                        if (D) Log.d( TAG, "Accepted incoming connection from " + address + " which has pre-existing bond." );

                        websocketBridge.createIncomingServerConnection(newConnection);
                    }


                } catch (IOException e) {

                    if (D) Log.d(TAG, "Error while accepting incoming connection: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        public void closeListenSocket() throws IOException {
            this.serverSocket.close();
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
        private boolean isIncoming = false;

        ConnectedThread(BluetoothSocket socket, boolean isIncoming) {
            if (D) Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            this.isIncoming = isIncoming;

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

        /**
         * @return True if this is an incoming connection, false if it is outgoing
         */
        public boolean isIncoming() {
            return isIncoming;
        }

        public String getRemoteAddress() {
            return mmSocket.getRemoteDevice().getAddress();
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int numberOfBytesRead;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    numberOfBytesRead = mmInStream.read(buffer);
                    String base64Data = Base64.encodeToString(buffer, 0, numberOfBytesRead, Base64.DEFAULT);
                    String address = mmSocket.getRemoteDevice().getAddress();

                    // Send the data to the UI activity to send over the react bridge as an event
                    mModule.onData(address, base64Data);
                } catch (Exception e) {
                    Log.e(TAG, "disconnected", e);
                    mModule.onError(e);
                    connectionLost(this);

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
                if (T) {
                    String str = new String(buffer, "UTF-8");
                    Log.v(TAG, "Write in thread " + str);
                }

                mmOutStream.write(buffer);
            } catch (Exception e) {
                Log.e(TAG, "Exception during write", e);
                mModule.onError(e);
            }
        }

    }
}
