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
import android.os.Handler;
import android.os.Looper;
import com.google.protobuf.Message;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.protobuf.Mumble;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JumbleConnection {

    public interface JumbleConnectionListener {
        public void onConnectionEstablished();
        public void onConnectionDisconnected();
        public void onConnectionError();

        public void onTCPMessageReceived(Message message, JumbleMessageType messageType);
        public void onUDPDataReceived(byte[] data, JumbleUDPMessageType dataType);
    }

    private Context mContext;
    private JumbleConnectionListener mListener;

    // Authentication
    private JumbleSSLSocketFactory mSocketFactory;

    // Threading
    private ExecutorService mExecutorService;
    private Looper mLooper;

    // Networking
    private Server mServer;
    private JumbleTCP mTCP;
    private JumbleUDP mUDP;
    private boolean mConnected;

    /**
     * Creates a new JumbleConnection object to facilitate server connections.
     * @param context An Android context.
     * @param certificatePath The absolute path of the PKCS12 (.p12) certificate.
     * @param certificatePassword The password to decrypt the key store.
     * @throws CertificateException if an exception occurred parsing the p12 file.
     * @throws IOException if there was an error reading the p12 file.
     */
    public JumbleConnection(Context context,
                            String certificatePath,
                            String certificatePassword) throws CertificateException, IOException {
        mContext = context;
        mLooper = context.getMainLooper();
        setupSocketFactory(certificatePath, certificatePassword);
    }

    public void connect(Server server) {
        mServer = server;

        mExecutorService = Executors.newFixedThreadPool(2); // One TCP thread, one UDP thread.
        mTCP = new JumbleTCP();
        mUDP = new JumbleUDP();
        mExecutorService.submit(mTCP);
        mExecutorService.submit(mUDP);
        mConnected = true;
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
    }

    /**
     * Immediately shuts down all network threads.
     */
    public void forceDisconnect() {
        mConnected = false;
        mExecutorService.shutdownNow();
        mTCP = null;
        mUDP = null;
    }

    /**
     * Attempts to load the PKCS12 certificate from the specified path, and set up an SSL socket factory.
     * You must call this method before establishing a TCP connection.
     * @param certificatePath The absolute path of the PKCS12 (.p12) certificate. May be null.
     * @param certificatePassword The password to decrypt the key store. May be null.
     * @throws KeyStoreException if an error occurred in the creation of the key store.
     * @throws NoSuchAlgorithmException if the PKCS12 algorithm is not supported by the platform.
     * @throws CertificateException if an exception occurred parsing the p12 file.
     * @throws IOException if there was an error reading the p12 file.
     */
    private void setupSocketFactory(String certificatePath, String certificatePassword) throws CertificateException, IOException {
        try {
            KeyStore keyStore = null;
            if(certificatePath != null) {
                InputStream certificateStream = new FileInputStream(certificatePath);
                keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(certificateStream, certificatePassword.toCharArray());
            }
            mSocketFactory = new JumbleSSLSocketFactory(keyStore, certificatePassword);
        } catch (KeyManagementException e) {
            throw new CertificateException("Could not recover keys from certificate!", e);
        } catch (KeyStoreException e) {
            throw new CertificateException("Could not recover keys from certificate!", e);
        } catch (UnrecoverableKeyException e) {
            throw new CertificateException("Could not recover keys from certificate!", e);
        } catch (NoSuchProviderException e) {
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

    public void sendTCPMessage(Message message, JumbleMessageType messageType) throws IOException {
        mTCP.sendMessage(message, messageType);
    }

    public JumbleConnectionListener getListener() {
        return mListener;
    }

    public void setListener(JumbleConnectionListener listener) {
        mListener = listener;
    }

    /**
     * Class to maintain and interface with the TCP connection to a Mumble server.
     */
    private class JumbleTCP implements Callable<Void> {
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

        @Override
        public Void call() throws SSLException, IOException {
            SSLSocket socket = (SSLSocket)mSocketFactory.createSocket();
            socket.setKeepAlive(true);
            socket.setEnabledProtocols(new String[] {"TLSv1"});
            socket.setUseClientMode(true);

            HttpParams httpParams = new BasicHttpParams();
            mSocketFactory.connectSocket(socket, mServer.getHost(), mServer.getPort(), null, 0, httpParams);

            socket.startHandshake();

            while(mConnected) {
                short messageType = mDataInput.readShort();
                int messageLength = mDataInput.readInt();
                byte[] data = new byte[messageLength];
                mDataInput.readFully(data);
            }

            return null;
        }

        /**
         * Attempts to send a protobuf message over TCP.
         * @param message The message to send.
         * @param messageType The type of the message to send.
         * @throws IOException if we can't write the message to the server.
         */
        public void sendMessage(Message message, JumbleMessageType messageType) throws IOException {
            mDataOutput.writeShort(messageType.ordinal());
            mDataOutput.writeInt(message.getSerializedSize());
            message.writeTo(mDataOutput);
        }
    }

    /**
     * Class to maintain and receive packets from the UDP connection to a Mumble server.
     */
    private class JumbleUDP implements Callable<Void> {
        private static final int BUFFER_SIZE = 1024;
        private DatagramSocket mUDPSocket;

        public void disconnect() {
            mUDPSocket.disconnect();
        }

        @Override
        public Void call() throws IOException {
            mUDPSocket = new DatagramSocket();
            mUDPSocket.connect(InetAddress.getByName(mServer.getHost()), mServer.getPort());
            DatagramPacket packet = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);

            while(mConnected) {
                mUDPSocket.receive(packet);

                // TODO DECRYPT
                final byte[] decryptedData = null;
                final JumbleUDPMessageType dataType = JumbleUDPMessageType.values()[decryptedData[0] >> 5 & 0x7];

                if(mListener != null) {
                    Handler handler = new Handler(mLooper);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            mListener.onUDPDataReceived(decryptedData, dataType);
                        }
                    });
                }
            }

            return null;
        }

        public void sendData(byte[] data, JumbleUDPMessageType messageType) {

        }
    }
}
