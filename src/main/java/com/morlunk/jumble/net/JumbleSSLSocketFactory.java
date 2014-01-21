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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class JumbleSSLSocketFactory {
    private SSLContext mContext;

    public JumbleSSLSocketFactory(KeyStore keystore, String keystorePassword) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException,
            UnrecoverableKeyException, NoSuchProviderException {
        mContext = SSLContext.getInstance("TLS");

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
        kmf.init(keystore, keystorePassword != null ? keystorePassword.toCharArray() : new char[0]);

        mContext.init(kmf.getKeyManagers(), new TrustManager[] { new JumblePermissiveTrustManager() }, new SecureRandom());
    }

    /**
     * Creates a new SSLSocket that runs through a SOCKS5 proxy to reach its destination.
     */
    public SSLSocket createTorSocket(String host, int port, String proxyHost, int proxyPort) throws IOException {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort));
        Socket socket = new Socket(proxy);
        socket.connect(new InetSocketAddress(host, port));
        return (SSLSocket) mContext.getSocketFactory().createSocket(socket, host, port, true);
    }

    public SSLSocket createSocket(String host, int port) throws IOException {
        return (SSLSocket) mContext.getSocketFactory().createSocket(host, port);
    }

    /**
     * This is horrible practice- we should prompt the user when the server's certificate is not trusted.
     * TODO FIX: SECURITY
     */
    private static class JumblePermissiveTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}