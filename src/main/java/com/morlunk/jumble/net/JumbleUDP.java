/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.morlunk.jumble.net;

import android.util.Log;

import com.morlunk.jumble.Constants;

import java.io.IOException;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Class to maintain and receive packets from the UDP connection to a Mumble server.
 */
public class JumbleUDP extends JumbleNetworkThread {
    private static final int BUFFER_SIZE = 2048;
    private final CryptState mCryptState;

    private DatagramSocket mUDPSocket;
    private UDPConnectionListener mListener;
    private String mHost;
    private int mPort;
    private InetAddress mResolvedHost;
    private boolean mConnected;

    /**
     * Sets up a new UDP connection context.
     * @param cryptState Cryptographic state provider.
     */
    public JumbleUDP(CryptState cryptState) {
        mCryptState = cryptState;
    }

    public void setUDPConnectionListener(UDPConnectionListener listener) {
        mListener = listener;
    }

    public void connect(String host, int port) throws ConnectException {
        if(mConnected) throw new ConnectException("UDP connection already established!");
        mHost = host;
        mPort = port;
        startThread();
    }

    public boolean isRunning() {
        return mConnected;
    }

    @Override
    public void run() {
        try {
            mResolvedHost = InetAddress.getByName(mHost);
            mUDPSocket = new DatagramSocket();
        } catch (SocketException e) {
            if(mListener != null) mListener.onUDPConnectionError(e);
            return;
        } catch (UnknownHostException e) {
            if(mListener != null) mListener.onUDPConnectionError(e);
            return;
        }

        mUDPSocket.connect(mResolvedHost, mPort);
        final DatagramPacket packet = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);
        final byte[] buffer = new byte[BUFFER_SIZE];

        Log.v(Constants.TAG, "Created UDP socket");
        mConnected = true;

        while(mConnected) {
            try {
                mUDPSocket.receive(packet);

                // Decrypt UDP packet using OCB-AES128
                final byte[] data = packet.getData();
                final int length = packet.getLength();
                mCryptState.decrypt(data, buffer, length);
                if(mListener != null) mListener.onUDPDataReceived(buffer);
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
        disconnect(); // Make sure we close the socket if disconnect wasn't controlled
    }

    public void sendMessage(byte[] data, int length) throws IOException {
        if(!mCryptState.isValid() || !mConnected)
            return;
        byte[] encryptedData = mCryptState.encrypt(data, length);
        DatagramPacket packet = new DatagramPacket(encryptedData, encryptedData.length);
        packet.setAddress(mResolvedHost);
        packet.setPort(mPort);
        mUDPSocket.send(packet);
    }

    public void disconnect() {
        if(!mConnected) return;
        mConnected = false;
        executeOnSendThread(new Runnable() {
            @Override
            public void run() {
                mUDPSocket.disconnect();
                mUDPSocket.close();
            }
        });
    }

    public interface UDPConnectionListener {
        public void onUDPDataReceived(byte[] data);
        public void onUDPConnectionError(Exception e);
    }
}
