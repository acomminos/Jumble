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

import com.google.protobuf.Message;
import com.morlunk.jumble.Constants;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;

/**
 * Class to maintain and interface with the TCP connection to a Mumble server.
 * Parses Mumble protobuf packets according to the Mumble protocol specification.
 */
public class JumbleTCP extends JumbleNetworkThread {
    private final JumbleSSLSocketFactory mSocketFactory;
    private String mHost;
    private int mPort;
    private boolean mUseTor;
    private SSLSocket mTCPSocket;
    private DataInputStream mDataInput;
    private DataOutputStream mDataOutput;
    private boolean mRunning;
    private boolean mConnected;
    private TCPConnectionListener mListener;

    public JumbleTCP(JumbleSSLSocketFactory socketFactory) {
        mSocketFactory = socketFactory;
    }

    public void setTCPConnectionListener(TCPConnectionListener listener) {
        mListener = listener;
    }

    public void connect(String host, int port, boolean useTor) throws ConnectException {
        if(mRunning) throw new ConnectException("TCP connection already established!");
        mHost = host;
        mPort = port;
        mUseTor = useTor;
        startThreads();
    }

    public boolean isRunning() {
        return mRunning;
    }

    public void run() {
        mRunning = true;
        try {
            InetAddress address = InetAddress.getByName(mHost);

            Log.i(Constants.TAG, "JumbleTCP: Connecting");

            if(mUseTor)
                mTCPSocket = mSocketFactory.createTorSocket(address, mPort, JumbleConnection.TOR_HOST, JumbleConnection.TOR_PORT);
            else
                mTCPSocket = mSocketFactory.createSocket(address, mPort);

            mTCPSocket.setKeepAlive(true);
            mTCPSocket.startHandshake();

            Log.v(Constants.TAG, "JumbleTCP: Started handshake");

            mDataInput = new DataInputStream(mTCPSocket.getInputStream());
            mDataOutput = new DataOutputStream(mTCPSocket.getOutputStream());
        } catch (SocketException e) {
            error("Could not open a connection to the host", e, false);
            return;
        } catch (SSLHandshakeException e) {
            // Try and verify certificate manually.
            if(mSocketFactory.getServerChain() != null && mListener != null) {
                if(!mRunning) return;
                executeOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onTLSHandshakeFailed(mSocketFactory.getServerChain());
                    }
                });
                mRunning = false;
            } else {
                error("Could not verify host certificate", e, false);
            }
            return;
        } catch (IOException e) {
            error("An error occurred when communicating with the host", e, false);
            return;
        }

        mConnected = true;
        if(mListener != null) {
            executeOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mListener.onTCPConnectionEstablished();
                }
            });
        }

        Log.v(Constants.TAG, "JumbleTCP: Now listening");

        while(mConnected) {
            try {
                final short messageType = mDataInput.readShort();
                final int messageLength = mDataInput.readInt();
                final byte[] data = new byte[messageLength];
                mDataInput.readFully(data);

                final JumbleTCPMessageType tcpMessageType = JumbleTCPMessageType.values()[messageType];
                if (mListener != null) {
                    executeOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            mListener.onTCPMessageReceived(tcpMessageType, messageLength, data);
                        }
                    });
                }
            } catch (final IOException e) {
                if(mConnected) {
                    mConnected = false;
                    error("Lost connection to server", e, true);
                }
            }
        }

        if(mListener != null) {
            executeOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mListener.onTCPConnectionDisconnect();
                }
            });
        }

        mRunning = false;
    }

    private void error(String desc, Exception e, boolean autoReconnect) {
        if(!mRunning) return;
        final JumbleException ce = new JumbleException(desc, e, autoReconnect);
        if(mListener != null) executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                mListener.onTCPConnectionFailed(ce);
            }
        });
        mRunning = false;
    }

    /**
     * Attempts to send a protobuf message over TCP. Thread-safe, executes on a single threaded executor.
     * @param message The message to send.
     * @param messageType The type of the message to send.
     */
    public void sendMessage(final Message message, final JumbleTCPMessageType messageType) {
        executeOnSendThread(new Runnable() {
            @Override
            public void run() {
                if (!JumbleConnection.UNLOGGED_MESSAGES.contains(messageType))
                    Log.v(Constants.TAG, "OUT: " + messageType);
                try {
                    mDataOutput.writeShort(messageType.ordinal());
                    mDataOutput.writeInt(message.getSerializedSize());
                    message.writeTo(mDataOutput);
                } catch (IOException e) {
                    e.printStackTrace();
                    // TODO handle
                }
            }
        });
    }
    /**
     * Attempts to send a protobuf message over TCP. Thread-safe, executes on a single threaded executor.
     * @param message The data to send.
     * @param length The length of the byte array.
     * @param messageType The type of the message to send.
     */
    public void sendMessage(final byte[] message, final int length, final JumbleTCPMessageType messageType) {
        executeOnSendThread(new Runnable() {
            @Override
            public void run() {
                if (!JumbleConnection.UNLOGGED_MESSAGES.contains(messageType))
                    Log.v(Constants.TAG, "OUT: " + messageType);
                try {
                    mDataOutput.writeShort(messageType.ordinal());
                    mDataOutput.writeInt(length);
                    mDataOutput.write(message, 0, length);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Attempts to disconnect gracefully on the Tx thread.
     */
    public void disconnect() {
        if(!mRunning) return;
        mConnected = false;
        mRunning = false;
        executeOnSendThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(mDataOutput != null) mDataOutput.close();
                    if(mDataInput != null) mDataInput.close();
                    if(mTCPSocket != null) mTCPSocket.close();
                    Log.i(Constants.TAG, "JumbleTCP: Disconnected");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        stopThreads();
    }

    public interface TCPConnectionListener {
        public void onTCPConnectionEstablished();
        public void onTLSHandshakeFailed(X509Certificate[] chain);
        public void onTCPConnectionFailed(JumbleException e);
        public void onTCPConnectionDisconnect();
        public void onTCPMessageReceived(JumbleTCPMessageType type, int length, byte[] data);
    }
}
