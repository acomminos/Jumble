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

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

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
        startThreads();
    }

    public boolean isRunning() {
        return mConnected;
    }

    @Override
    public void run() {
        try {
            mResolvedHost = InetAddress.getByName(mHost);
            mUDPSocket = new DatagramSocket();
        } catch (final IOException e) {
            if(mListener != null) {
                executeOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onUDPConnectionError(e);
                    }
                });
            }
            return;
        }

        mUDPSocket.connect(mResolvedHost, mPort);
        final DatagramPacket packet = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);

        Log.v(Constants.TAG, "Created UDP socket");
        mConnected = true;

        while(mConnected) {
            try {
                mUDPSocket.receive(packet);
                final byte[] data = packet.getData();
                final int length = packet.getLength();

                if (!mCryptState.isValid()) continue;
                if (length < 5) continue;

                try {
                    final byte[] buffer = mCryptState.decrypt(data, length);

                    if (mListener != null) {
                        if (buffer != null) {
                            executeOnReceiveThread(new Runnable() {
                                @Override
                                public void run() {
                                    mListener.onUDPDataReceived(buffer);
                                }
                            });
                        } else if(mCryptState.getLastGoodElapsed() > 5000000 &&
                                mCryptState.getLastRequestElapsed() > 5000000) {
                            mCryptState.resetLastRequestTime();
                            executeOnMainThread(new Runnable() {
                                @Override
                                public void run() {
                                    mListener.resyncCryptState();
                                }
                            });
                        }
                    }
                } catch (BadPaddingException e) {
                    e.printStackTrace();
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                } catch (ShortBufferException e) {
                    e.printStackTrace();
                }
            } catch (final IOException e) {
                // If a UDP exception is thrown while connected, notify the listener to fall back to TCP.
                if(mConnected && mListener != null) {
                    executeOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            mListener.onUDPConnectionError(e);
                        }
                    });
                }
                break;
            }
        }
        disconnect(); // Make sure we close the socket if disconnect wasn't controlled
    }

    public void sendMessage(final byte[] data, final int length) {
        if(!mCryptState.isValid() || !mConnected) return;
        executeOnSendThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(!mCryptState.isValid() || !mConnected) return;
                    byte[] encryptedData = mCryptState.encrypt(data, length);
                    final DatagramPacket packet = new DatagramPacket(encryptedData, encryptedData.length);
                    packet.setAddress(mResolvedHost);
                    packet.setPort(mPort);
                    mUDPSocket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                } catch (ShortBufferException e) {
                    e.printStackTrace();
                } catch (BadPaddingException e) {
                    e.printStackTrace();
                }
            }
        });
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
        stopThreads();
    }

    /**
     * Note that all connection state related calls are made on the main thread.
     * onUDPDataReceived is always called on the UDP receive thread.
     */
    public interface UDPConnectionListener {
        public void onUDPDataReceived(byte[] data);
        public void onUDPConnectionError(Exception e);
        public void resyncCryptState();
    }
}
