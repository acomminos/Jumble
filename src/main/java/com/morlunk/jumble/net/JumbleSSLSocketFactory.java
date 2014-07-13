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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class JumbleSSLSocketFactory {
    private SSLContext mContext;
    private JumbleTrustManagerWrapper mTrustWrapper;

    public JumbleSSLSocketFactory(KeyStore keystore, String keystorePassword, String trustStorePath, String trustStorePassword, String trustStoreFormat) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException, NoSuchProviderException, IOException, CertificateException {
        mContext = SSLContext.getInstance("TLS");

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
        kmf.init(keystore, keystorePassword != null ? keystorePassword.toCharArray() : new char[0]);

        if(trustStorePath != null) {
            KeyStore trustStore = KeyStore.getInstance(trustStoreFormat);
            FileInputStream fis = new FileInputStream(trustStorePath);
            trustStore.load(fis, trustStorePassword.toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            mTrustWrapper = new JumbleTrustManagerWrapper((X509TrustManager) tmf.getTrustManagers()[0]);
            Log.i(Constants.TAG, "Using custom trust store " + trustStorePath + " with system trust store");
        } else {
            mTrustWrapper = new JumbleTrustManagerWrapper(null);
            Log.i(Constants.TAG, "Using system trust store");
        }

        mContext.init(kmf.getKeyManagers(), new TrustManager[] { mTrustWrapper }, null);
    }

    /**
     * Creates a new SSLSocket that runs through a SOCKS5 proxy to reach its destination.
     */
    public SSLSocket createTorSocket(InetAddress host, int port, String proxyHost, int proxyPort) throws IOException {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort));
        Socket socket = new Socket(proxy);
        socket.connect(new InetSocketAddress(host, port));
        return (SSLSocket) mContext.getSocketFactory().createSocket(socket, host.getHostName(), port, true);
    }

    public SSLSocket createSocket(InetAddress host, int port) throws IOException {
        return (SSLSocket) mContext.getSocketFactory().createSocket(host, port);
    }

    /**
     * Gets the certificate chain of the remote host.
     * @return The remote server's certificate chain, or null if a connection has not reached handshake yet.
     */
    public X509Certificate[] getServerChain() {
        return mTrustWrapper.getServerChain();
    }

    /**
     * Wraps around a custom trust manager and stores the certificate chains that did not validate.
     * We can then send the chain to the user for manual validation.
     */
    private static class JumbleTrustManagerWrapper implements X509TrustManager {

        private X509TrustManager mDefaultTrustManager;
        private X509TrustManager mTrustManager;
        private X509Certificate[] mServerChain;

        public JumbleTrustManagerWrapper(X509TrustManager trustManager) throws NoSuchAlgorithmException, KeyStoreException {
            TrustManagerFactory dmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            dmf.init((KeyStore) null);
            mDefaultTrustManager = (X509TrustManager) dmf.getTrustManagers()[0];
            mTrustManager = trustManager;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                mDefaultTrustManager.checkClientTrusted(chain, authType);
            } catch (CertificateException e) {
                if(mTrustManager != null) mTrustManager.checkClientTrusted(chain, authType);
                else throw e;
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            mServerChain = chain;
            try {
                mDefaultTrustManager.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                if(mTrustManager != null) mTrustManager.checkServerTrusted(chain, authType);
                else throw e;
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return mDefaultTrustManager.getAcceptedIssuers();
        }

        public X509Certificate[] getServerChain() {
            return mServerChain;
        }
    }
}