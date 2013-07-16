/*
 * Copyright (C) 2013 Andrew Comminos
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

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.JumbleParams;
import com.morlunk.jumble.protobuf.Mumble;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JumbleConnection {
    public interface JumbleConnectionListener {
        public void onConnectionEstablished();
        public void onConnectionDisconnected();
        public void onConnectionError(JumbleConnectionException e);
    }

    private Context mContext;
    private JumbleConnectionListener mListener;
    private JumbleParams mParams;

    // Authentication
    private JumbleSSLSocketFactory mSocketFactory;

    // Threading
    private ExecutorService mExecutorService;
    private ScheduledExecutorService mPingExecutorService;
    private Handler mMainHandler;

    // Networking
    private InetAddress mHost;
    private JumbleTCP mTCP;
    private JumbleUDP mUDP;
    private boolean mConnected;
    private CryptState mCryptState = new CryptState();
    private long mStartTimestamp; // Time that the connection was initiated in nanoseconds

    // Server
    private int mServerVersion;
    private String mServerRelease;
    private String mServerOSName;
    private String mServerOSVersion;

    // Session
    private int mSession;

    // Message handlers
    private ConcurrentLinkedQueue<JumbleMessageHandler> mHandlers = new ConcurrentLinkedQueue<JumbleMessageHandler>();

    /**
     * Handles packets received that are critical to the connection state.
     */
    private JumbleMessageHandler mConnectionMessageHandler = new JumbleMessageHandler() {

        @Override
        public void messageServerSync(Mumble.ServerSync msg) {
            /* TODO list:
             * - set user session
             * - other great things
             */

            // Start TCP/UDP ping thread. FIXME is this the right place?
            mPingExecutorService.scheduleAtFixedRate(mPingRunnable, 0, 5, TimeUnit.SECONDS);

            mSession = msg.getSession();

            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnectionEstablished();
                }
            });

        }

        @Override
        public void messageReject(final Mumble.Reject msg) {
            if(mListener != null) {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onConnectionError(new JumbleConnectionException(msg));
                    }
                });
            }
            disconnect();
        }

        @Override
        public void messageUserRemove(final Mumble.UserRemove msg) {
            if(msg.getActor() == mSession) {
                if(mListener != null) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mListener.onConnectionError(new JumbleConnectionException(msg));
                        }
                    });
                }
                disconnect();
            }
        }

        @Override
        public void messageCryptSetup(Mumble.CryptSetup msg) {
            try {
                if(msg.hasKey() && msg.hasClientNonce() && msg.hasServerNonce()) {
                    ByteString key = msg.getKey();
                    ByteString clientNonce = msg.getClientNonce();
                    ByteString serverNonce = msg.getServerNonce();
                    if(key.size() == CryptState.AES_BLOCK_SIZE &&
                            clientNonce.size() == CryptState.AES_BLOCK_SIZE &&
                            serverNonce.size() == CryptState.AES_BLOCK_SIZE)
                        mCryptState.setKey(key.toByteArray(), clientNonce.toByteArray(), serverNonce.toByteArray());
                } else if(msg.hasServerNonce()) {
                    ByteString serverNonce = msg.getServerNonce();
                    if(serverNonce.size() == CryptState.AES_BLOCK_SIZE) {
                        mCryptState.mUiResync++;
                        mCryptState.setDecryptIV(serverNonce.toByteArray());
                    }
                } else {
                    Mumble.CryptSetup.Builder csb = Mumble.CryptSetup.newBuilder();
                    csb.setClientNonce(ByteString.copyFrom(mCryptState.getEncryptIV()));
                    sendTCPMessage(csb.build(), JumbleTCPMessageType.CryptSetup);
                }
            } catch (InvalidKeyException e) {
                handleFatalException(new JumbleConnectionException("Received invalid cryptographic nonce from server", e, true));
            } catch (IOException e) {
                handleFatalException(new JumbleConnectionException("Failed to send client nonce to server", e, true));
            }
        }

        @Override
        public void messageVersion(Mumble.Version msg) {
            mServerVersion = msg.getVersion();
            mServerRelease = msg.getRelease();
            mServerOSName = msg.getOs();
            mServerOSVersion = msg.getOsVersion();
        }
    };

    private Runnable mPingRunnable = new Runnable() {
        @Override
        public void run() {

            if(mUDP != null) {
                byte[] buffer = new byte[256];

            }

            Mumble.Ping.Builder pb = Mumble.Ping.newBuilder();
            pb.setTimestamp((System.nanoTime()-mStartTimestamp)/1000000);
            pb.setGood(mCryptState.mUiGood);
            pb.setLate(mCryptState.mUiLate);
            pb.setLost(mCryptState.mUiLost);
            pb.setResync(mCryptState.mUiResync);

            try {
                sendTCPMessage(pb.build(), JumbleTCPMessageType.Ping);
            } catch (IOException e) {
                handleFatalException(new JumbleConnectionException("Failed to ping remote server.", e, true));
            }
        }
    };

    /**
     * Creates a new JumbleConnection object to facilitate server connections.
     * @param context An Android context.
     * @param params Jumble parameters for connection.
     * @throws CertificateException if an exception occurred parsing the p12 file.
     * @throws IOException if there was an error reading the p12 file.
     */
    public JumbleConnection(Context context,
                            JumbleConnectionListener listener,
                            JumbleParams params) throws JumbleConnectionException {
        mContext = context;
        mListener = listener;
        mParams = params;
        mMainHandler = new Handler(context.getMainLooper());
        mHandlers.add(mConnectionMessageHandler);
        setupSocketFactory(mParams.certificatePath, mParams.certificatePassword);
    }

    public void connect() {
        mConnected = false;
        mStartTimestamp = System.nanoTime();

        mExecutorService = Executors.newFixedThreadPool(2); // One TCP thread, one UDP thread.
        mPingExecutorService = Executors.newSingleThreadScheduledExecutor();
        mTCP = new JumbleTCP();
        mUDP = new JumbleUDP();
        mExecutorService.submit(mTCP);
        mExecutorService.submit(mUDP);
    }

    public boolean isConnected() {
        return mConnected;
    }

    /**
     * Gracefully shuts down all networking.
     */
    public void disconnect() {
        mConnected = false;
        try {
            mTCP.disconnect();
            mUDP.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mTCP = null;
        mUDP = null;
        mExecutorService.shutdown();
        mPingExecutorService.shutdown();
    }

    /**
     * Immediately shuts down all network threads.
     */
    public void forceDisconnect() {
        mConnected = false;
        mExecutorService.shutdownNow();
        mPingExecutorService.shutdownNow();
        mTCP = null;
        mUDP = null;
    }

    /**
     * Handles an exception that would cause termination of the connection.
     * @param e The exception that caused termination.
     */
    private void handleFatalException(final JumbleConnectionException e) {
        forceDisconnect();
        if(mListener != null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnectionError(e);
                }
            });
        }
    }

    /**
     * Attempts to load the PKCS12 certificate from the specified stream, and sets up an SSL socket factory.
     * You must call this method before establishing a TCP connection.
     * @param certificateStream The input stream of a PKCS12 (.p12) certificate. May be null.
     * @param certificatePassword The password to decrypt the key store. May be null.
     */
    protected void setupSocketFactory(InputStream certificateStream, String certificatePassword) throws JumbleConnectionException {
        try {
            KeyStore keyStore = null;
            if(certificateStream != null) {
                keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(certificateStream, certificatePassword != null ? certificatePassword.toCharArray() : new char[0]);
            }
            mSocketFactory = new JumbleSSLSocketFactory(keyStore, certificatePassword);
        } catch (KeyManagementException e) {
            throw new JumbleConnectionException("Could not recover keys from certificate", e, false);
        } catch (KeyStoreException e) {
            throw new JumbleConnectionException("Could not recover keys from certificate", e, false);
        } catch (UnrecoverableKeyException e) {
            throw new JumbleConnectionException("Could not recover keys from certificate", e, false);
        } catch (IOException e) {
            throw new JumbleConnectionException("Could not read certificate file", e, false);
        } catch (CertificateException e) {
            e.printStackTrace();
        }catch (NoSuchProviderException e) {
                /*
                 * This will actually NEVER occur.
                 * We use Spongy Castle to provide the algorithm and provider implementations.
                 * There's no platform dependency.
                 */
            throw new RuntimeException("We use Spongy Castle- what? ", e);
        } catch (NoSuchAlgorithmException e) {
                /*
                 * This will actually NEVER occur.
                 * We use Spongy Castle to provide the algorithm and provider implementations.
                 * There's no platform dependency.
                 */
            throw new RuntimeException("We use Spongy Castle- what? ", e);
        }
    }

    /**
     * Wrapper to load a P12 into a socket factory via a path..
     * @param certificateFile
     * @param certificatePassword
     * @throws JumbleConnectionException
     */
    protected void setupSocketFactory(String certificateFile, String certificatePassword) throws JumbleConnectionException {
        try {
            setupSocketFactory(certificateFile != null ? new FileInputStream(certificateFile) : null, certificatePassword);
        } catch (FileNotFoundException e) {
            throw new JumbleConnectionException("Could not find certificate file", e, false);
        }
    }

    public void sendTCPMessage(Message message, JumbleTCPMessageType messageType) throws IOException {
        Log.v(Constants.TAG, "OUT: "+messageType);
        mTCP.sendMessage(message, messageType);
    }

    /**
     * Class to maintain and interface with the TCP connection to a Mumble server.
     */
    private class JumbleTCP implements Runnable {
        private SSLSocket mTCPSocket;
        private DataInputStream mDataInput;
        private DataOutputStream mDataOutput;

        /**
         * Attempts to disconnect gracefully.
         * @throws IOException if the socket couldn't close as expected.
         */
        public void disconnect() throws IOException {
            mDataOutput.close();
            mDataInput.close();
            mTCPSocket.close();
        }

        public void run() {
            try {
                mTCPSocket = (SSLSocket)mSocketFactory.createSocket();
                mTCPSocket.setKeepAlive(true);
                mTCPSocket.setEnabledProtocols(new String[] {"TLSv1"});
                mTCPSocket.setUseClientMode(true);

                HttpParams httpParams = new BasicHttpParams();
                mSocketFactory.connectSocket(mTCPSocket, mParams.server.getHost(), mParams.server.getPort(), null, 0, httpParams);

                mTCPSocket.startHandshake();

                Log.v(Constants.TAG, "Started handshake");

                mDataInput = new DataInputStream(mTCPSocket.getInputStream());
                mDataOutput = new DataOutputStream(mTCPSocket.getOutputStream());

                // Send version information and authenticate.
                final Mumble.Version.Builder version = Mumble.Version.newBuilder();
                version.setRelease(mParams.clientName);
                version.setVersion(Constants.PROTOCOL_VERSION);
                version.setOs("Android");
                version.setOsVersion(Build.VERSION.RELEASE);

                final Mumble.Authenticate.Builder auth = Mumble.Authenticate.newBuilder();
                auth.setUsername(mParams.server.getUsername());
                //auth.setPassword(mParams.userPassword);
                auth.addCeltVersions(Constants.CELT_VERSION);
                auth.setOpus(mParams.useOpus);

                sendTCPMessage(version.build(), JumbleTCPMessageType.Version);
                sendTCPMessage(auth.build(), JumbleTCPMessageType.Authenticate);
            } catch (SocketException e) {
                handleFatalException(new JumbleConnectionException("Could not open a connection to the host", e, false));
                return;
            } catch (IOException e) {
                handleFatalException(new JumbleConnectionException("An error occurred when communicating with the host", e, false));
                return;
            }

            mConnected = true;

            Log.v(Constants.TAG, "Started listening");

            while(mConnected) {
                try {
                    final short messageType = mDataInput.readShort();
                    int messageLength = mDataInput.readInt();
                    final byte[] data = new byte[messageLength];
                    mDataInput.readFully(data);

                    JumbleTCPMessageType tcpMessageType = JumbleTCPMessageType.values()[messageType];

                    Log.v(Constants.TAG, "IN: "+tcpMessageType);

                    try {
                        Message message = JumbleMessageHandler.getProtobufMessage(data, tcpMessageType);
                        for(JumbleMessageHandler handler : mHandlers) {
                            handler.handleTCPMessage(message, tcpMessageType);
                        }
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    if(!mConnected) // Only handle unexpected exceptions here. This could be the result of a clean disconnect like a Reject or UserRemove.
                        handleFatalException(new JumbleConnectionException("Lost connection to server", e, true));
                    break;
                }
            }

            mConnected = false;

            if(mListener != null) {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onConnectionDisconnected();
                    }
                });
            }
        }

        /**
         * Attempts to send a protobuf message over TCP.
         * @param message The message to send.
         * @param messageType The type of the message to send.
         * @throws IOException if we can't write the message to the server.
         */
        public void sendMessage(Message message, JumbleTCPMessageType messageType) throws IOException {
            mDataOutput.writeShort(messageType.ordinal());
            mDataOutput.writeInt(message.getSerializedSize());
            message.writeTo(mDataOutput);
        }
    }

    /**
     * Class to maintain and receive packets from the UDP connection to a Mumble server.
     */
    private class JumbleUDP implements Runnable {
        private static final int BUFFER_SIZE = 1024;
        private DatagramSocket mUDPSocket;

        public void disconnect() {
            mUDPSocket.disconnect();
        }

        @Override
        public void run() {
            try {
                mUDPSocket = new DatagramSocket();
                mUDPSocket.connect(mHost, mParams.server.getPort());
            } catch (SocketException e) {
                handleFatalException(new JumbleConnectionException("Could not open a connection to the host", e, false));
            }

            DatagramPacket packet = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);

            while(mConnected) {
                try {
                    mUDPSocket.receive(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }

                // Decrypt UDP packet using OCB-AES128
                final byte[] decryptedData = new byte[packet.getLength()];
                mCryptState.decrypt(packet.getData(), decryptedData, packet.getLength());

                final JumbleUDPMessageType dataType = JumbleUDPMessageType.values()[decryptedData[0] >> 5 & 0x7];
            }
        }

        public void sendMessage(byte[] data, int length, JumbleUDPMessageType messageType) throws IOException {
            byte[] encryptedData = new byte[length];
            mCryptState.encrypt(data, encryptedData, length);
            DatagramPacket packet = new DatagramPacket(encryptedData, encryptedData.length);
            packet.setAddress(mHost);
            packet.setPort(mParams.server.getPort());

            mUDPSocket.send(packet);
        }
    }
}
