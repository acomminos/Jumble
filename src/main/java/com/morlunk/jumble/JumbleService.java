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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import com.morlunk.jumble.audio.AudioInput;
import com.morlunk.jumble.audio.AudioOutput;
import com.morlunk.jumble.db.Database;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.protocol.ChannelHandler;
import com.morlunk.jumble.net.JumbleConnection;
import com.morlunk.jumble.net.JumbleConnectionException;
import com.morlunk.jumble.net.JumbleTCPMessageType;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.protocol.JumbleMessageListener;
import com.morlunk.jumble.protocol.TextMessageHandler;
import com.morlunk.jumble.protocol.UserHandler;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.util.MessageFormatter;

import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
    public static final String EXTRAS_TRANSMIT_MODE = "transmit_mode";
    public static final String EXTRAS_USE_OPUS = "use_opus";
    public static final String EXTRAS_FORCE_TCP = "force_tcp";
    public static final String EXTRAS_USE_TOR = "use_tor";
    public static final String EXTRAS_CLIENT_NAME = "client_name";

    public static final String ACTION_DISCONNECT = "com.morlunk.jumble.DISCONNECT";

    // Service settings
    public Server mServer;
    public boolean mShowChatNotifications;
    public boolean mAutoReconnect;
    public byte[] mCertificate;
    public String mCertificatePassword;
    public float mDetectionThreshold;
    public int mTransmitMode;
    public boolean mUseOpus;
    public boolean mForceTcp;
    public boolean mUseTor;
    public String mClientName;

    private JumbleConnection mConnection;
    private Database mDatabase;
    private ChannelHandler mChannelHandler;
    private UserHandler mUserHandler;
    private TextMessageHandler mTextMessageHandler;
    private AudioOutput mAudioOutput;
    private AudioInput mAudioInput;

    private AudioInput.AudioInputListener mAudioInputListener = new AudioInput.AudioInputListener() {
        @Override
        public void onFrameEncoded(byte[] data, int length, JumbleUDPMessageType messageType) {
            if(isConnected())
                mConnection.sendUDPMessage(data, length, false);
        }

        @Override
        public void onTalkStateChanged(final boolean talking) {
            if(!isConnected())
                return;

            new Handler(getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    final User currentUser = getUserHandler().getUser(getSession());
                    currentUser.setTalkState(talking ? User.TalkState.TALKING : User.TalkState.PASSIVE);
                    notifyObservers(new ObserverRunnable() {
                        @Override
                        public void run(IJumbleObserver observer) throws RemoteException {
                            observer.onUserTalkStateUpdated(currentUser);
                        }
                    });
                }
            });
        }
    };

    // Logging
    private List<String> mLogHistory = new ArrayList<String>();
    private SimpleDateFormat mChatDateFormat = new SimpleDateFormat("[h:mm a] ");

    private int mPermissions;

    private RemoteCallbackList<IJumbleObserver> mObservers = new RemoteCallbackList<IJumbleObserver>();

    /**
     * Interface to communicate with observers through the service.
     */
    public interface ObserverRunnable {
        public void run(IJumbleObserver observer) throws RemoteException;
    }

    private IJumbleService.Stub mBinder = new IJumbleService.Stub() {

        @Override
        public void disconnect() throws RemoteException {
            JumbleService.this.disconnect();
        }

        @Override
        public boolean isConnected() throws RemoteException {
            if(mConnection != null)
                return mConnection.isConnected();
            return false;
        }

        @Override
        public boolean isConnecting() throws RemoteException {
            return mConnection != null && !mConnection.isConnected();
        }

        @Override
        public int getSession() throws RemoteException {
            return mConnection.getSession();
        }

        @Override
        public User getSessionUser() throws RemoteException {
            return mUserHandler != null ? mUserHandler.getUser(getSession()) : null;
        }

        @Override
        public Channel getSessionChannel() throws RemoteException {
            User user = getSessionUser();
            return getChannel(user.getChannelId());
        }

        @Override
        public Server getConnectedServer() throws RemoteException {
            return mServer;
        }

        @Override
        public User getUser(int id) throws RemoteException {
            return mUserHandler.getUser(id);
        }

        @Override
        public Channel getChannel(int id) throws RemoteException {
            return mChannelHandler.getChannel(id);
        }

        @Override
        public List getUserList() throws RemoteException {
            return mUserHandler.getUsers();
        }

        @Override
        public List getChannelList() throws RemoteException {
            return mChannelHandler.getChannels();
        }

        @Override
        public List getLogHistory() throws RemoteException {
            return mLogHistory;
        }

        @Override
        public int getPermissions() throws RemoteException {
            return mPermissions;
        }

        @Override
        public int getTransmitMode() throws RemoteException {
            return mTransmitMode;
        }

        @Override
        public boolean isTalking() throws RemoteException {
            return mAudioInput.isRecording();
        }

        @Override
        public void setTalkingState(boolean talking) throws RemoteException {
            if(talking)
                mAudioInput.startRecording();
            else
                mAudioInput.stopRecording();
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
        public void sendAccessTokens(List tokens) throws RemoteException {
            Mumble.Authenticate.Builder ab = Mumble.Authenticate.newBuilder();
            ab.addAllTokens(tokens);
            mConnection.sendTCPMessage(ab.build(), JumbleTCPMessageType.Authenticate);
            mDatabase.setTokens(mServer.getId(), tokens);
        }

        @Override
        public List getAccessTokens() throws RemoteException {
            return mDatabase.getTokens(mServer.getId());
        }

        @Override
        public void requestBanList() throws RemoteException {
            // TODO
        }

        @Override
        public void requestUserList() throws RemoteException {
            // TODO
        }

        @Override
        public void requestPermissions(int channel) throws RemoteException {
            Mumble.PermissionQuery.Builder pqb = Mumble.PermissionQuery.newBuilder();
            pqb.setChannelId(channel);
            mConnection.sendTCPMessage(pqb.build(), JumbleTCPMessageType.PermissionQuery);
        }

        @Override
        public void requestComment(int session) throws RemoteException {
            Mumble.RequestBlob.Builder rbb = Mumble.RequestBlob.newBuilder();
            rbb.addSessionComment(session);
            mConnection.sendTCPMessage(rbb.build(), JumbleTCPMessageType.RequestBlob);
        }

        @Override
        public void requestChannelDescription(int channel) throws RemoteException {
            Mumble.RequestBlob.Builder rbb = Mumble.RequestBlob.newBuilder();
            rbb.addChannelDescription(channel);
            mConnection.sendTCPMessage(rbb.build(), JumbleTCPMessageType.RequestBlob);
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

            // Log message to chat
            User target = getUser(session);
            String formattedMessage = getString(R.string.chat_message_to, MessageFormatter.highlightString(target.getName()), message);
            logMessage(getSessionUser(), formattedMessage);
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

            // Log message to chat
            Channel target = getChannel(channel);
            String formattedMessage = getString(R.string.chat_message_to, MessageFormatter.highlightString(target.getName()), message);
            logMessage(getSessionUser(), formattedMessage);
        }

        @Override
        public void setUserComment(int session, String comment) throws RemoteException {
            Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
            usb.setSession(session);
            usb.setComment(comment);
            mConnection.sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
        }

        @Override
        public void setPrioritySpeaker(int session, boolean priority) throws RemoteException {
            Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
            usb.setSession(session);
            usb.setPrioritySpeaker(priority);
            mConnection.sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
        }

        @Override
        public void removeChannel(int channel) throws RemoteException {
            Mumble.ChannelRemove.Builder crb = Mumble.ChannelRemove.newBuilder();
            crb.setChannelId(channel);
            mConnection.sendTCPMessage(crb.build(), JumbleTCPMessageType.ChannelRemove);
        }

        @Override
        public void setMuteDeafState(int session, boolean mute, boolean deaf) throws RemoteException {
            Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
            usb.setSession(session);
            usb.setMute(mute);
            usb.setDeaf(deaf);
            mConnection.sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
        }

        @Override
        public void setSelfMuteDeafState(boolean mute, boolean deaf) throws RemoteException {
            Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
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
            mDetectionThreshold = extras.getFloat(EXTRAS_DETECTION_THRESHOLD, 0.5f);
            mTransmitMode = extras.getInt(EXTRAS_TRANSMIT_MODE, Constants.TRANSMIT_VOICE_ACTIVITY);
            mUseOpus = extras.getBoolean(EXTRAS_USE_OPUS, true);
            mUseTor = extras.getBoolean(EXTRAS_USE_TOR, false);
            mForceTcp = extras.getBoolean(EXTRAS_FORCE_TCP, false) || mUseTor; // Tor requires TCP connections to work- if it's on, force TCP.
            mClientName = extras.containsKey(EXTRAS_CLIENT_NAME) ? extras.getString(EXTRAS_CLIENT_NAME) : "Jumble";
            connect();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mDatabase = new Database(this);
    }

    @Override
    public void onDestroy() {
        mObservers.kill();
        mDatabase.close();
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public IJumbleService getBinder() {
        return mBinder;
    }

    public void connect() {
        try {
            mConnection = new JumbleConnection(this, this, mServer, mClientName, mCertificate, mCertificatePassword, mForceTcp, mUseOpus, mUseTor);
        } catch (final JumbleConnectionException e) {
            e.printStackTrace();

            notifyObservers(new ObserverRunnable() {
                @Override
                public void run(IJumbleObserver observer) throws RemoteException {
                    observer.onConnectionError(e.getMessage(), e.isAutoReconnectAllowed());
                }
            });

            return;
        }

        mLogHistory.clear();
        mPermissions = 0;

        mChannelHandler = new ChannelHandler(this);
        mUserHandler = new UserHandler(this);
        mTextMessageHandler = new TextMessageHandler(this);
        mAudioOutput = new AudioOutput(this);
        mAudioInput = new AudioInput(this, JumbleUDPMessageType.UDPVoiceOpus, mTransmitMode, mDetectionThreshold, mAudioInputListener);

        mConnection.addMessageHandlers(mChannelHandler, mUserHandler, mTextMessageHandler, mAudioOutput, mAudioInput);
        mConnection.connect();
    }

    public void disconnect() {
        mConnection.disconnect();
        mConnection = null;
    }

    public boolean isConnected() {
        return mConnection != null ? mConnection.isConnected() : false;
    }

    @Override
    public void onConnectionEstablished() {
        Log.v(Constants.TAG, "Connected");

        mAudioOutput.startPlaying();
        if(mTransmitMode == Constants.TRANSMIT_CONTINUOUS || mTransmitMode == Constants.TRANSMIT_VOICE_ACTIVITY)
            mAudioInput.startRecording();

        if(mServer.getId() != -1) {
            // Send access tokens
            Mumble.Authenticate.Builder ab = Mumble.Authenticate.newBuilder();
            ab.addAllTokens(mDatabase.getTokens(mServer.getId()));
            mConnection.sendTCPMessage(ab.build(), JumbleTCPMessageType.Authenticate);
        }

        notifyObservers(new ObserverRunnable() {
            @Override
            public void run(IJumbleObserver observer) throws RemoteException {
                observer.onConnected();
            }
        });
    }

    @Override
    public void onConnectionDisconnected() {
        Log.v(Constants.TAG, "Disconnected");

        mAudioOutput.stopPlaying();
        mAudioInput.stopRecording();

        notifyObservers(new ObserverRunnable() {
            @Override
            public void run(IJumbleObserver observer) throws RemoteException {
                observer.onDisconnected();
            }
        });

        mChannelHandler = null;
        mUserHandler = null;

        stopSelf();
    }

    @Override
    public void onConnectionError(final JumbleConnectionException e) {
        Log.e(Constants.TAG, "Connection error: "+e.getMessage());
        notifyObservers(new ObserverRunnable() {
            @Override
            public void run(IJumbleObserver observer) throws RemoteException {
                observer.onConnectionError(e.getMessage(), e.isAutoReconnectAllowed());
            }
        });
    }

    @Override
    public void onConnectionWarning(String warning) {
        logWarning(warning);
    }

    /**
     * Returns the user ID of this user session.
     * @return Identifier for the local user.
     */
    public int getSession() {
        return mConnection.getSession();
    }

    public UserHandler getUserHandler() {
        return mUserHandler;
    }

    public ChannelHandler getChannelHandler() {
        return mChannelHandler;
    }

    public void setPermissions(int permissions) {
        mPermissions = permissions;
    }

    /*
     * --- HERE BE CALLBACKS ---
     * This code will be called by components of the service like ChannelHandler.
     */

    /**
     * Logs a warning message to the client.
     * @param warning An HTML warning string to be messagxz ed to the client.
     */
    public void logWarning(final String warning) {
        Log.w(Constants.TAG, warning);
        mLogHistory.add(warning);
        notifyObservers(new ObserverRunnable() {
            @Override
            public void run(IJumbleObserver observer) throws RemoteException {
                observer.onLogWarning(warning);
            }
        });
    }

    /**
     * Logs an info message to the client.
     * @param info An HTML info string to be messaged to the client.
     */
    public void logInfo(final String info) {
        if(!mConnection.isSynchronized())
            return; // Don't log messages while synchronizing.

        final String formatInfo = mChatDateFormat.format(new Date())+info;
        Log.v(Constants.TAG, formatInfo);
        mLogHistory.add(formatInfo);
        notifyObservers(new ObserverRunnable() {
            @Override
            public void run(IJumbleObserver observer) throws RemoteException {
                observer.onLogInfo(formatInfo);
            }
        });
    }

    /**
     * Logs a text message to the client.
     * @param user The user that sent the message.
     * @param message An HTML message to send to the client.
     */
    public void logMessage(final User user, final String message) {
        final String formatMessage = mChatDateFormat.format(new Date())+message;
        mLogHistory.add(formatMessage);
        notifyObservers(new ObserverRunnable() {
            @Override
            public void run(IJumbleObserver observer) throws RemoteException {
                observer.onMessageReceived(user, formatMessage);
            }
        });
    }

    /**
     * Iterates through all registered IJumbleObservers and performs the action implemented in the passed ObserverRunnable.
     * @param runnable A runnable to execute on each observer.
     */
    public void notifyObservers(ObserverRunnable runnable) {
        int i = mObservers.beginBroadcast();
        while(i > 0) {
            i--;
            try {
                runnable.run(mObservers.getBroadcastItem(i));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        mObservers.finishBroadcast();
    }
}
