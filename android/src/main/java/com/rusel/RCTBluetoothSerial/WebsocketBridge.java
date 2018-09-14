package com.rusel.RCTBluetoothSerial;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Base64;
import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import static android.util.Log.d;

/**
 * Proxies incoming and outgoing bluetooth data over a websocket
 */
public class WebsocketBridge extends WebSocketServer {

    private final BluetoothAdapter bluetoothAdapter;
    private final UUID serviceUUID;

    public WebsocketBridge(int listenPort, BluetoothAdapter bluetoothAdapter, UUID serviceUUID) {
        super(listenLocalhost(listenPort));
        this.bluetoothAdapter = bluetoothAdapter;
        this.serviceUUID = serviceUUID;
    }

    private static InetSocketAddress listenLocalhost(int port){

        return new InetSocketAddress("localhost", port);
    }


    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        UnderlyingConnection underlyingConnection = new UnderlyingConnection();
        conn.setAttachment(underlyingConnection);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        UnderlyingConnection underlyingConnection = conn.getAttachment();
        try {
            underlyingConnection.getBluetoothSocket().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(final WebSocket conn, String message) {
        d("wsbridge", message);

        UnderlyingConnection connectionDetails = conn.getAttachment();

        // The first message is the remote address of the bluetooth device.
        if (connectionDetails.getBluetoothSocket() != null) {

            BluetoothSocket bluetoothSocket = connectionDetails.getBluetoothSocket();

            try {

                // Send to remote bluetooth device
                bluetoothSocket.getOutputStream().write(Base64.decode(message, Base64.DEFAULT));
            } catch (IOException e) {
                conn.close(404, "Connection lost to " + bluetoothSocket.getRemoteDevice().getAddress());
            }


        } else {

            String remoteAddress = message;
            d("BluetoothSerialBridge", "Trying to connect to " + remoteAddress);

            // The first message is the actual bluetooth device's remote address. We attempt to establish
            // a connection to it.
            BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(remoteAddress);

            try {
                final BluetoothSocket socket = remoteDevice.createRfcommSocketToServiceRecord(serviceUUID);
                connectionDetails.setBluetoothSocket(socket);

                d("wsbridge", "Calling .connect");
                socket.connect();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        readFromBluetoothAndSendToSocket(conn, socket);
                    }
                }).start();

                d("BluetoothSerialBridge", "Successfully connected to " + remoteAddress);

            } catch (IOException e) {
                conn.close(404, "Could not connect to bluetooth device " + remoteAddress);

                Log.d("wsbridge", "Exception while connecting to bluetooth socket: " + e.getMessage());
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        UnderlyingConnection underlyingConnection = conn.getAttachment();

        try {
            underlyingConnection.bluetoothSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStart() {
        d("wsbridge ",  "websocket bridge started");
    }


    /**
     * We got an incoming bluetooth connection - we create a client connection across the bridge.
     *
     * @param bluetoothSocket the underlying bluetooth connection
     */
    public void createIncomingServerConnection(final BluetoothSocket bluetoothSocket) throws URISyntaxException {

        URI address = new URI("ws://127.0.0.1:5667");

        new WebSocketClient(address) {

            WebSocketClient me = this;

            @Override
            public void onOpen(ServerHandshake handshakedata) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        readFromBluetoothAndSendToSocket(me, bluetoothSocket);
                    }
                }).start();
            }

            @Override
            public void onMessage(String message) {
                try {
                    bluetoothSocket.getOutputStream().write(Base64.decode(message, Base64.DEFAULT));

                } catch (IOException e) {
                    this.close(400, "Connection closed to " + bluetoothSocket.getRemoteDevice().getAddress());
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                try {
                    bluetoothSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(Exception ex) {
                try {
                    bluetoothSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

    }

    private class UnderlyingConnection {

        private BluetoothSocket bluetoothSocket = null;

        public UnderlyingConnection() {

        }

        public BluetoothSocket getBluetoothSocket() {
            return bluetoothSocket;
        }

        public void setBluetoothSocket(BluetoothSocket bluetoothSocket) {
            this.bluetoothSocket = bluetoothSocket;
        }
    }

    private void readFromBluetoothAndSendToSocket(WebSocket webSocket, BluetoothSocket bluetoothSocket) {
        byte[] buffer = new byte[1024];
        int numberOfBytesRead;

        InputStream inputStream = null;
        try {
            inputStream = bluetoothSocket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
            webSocket.close();

            d("wsbridge", "Error while preparing to read from bluetooth socket: " + e.getMessage());

            return;
        }

        // Keep listening to the InputStream while connected
        while (true) {
            try {
                // Send the incoming data across the bridge
                
                numberOfBytesRead = inputStream.read(buffer);
                String base64Data = Base64.encodeToString(buffer, 0, numberOfBytesRead, Base64.DEFAULT);
                webSocket.send(base64Data);
            } catch (IOException e) {
                d("wsbridge", "Error while reading from bluetooth socket: " + e.getMessage());

                webSocket.close();
                break;
            } catch (WebsocketNotConnectedException ex) {
                try {
                    bluetoothSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
    }

}