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

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;

public class JumbleConnection {

    private Server mServer;

    // Authentication
    private KeyStore mKeyStore;
    private String mKeyStorePassword;

    // Networking
    private SSLSocket mTCPSocket;
    private DatagramSocket mUDPSocket;
    private DataInputStream mDataInput;
    private DataOutputStream mDataOutput;

    public JumbleConnection() {

    }

    public void connect(Server server) throws UnrecoverableKeyException, NoSuchAlgorithmException, IOException, KeyManagementException, KeyStoreException, NoSuchProviderException {
        mServer = server;

        mTCPSocket = createTCPSocket();
        mUDPSocket = createUDPSocket();
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

    private SSLSocket createTCPSocket() throws IOException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, NoSuchProviderException, KeyManagementException {
        JumbleSSLSocketFactory socketFactory = new JumbleSSLSocketFactory(mKeyStore, mKeyStorePassword);

        SSLSocket socket = (SSLSocket)socketFactory.createSocket();
        socket.setKeepAlive(true);
        socket.setEnabledProtocols(new String[] {"TLS"});
        socket.setUseClientMode(true);

        HttpParams httpParams = new BasicHttpParams();
        socketFactory.connectSocket(socket, mServer.getHost(), mServer.getPort(), null, 0, httpParams);

        socket.startHandshake();

        return socket;
    }

    private DatagramSocket createUDPSocket() throws SocketException, UnknownHostException {
        DatagramSocket socket = new DatagramSocket();
        socket.connect(InetAddress.getByName(mServer.getHost()), mServer.getPort());
        return socket;
    }

    public void disconnect() {
        try {
            mTCPSocket.close();
        } catch (IOException e) {
            // We don't need to report an error in closing a socket.
            e.printStackTrace();
        }
        mUDPSocket.close();

        mServer = null;
    }

    public void sendTCPMessage(Message message, JumbleMessageType messageType) throws IOException {
        mDataOutput.writeShort(messageType.ordinal());
        mDataOutput.writeInt(message.getSerializedSize());
        message.writeTo(mDataOutput);
    }

}
