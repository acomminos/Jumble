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

package com.morlunk.jumble;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import com.google.protobuf.Message;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.ChannelManager;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleConnection;
import com.morlunk.jumble.net.JumbleTCPMessageType;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.protobuf.Mumble;

import java.io.IOException;
import java.security.Security;

public class JumbleService extends Service implements JumbleConnection.JumbleConnectionListener {

    static {
        // Use Spongy Castle for crypto implementation so we can create and manage PKCS #12 (.p12) certificates.
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    public static final String ACTION_CONNECT = "com.morlunk.jumble.connect";
    public static final String EXTRA_PARAMS = "params";

    private JumbleParams mParams;
    private JumbleConnection mConnection;
    private ChannelManager mChannelManager;

    private IJumbleService.Stub mBinder = new IJumbleService.Stub() {

        @Override
        public boolean isConnected() throws RemoteException {
            return mConnection.isConnected();
        }

        @Override
        public Server getConnectedServer() throws RemoteException {
            return null;
        }

        @Override
        public User getUserWithId(int id) throws RemoteException {
            return null;
        }

        @Override
        public Channel getChannelWithId(int id) throws RemoteException {
            return null;
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent.getAction().equals(ACTION_CONNECT)) {
            mParams = intent.getParcelableExtra(EXTRA_PARAMS);
            //connect();
        }
        return START_NOT_STICKY;
    }

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void connect() throws JumbleConnection.JumbleConnectionException {
        mConnection = new JumbleConnection(this, this, mParams.certificatePath, mParams.certificatePassword); // FIXME
        mConnection.connect(mParams.server);
    }

    public boolean isConnected() {
        return mConnection.isConnected();
    }

    @Override
    public void onConnectionEstablished() {
        Log.v(Constants.TAG, "Connected");

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

            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(Void... voids) {
                    try {
                        mConnection.sendTCPMessage(version.build(), JumbleTCPMessageType.Version);
                        mConnection.sendTCPMessage(auth.build(), JumbleTCPMessageType.Authenticate);
                    } catch (IOException e) {
                        e.printStackTrace();
                        mConnection.disconnect(); // We need to be able to send these packets.
                    }
                    return null;
                }
            }.execute();
    }

    @Override
    public void onConnectionDisconnected() {
        Log.v(Constants.TAG, "Disconnected");
    }

    @Override
    public void onConnectionError(JumbleConnection.JumbleConnectionException e) {
        Log.e(Constants.TAG, "Connection error: "+e.getMessage());
        e.getCause().printStackTrace();
    }

    @Override
    public void onTCPDataReceived(byte[] data, JumbleTCPMessageType messageType) {

    }

    @Override
    public void onUDPDataReceived(byte[] data, JumbleUDPMessageType dataType) {

    }
}
