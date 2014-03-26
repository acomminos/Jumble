/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

        Log.v(Constants.TAG, "Created UDP socket");
        mConnected = true;

        while(mConnected) {
            try {
                mUDPSocket.receive(packet);

                // Decrypt UDP packet using OCB-AES128
                final byte[] data = packet.getData();
                final int length = packet.getLength();
                final byte[] decryptedData = new byte[length - 4]; // Tag occupies 4 bytes in encrypted data
                mCryptState.decrypt(data, decryptedData, length);
                if(mListener != null) mListener.onUDPDataReceived(decryptedData);
            } catch (IOException e) {
                e.printStackTrace();
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
