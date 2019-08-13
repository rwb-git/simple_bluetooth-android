/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.fork20.mine.simple_bt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import static xyz.fork20.mine.simple_bt.MainActivity.Log_e;
import static xyz.fork20.mine.simple_bt.MainActivity.Log_estr;


class BluetoothChatService {

    private static final String NAME_SECURE = "BluetoothChatSecure";
    private static final String NAME_INSECURE = "BluetoothChatInsecure";

    private static final UUID spp_UUID_SECURE =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");  // this is the UUID that applies to bluetooth devices that use SPP (serial port profile)
    private static final UUID spp_UUID_INSECURE =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final UUID MY_UUID_SECURE = spp_UUID_SECURE;

    private static final UUID MY_UUID_INSECURE = spp_UUID_INSECURE;

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private static int mState;
    private int mNewState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    private static int dump_total = 0;
    private static int dump_state = 0;

    public BluetoothChatService(Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mNewState = mState;
        mHandler = handler;
    }


    private synchronized void updateUserInterfaceTitle() {
        mState = getState();
        //Log_e("updateUserInterfaceTitle() " + mNewState + " -> " + mState);
        mNewState = mState;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, mNewState, -1).sendToTarget();
    }


    public synchronized int getState() {
        return mState;
    }


    public synchronized void start() {
        Log_e("start btcs");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        updateUserInterfaceTitle();
    }

    public synchronized void connect(BluetoothDevice device, boolean secure) {
        Log_e("connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        // Update UI title
        updateUserInterfaceTitle();
    }

    private synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        Log_e("connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        // Update UI title
        updateUserInterfaceTitle();
    }

    public synchronized void stop() {
        Log_e("stop btcs");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        mState = STATE_NONE;

        updateUserInterfaceTitle();
    }

    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);

    }

    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle();

        // Start the service over to restart listening mode
        BluetoothChatService.this.start();
    }

    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");

        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle();

        // Start the service over to restart listening mode
        BluetoothChatService.this.start();
    }

    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private final String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                            MY_UUID_SECURE);
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            NAME_INSECURE, MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log_estr("Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
            mState = STATE_LISTEN;
        }

        void cancel() {
            Log_e("Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log_estr("Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }

    @SuppressWarnings("unused")
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final String mSocketType;

        ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(
                            MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(
                            MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log_estr("Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
            mState = STATE_CONNECTING;
        }

        public void run() {
            Log_e("BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log_estr("unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothChatService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log_estr("close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        ConnectedThread(BluetoothSocket socket, String socketType) {
            Log_e("create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log_estr("temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mState = STATE_CONNECTED;
        }

        @SuppressWarnings("unused")
        // Analyze - Inspect Code says this is unused, and suggests that I delete it. but process_data() is only called from this code, and it clearly runs. Use Analyze - Inspect Code carefully
        // because it loves to break things.

        public void run() {
            Log_e("BEGIN mConnectedThread");

            setName("ConnectedThread");

            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (mState == STATE_CONNECTED) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);  // this blocks.

                    if (bytes >= 1){

                        process_data(bytes, buffer);
                    }

                } catch (IOException e) {
                    Log_estr("disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }


        @SuppressLint("DefaultLocale")
        void process_data(int bytes, byte[] buffer){

            switch (dump_state){

                case 0:		// waiting

                    // 0x57 is a random byte I picked. the nano sends this followed by 256 data bytes
                    if (buffer[0] == 0x57) {
                        dump_state = 0x57;

                        dump_total = 0;

                        handle_0x57(bytes, buffer, 1);
                    } else {
                        for (int i = 0; i < bytes; i++) {
                            Log_e(String.format("unknown byte from avr %02X #%d", buffer[i], i));
                        }
                    }

                    break;


                case 0x57: // new message for rgb2019.asm and app simple_bt

                    handle_0x57(bytes,buffer,0);

                    break;


            }

        }

        final byte[] buff57 = new byte[256];

        void handle_0x57(int bytes, byte[] buffer, int start){

//            check_watchdog();

            for (int i=start;i<bytes;i++){

                buff57[dump_total] = buffer[i];

                dump_total++;

                if (dump_total >= (256)){

                    dump_total = 0;
                    dump_state= 0;

                    int cnt = 0;

                    StringBuilder str;

                    for (int j=0;j<16;j++){

                        str = new StringBuilder();

                        for (int k=0;k<16;k++){

                            str.append(String.format("%02X ", buff57[cnt]));

                            cnt++;
                        }

                        Log_e(str.toString());
                    }
                    Log_e( "finished msg 0x57");
                }
            }
        }



        void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

            } catch (IOException e) {
                Log_estr("Exception during write", e); // this is not sent until 10-15 seconds after power is removed
            }
        }

        void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log_estr("close() of connect socket failed", e);
            }
        }
    }

    public static void clean_up_mess(){

        // if the last transaction did not send the expected number of bytes, this will discard whatever was received. all of the code in this example expects
        // an exact number of bytes from the avr; this could easily be changed to handle other cases.

        dump_total = 0;
        dump_state = 0;
    }
}
