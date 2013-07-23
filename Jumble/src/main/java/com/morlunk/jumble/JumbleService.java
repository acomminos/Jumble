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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import com.google.protobuf.Message;
import com.morlunk.jumble.audio.Audio;
import com.morlunk.jumble.audio.AudioOutput;
import com.morlunk.jumble.db.Database;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.ChannelManager;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.model.UserManager;
import com.morlunk.jumble.net.JumbleConnection;
import com.morlunk.jumble.net.JumbleConnectionException;
import com.morlunk.jumble.net.JumbleMessageHandler;
import com.morlunk.jumble.net.JumbleTCPMessageType;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.protobuf.Mumble;

import java.io.IOException;
import java.security.Security;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class JumbleService extends Service implements JumbleConnection.JumbleConnectionListener {

    static {
        // Use Spongy Castle for crypto implementation so we can create and manage PKCS #12 (.p12) certificates.
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    public static final String ACTION_CONNECT = "com.morlunk.jumble.CONNECT";
    public static final String EXTRAS_SERVER = "server";
    public static final String EXTRAS_SHOW_CHAT_NOTIFICATION = "show_chat_notifications";
    public static final String EXTRAS_AUTO_RECONNECT = "auto_reconnect";
    public static final String EXTRAS_CERTIFICATE = "certificate";
    public static final String EXTRAS_CERTIFICATE_PASSWORD = "certificate_password";
    public static final String EXTRAS_DETECTION_THRESHOLD = "detection_threshold";
    public static final String EXTRAS_PUSH_TO_TALK = "use_ptt";
    public static final String EXTRAS_USE_OPUS = "use_opus";
    public static final String EXTRAS_FORCE_TCP = "force_tcp";
    public static final String EXTRAS_CLIENT_NAME = "client_name";

    public static final String ACTION_DISCONNECT = "com.morlunk.jumble.DISCONNECT";

    public static final int NOTIFICATION_CONNECTION = 0;

    // Service settings
    public Server mServer;
    public boolean mShowChatNotifications;
    public boolean mAutoReconnect;
    public byte[] mCertificate;
    public String mCertificatePassword;
    public int mDetectionThreshold;
    public boolean mUsePushToTalk;
    public boolean mUseOpus;
    public boolean mForceTcp;
    public String mClientName;

    private JumbleConnection mConnection;
    private Database mDatabase;
    private ChannelManager mChannelManager;
    private UserManager mUserManager;
    private AudioOutput mAudioOutput;

    private RemoteCallbackList<IJumbleObserver> mObservers = new RemoteCallbackList<IJumbleObserver>();

    private IJumbleService.Stub mBinder = new IJumbleService.Stub() {

        @Override
        public void disconnect() throws RemoteException {
            JumbleService.this.disconnect();
        }

        @Override
        public boolean isConnected() throws RemoteException {
            return mConnection.isConnected();
        }

        @Override
        public int getSession() throws RemoteException {
            return mConnection.getSession();
        }

        @Override
        public Server getConnectedServer() throws RemoteException {
            return mServer;
        }

        @Override
        public User getUserWithId(int id) throws RemoteException {
            return mUserManager.getUser(id);
        }

        @Override
        public Channel getChannelWithId(int id) throws RemoteException {
            return mChannelManager.getChannel(id);
        }

        @Override
        public List getChannelList() throws RemoteException {
            return mChannelManager.getChannels();
        }

        @Override
        public void joinChannel(int channel) throws RemoteException {
            Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
            usb.setSession(mConnection.getSession());
            usb.setChannelId(channel);
            mConnection.sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
        }

        @Override
        public void createChannel(int parent, String name, String description, int position, boolean temporary) throws RemoteException {
            Mumble.ChannelState.Builder csb = Mumble.ChannelState.newBuilder();
            csb.setParent(parent);
            csb.setName(name);
            csb.setDescription(description);
            csb.setPosition(position);
            csb.setTemporary(temporary);
            mConnection.sendTCPMessage(csb.build(), JumbleTCPMessageType.ChannelState);
        }

        @Override
        public void requestBanList() throws RemoteException {

        }

        @Override
        public void requestUserList() throws RemoteException {

        }

        @Override
        public void registerUser(int session) throws RemoteException {
            Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
            usb.setSession(session);
            usb.setUserId(0);
            mConnection.sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
        }

        @Override
        public void kickBanUser(int session, String reason, boolean ban) throws RemoteException {
            Mumble.UserRemove.Builder urb = Mumble.UserRemove.newBuilder();
            urb.setSession(session);
            urb.setReason(reason);
            urb.setBan(ban);
            mConnection.sendTCPMessage(urb.build(), JumbleTCPMessageType.UserRemove);
        }

        @Override
        public void sendUserTextMessage(int session, String message) throws RemoteException {
            Mumble.TextMessage.Builder tmb = Mumble.TextMessage.newBuilder();
            tmb.addSession(session);
            tmb.setMessage(message);
            mConnection.sendTCPMessage(tmb.build(), JumbleTCPMessageType.TextMessage);
        }

        @Override
        public void sendChannelTextMessage(int channel, String message, boolean tree) throws RemoteException {
            Mumble.TextMessage.Builder tmb = Mumble.TextMessage.newBuilder();
            if(tree)
                tmb.addTreeId(channel);
            else
                tmb.addChannelId(channel);
            tmb.setMessage(message);
            mConnection.sendTCPMessage(tmb.build(), JumbleTCPMessageType.TextMessage);
        }

        @Override
        public void setUserComment(int session, String comment) throws RemoteException {
            Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
            usb.setSession(session);
            usb.setComment(comment);
            mConnection.sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
        }

        @Override
        public void removeChannel(int channel) throws RemoteException {
            Mumble.ChannelRemove.Builder crb = Mumble.ChannelRemove.newBuilder();
            crb.setChannelId(channel);
            mConnection.sendTCPMessage(crb.build(), JumbleTCPMessageType.ChannelRemove);
        }

        @Override
        public void setSelfMuteDeafState(boolean mute, boolean deaf) throws RemoteException {
            User currentUser = mUserManager.getUser(mConnection.getSession());
            currentUser.setSelfMuted(mute);
            currentUser.setSelfDeafened(deaf);

            Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
            usb.setSession(mConnection.getSession());
            usb.setSelfMute(mute);
            usb.setSelfDeaf(deaf);
            mConnection.sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
        }

        @Override
        public void registerObserver(IJumbleObserver observer) {
            mObservers.register(observer);
        }

        @Override
        public void unregisterObserver(IJumbleObserver observer) {
            mObservers.unregister(observer);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null &&
                intent.getAction() != null &&
                intent.getExtras() != null &&
                intent.getAction().equals(ACTION_CONNECT)) {
            // Get connection parameters
            Bundle extras = intent.getExtras();
            mServer = extras.getParcelable(EXTRAS_SERVER);
            mShowChatNotifications = extras.getBoolean(EXTRAS_SHOW_CHAT_NOTIFICATION, true);
            mAutoReconnect = extras.getBoolean(EXTRAS_AUTO_RECONNECT, true);
            mCertificate = extras.getByteArray(EXTRAS_CERTIFICATE);
            mCertificatePassword = extras.getString(EXTRAS_CERTIFICATE_PASSWORD);
            mDetectionThreshold = extras.getInt(EXTRAS_DETECTION_THRESHOLD, 1400);
            mUsePushToTalk = extras.getBoolean(EXTRAS_PUSH_TO_TALK, false);
            mUseOpus = extras.getBoolean(EXTRAS_USE_OPUS, true);
            mForceTcp = extras.getBoolean(EXTRAS_FORCE_TCP, false);
            mClientName = extras.containsKey(EXTRAS_CLIENT_NAME) ? extras.getString(EXTRAS_CLIENT_NAME) : "Jumble";
            connect();
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mDatabase = new Database(this);
    }

    @Override
    public void onDestroy() {
        mObservers.kill();
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void connect() {
        try {
            mConnection = new JumbleConnection(this, this, mServer, mClientName, mCertificate, mCertificatePassword, mForceTcp, mUseOpus);
        } catch (JumbleConnectionException e) {
            e.printStackTrace();

            int i = mObservers.beginBroadcast();
            while(i > 0) {
                i--;
                try {
                    mObservers.getBroadcastItem(i).onConnectionError(e.getMessage(), e.isAutoReconnectAllowed());
                } catch (RemoteException e2) {}
            }
            mObservers.finishBroadcast();

            return;
        }

        mChannelManager = new ChannelManager(this);
        mUserManager = new UserManager(this);
        mAudioOutput = new AudioOutput(this);

        // Add message handlers for all managers
        mConnection.addMessageHandler(mChannelManager);
        mConnection.addMessageHandler(mUserManager);
        mConnection.addMessageHandler(mAudioOutput);

        mConnection.connect();
    }

    public void disconnect() {
        mConnection.removeMessageHandler(mChannelManager);
        mConnection.removeMessageHandler(mUserManager);
        mConnection.removeMessageHandler(mAudioOutput);
        mConnection.disconnect();
        mConnection = null;
    }

    public boolean isConnected() {
        return mConnection.isConnected();
    }

    @Override
    public void onConnectionEstablished() {
        Log.v(Constants.TAG, "Connected");

        mAudioOutput.startPlaying();
        showNotification();

        int i = mObservers.beginBroadcast();
        while(i > 0) {
            i--;
            try {
                mObservers.getBroadcastItem(i).onConnected();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        mObservers.finishBroadcast();
    }

    @Override
    public void onConnectionDisconnected() {
        Log.v(Constants.TAG, "Disconnected");

        mAudioOutput.stopPlaying();
        hideNotification();

        int i = mObservers.beginBroadcast();
        while(i > 0) {
            i--;
            try {
                mObservers.getBroadcastItem(i).onDisconnected();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        mObservers.finishBroadcast();

        mChannelManager = null;
        mUserManager = null;
    }

    @Override
    public void onConnectionError(JumbleConnectionException e) {
        Log.e(Constants.TAG, "Connection error: "+e.getMessage());
        int i = mObservers.beginBroadcast();
        while(i > 0) {
            i--;
            try {
                mObservers.getBroadcastItem(i).onConnectionError(e.getMessage(), e.isAutoReconnectAllowed());
            } catch (RemoteException e2) {
                e2.printStackTrace();
            }
        }
        mObservers.finishBroadcast();
    }

    @Override
    public void onConnectionWarning(String warning) {
        Log.e(Constants.TAG, "Connection warning: "+warning);
        int i = mObservers.beginBroadcast();
        while(i > 0) {
            i--;
            try {
                mObservers.getBroadcastItem(i).onLogWarning(warning);
            } catch (RemoteException e2) {
                e2.printStackTrace();
            }
        }
        mObservers.finishBroadcast();
    }

    /**
     * Returns the user ID of this user session.
     * @return Identifier for the local user.
     */
    public int getSession() {
        return mConnection.getSession();
    }

    public UserManager getUserManager() {
        return mUserManager;
    }

    public ChannelManager getChannelManager() {
        return mChannelManager;
    }

    private void showNotification() {
        NotificationCompat.Builder nb = new NotificationCompat.Builder(this);
        nb.setContentTitle(mClientName);
        nb.setSmallIcon(android.R.drawable.ic_menu_call);
        nb.setContentText(getString(R.string.notification_connected));
        //startForeground(NOTIFICATION_CONNECTION, nb.build());
    }

    private void hideNotification() {
        //stopForeground(true);
    }

    /*
     * --- HERE BE CALLBACKS ---
     * This code will be called by components of the service like ChannelManager.
     */
}
