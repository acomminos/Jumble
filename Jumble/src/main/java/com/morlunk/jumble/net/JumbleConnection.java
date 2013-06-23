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

import com.google.protobuf.Message;
import com.morlunk.jumble.model.Server;
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

    private Server mServer;

    // Authentication
    private KeyStore mKeyStore;
    private String mKeyStorePassword;

    // Networking
    private ExecutorService mExecutorService;
    private JumbleTCP mTCP;
    private JumbleUDP mUDP;

    public JumbleConnection(Server host,
                            KeyStore keyStore,
                            String keyStorePassword) {
        mServer = host;
        mKeyStore = keyStore;
        mKeyStorePassword = keyStorePassword;
        mExecutorService = Executors.newFixedThreadPool(2); // One TCP thread, one UDP thread.
    }

    public void connect(Server server) {
        mServer = server;
        mTCP = new JumbleTCP();
        mUDP = new JumbleUDP();
        mExecutorService.submit(mTCP);
        mExecutorService.submit(mUDP);
    }

    /**
     * Gracefully shuts down all networking.
     */
    public void disconnect() {
        try {
            mTCP.disconnect();
            mUDP.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mTCP = null;
        mUDP = null;
    }

    /**
     * Immediately shuts down all network threads.
     */
    public void forceDisconnect() {
        mExecutorService.shutdownNow();
    }

    /**
     * Attempts to load the PKCS12 certificate from the specified path.
     * You must call this method before establishing a TCP connection if you wish to use a certificate.
     * @param certificatePath The absolute path of the PKCS12 (.p12) certificate.
     * @param certificatePassword The password to decrypt the key store.
     * @throws KeyStoreException if an error occurred in the creation of the key store.
     * @throws NoSuchAlgorithmException if the PKCS12 algorithm is not supported by the platform.
     * @throws IOException if there was an error reading the p12 file.
     * @throws CertificateException if an exception occurred reading the certificates from the p12 file.
     */
    public void loadCertificate(String certificatePath, String certificatePassword) throws KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException {
        InputStream certificateStream = new FileInputStream(certificatePath);
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(certificateStream, certificatePassword.toCharArray());
        mKeyStore = keyStore;
        mKeyStorePassword = certificatePassword;
    }

    public void sendTCPMessage(Message message, JumbleMessageType messageType) throws IOException {
        mTCP.sendMessage(message, messageType);
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
        public Void call() throws SSLException {
            try {
                JumbleSSLSocketFactory socketFactory = new JumbleSSLSocketFactory(mKeyStore, mKeyStorePassword);

                SSLSocket socket = (SSLSocket)socketFactory.createSocket();
                socket.setKeepAlive(true);
                socket.setEnabledProtocols(new String[] {"TLSv1"});
                socket.setUseClientMode(true);

                HttpParams httpParams = new BasicHttpParams();
                socketFactory.connectSocket(socket, mServer.getHost(), mServer.getPort(), null, 0, httpParams);

                socket.startHandshake();
            } catch (KeyManagementException e) {
                throw new SSLException("Could not recover keys from certificate!", e);
            } catch (KeyStoreException e) {
                throw new SSLException("Could not recover keys from certificate!", e);
            } catch (UnrecoverableKeyException e) {
                throw new SSLException("Could not recover keys from certificate!", e);
            } catch (IOException e) {
                throw new SSLException("An error occurred when creating the SSL socket.", e);
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
        private DatagramSocket mUDPSocket;

        public void disconnect() {
            mUDPSocket.disconnect();
        }

        @Override
        public Void call() throws SocketException, UnknownHostException {
            mUDPSocket = new DatagramSocket();
            mUDPSocket.connect(InetAddress.getByName(mServer.getHost()), mServer.getPort());

            return null;
        }

        public void sendData(byte[] data, JumbleUDPMessageType messageType) {

        }
    }
}
