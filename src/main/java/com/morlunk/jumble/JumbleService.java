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

package com.morlunk.jumble;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

import com.morlunk.jumble.audio.AudioInput;
import com.morlunk.jumble.audio.AudioOutput;
import com.morlunk.jumble.exception.AudioException;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleConnection;
import com.morlunk.jumble.net.JumbleException;
import com.morlunk.jumble.net.JumbleTCPMessageType;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.protocol.AudioHandler;
import com.morlunk.jumble.protocol.ModelHandler;
import com.morlunk.jumble.util.JumbleCallbacks;
import com.morlunk.jumble.util.JumbleLogger;
import com.morlunk.jumble.util.ParcelableByteArray;

import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class JumbleService extends Service implements JumbleConnection.JumbleConnectionListener, JumbleLogger {

    static {
        // Use Spongy Castle for crypto implementation so we can create and manage PKCS #12 (.p12) certificates.
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    /** Intent to connect to a Mumble server. See extras. **/
    public static final String ACTION_CONNECT = "com.morlunk.jumble.CONNECT";
    public static final String EXTRAS_SERVER = "server";
    public static final String EXTRAS_AUTO_RECONNECT = "auto_reconnect";
    public static final String EXTRAS_AUTO_RECONNECT_DELAY = "auto_reconnect_delay";
    public static final String EXTRAS_CERTIFICATE = "certificate";
    public static final String EXTRAS_CERTIFICATE_PASSWORD = "certificate_password";
    public static final String EXTRAS_DETECTION_THRESHOLD = "detection_threshold";
    public static final String EXTRAS_AMPLITUDE_BOOST = "amplitude_boost";
    public static final String EXTRAS_TRANSMIT_MODE = "transmit_mode";
    public static final String EXTRAS_INPUT_RATE = "input_frequency";
    public static final String EXTRAS_INPUT_QUALITY = "input_quality";
    public static final String EXTRAS_USE_OPUS = "use_opus";
    public static final String EXTRAS_FORCE_TCP = "force_tcp";
    public static final String EXTRAS_USE_TOR = "use_tor";
    public static final String EXTRAS_CLIENT_NAME = "client_name";
    public static final String EXTRAS_ACCESS_TOKENS = "access_tokens";
    public static final String EXTRAS_AUDIO_SOURCE = "audio_source";
    public static final String EXTRAS_AUDIO_STREAM = "audio_stream";
    public static final String EXTRAS_FRAMES_PER_PACKET = "frames_per_packet";
    /** An optional path to a trust store for CA certificates. */
    public static final String EXTRAS_TRUST_STORE = "trust_store";
    /** The trust store's password. */
    public static final String EXTRAS_TRUST_STORE_PASSWORD = "trust_store_password";
    /** The trust store's format. */
    public static final String EXTRAS_TRUST_STORE_FORMAT = "trust_store_format";
    public static final String EXTRAS_HALF_DUPLEX = "half_duplex";
    /** A list of users that should be local muted upon connection. */
    public static final String EXTRAS_LOCAL_MUTE_HISTORY = "local_mute_history";
    /** A list of users that should be local ignored upon connection. */
    public static final String EXTRAS_LOCAL_IGNORE_HISTORY = "local_ignore_history";
    public static final String EXTRAS_ENABLE_PREPROCESSOR = "enable_preprocessor";

    public static final String ACTION_DISCONNECT = "com.morlunk.jumble.DISCONNECT";

    // Service settings
    private Server mServer;
    private boolean mAutoReconnect;
    private int mAutoReconnectDelay;
    private byte[] mCertificate;
    private String mCertificatePassword;
    private float mDetectionThreshold;
    private float mAmplitudeBoost;
    private int mTransmitMode;
    private boolean mUseOpus;
    private int mInputRate;
    private int mInputQuality;
    private boolean mForceTcp;
    private boolean mUseTor;
    private String mClientName;
    private List<String> mAccessTokens;
    private int mAudioSource;
    private int mAudioStream;
    private int mFramesPerPacket;
    private String mTrustStore;
    private String mTrustStorePassword;
    private String mTrustStoreFormat;
    private boolean mHalfDuplex;
    private List<Integer> mLocalMuteHistory;
    private List<Integer> mLocalIgnoreHistory;
    private boolean mEnablePreprocessor;

    private PowerManager.WakeLock mWakeLock;
    private Handler mHandler;
    private JumbleCallbacks mCallbacks;
    private IJumbleService.Stub mBinder = new JumbleBinder();

    private JumbleConnection mConnection;
    private ModelHandler mModelHandler;
    private AudioHandler mAudioHandler;

    private List<Message> mMessageLog;
    private boolean mReconnecting;

    private AudioInput.AudioInputListener mAudioInputListener = new AudioInput.AudioInputListener() {
        @Override
        public void onFrameEncoded(byte[] data, int length, JumbleUDPMessageType messageType) {
            if(mConnection.isSynchronized()) {
                mConnection.sendUDPMessage(data, length, false);
            }
        }

        @Override
        public void onTalkStateChanged(final boolean talking) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(!isConnected()) return;
                    final User currentUser = mModelHandler.getUser(mConnection.getSession());
                    if(currentUser == null) return;

                    currentUser.setTalkState(talking ? User.TalkState.TALKING : User.TalkState.PASSIVE);
                    try {
                        mCallbacks.onUserTalkStateUpdated(currentUser);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    };

    private AudioOutput.AudioOutputListener mAudioOutputListener = new AudioOutput.AudioOutputListener() {
        @Override
        public void onUserTalkStateUpdated(final User user) {
            try {
                mCallbacks.onUserTalkStateUpdated(user);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public User getUser(int session) {
            return mModelHandler.getUser(session);
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
            mAutoReconnect = extras.getBoolean(EXTRAS_AUTO_RECONNECT, true);
            mAutoReconnectDelay = extras.getInt(EXTRAS_AUTO_RECONNECT_DELAY, 5000);
            mCertificate = extras.getByteArray(EXTRAS_CERTIFICATE);
            mCertificatePassword = extras.getString(EXTRAS_CERTIFICATE_PASSWORD);
            mDetectionThreshold = extras.getFloat(EXTRAS_DETECTION_THRESHOLD, 0.5f);
            mAmplitudeBoost = extras.getFloat(EXTRAS_AMPLITUDE_BOOST, 1.0f);
            mTransmitMode = extras.getInt(EXTRAS_TRANSMIT_MODE, Constants.TRANSMIT_VOICE_ACTIVITY);
            mInputRate = extras.getInt(EXTRAS_INPUT_RATE, AudioHandler.SAMPLE_RATE);
            mInputQuality = extras.getInt(EXTRAS_INPUT_QUALITY, 40000);
            mUseOpus = extras.getBoolean(EXTRAS_USE_OPUS, true);
            mUseTor = extras.getBoolean(EXTRAS_USE_TOR, false);
            mForceTcp = extras.getBoolean(EXTRAS_FORCE_TCP, false) || mUseTor; // Tor requires TCP connections to work- if it's on, force TCP.
            mClientName = extras.containsKey(EXTRAS_CLIENT_NAME) ? extras.getString(EXTRAS_CLIENT_NAME) : "Jumble";
            mAccessTokens = extras.getStringArrayList(EXTRAS_ACCESS_TOKENS);
            mAudioSource = extras.getInt(EXTRAS_AUDIO_SOURCE, MediaRecorder.AudioSource.MIC);
            mAudioStream = extras.getInt(EXTRAS_AUDIO_STREAM, AudioManager.STREAM_MUSIC);
            mFramesPerPacket = extras.getInt(EXTRAS_FRAMES_PER_PACKET, 2);
            mTrustStore = extras.getString(EXTRAS_TRUST_STORE);
            mTrustStorePassword = extras.getString(EXTRAS_TRUST_STORE_PASSWORD);
            mTrustStoreFormat = extras.getString(EXTRAS_TRUST_STORE_FORMAT);
            mHalfDuplex = extras.getBoolean(EXTRAS_HALF_DUPLEX);
            mLocalMuteHistory = extras.getIntegerArrayList(EXTRAS_LOCAL_MUTE_HISTORY);
            mLocalIgnoreHistory = extras.getIntegerArrayList(EXTRAS_LOCAL_IGNORE_HISTORY);
            mEnablePreprocessor = extras.getBoolean(EXTRAS_ENABLE_PREPROCESSOR, true);

            connect();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jumble");
        mHandler = new Handler(getMainLooper());
        mCallbacks = new JumbleCallbacks();
        mMessageLog = new ArrayList<Message>();
    }

    @Override
    public void onDestroy() {
        mCallbacks.kill();
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
            mReconnecting = false;

            mConnection = new JumbleConnection(this);
            mConnection.setForceTCP(mForceTcp);
            mConnection.setUseTor(mUseTor);
            mConnection.setKeys(mCertificate, mCertificatePassword);
            mConnection.setTrustStore(mTrustStore, mTrustStorePassword, mTrustStoreFormat);

            mModelHandler = new ModelHandler(this, mCallbacks, this,
                    mLocalMuteHistory, mLocalIgnoreHistory);
            mAudioHandler = new AudioHandler(this, this, mAudioInputListener, mAudioOutputListener);
            mAudioHandler.setAmplitudeBoost(mAmplitudeBoost);
            mAudioHandler.setBitrate(mInputQuality);
            mAudioHandler.setVADThreshold(mDetectionThreshold);
            mAudioHandler.setTransmitMode(mTransmitMode);
            mAudioHandler.setAudioSource(mAudioSource);
            mAudioHandler.setAudioStream(mAudioStream);
            mAudioHandler.setFramesPerPacket(mFramesPerPacket);
            mAudioHandler.setSampleRate(mInputRate);
            mAudioHandler.setHalfDuplex(mHalfDuplex);
            mAudioHandler.setPreprocessorEnabled(mEnablePreprocessor);
            mConnection.addTCPMessageHandlers(mModelHandler, mAudioHandler);
            mConnection.addUDPMessageHandlers(mAudioHandler);

            mConnection.connect(mServer.getHost(), mServer.getPort());
        } catch (JumbleException e) {
            e.printStackTrace();
            try {
                mCallbacks.onConnectionError(e.getMessage(), e.isAutoReconnectAllowed());
            } catch (RemoteException e1) {
                e1.printStackTrace();
            }
        } catch (AudioException e) {
            e.printStackTrace();
            try {
                mCallbacks.onConnectionError(e.getMessage(), false);
            } catch (RemoteException e1) {
                e1.printStackTrace();
            }
        }
    }

    public void disconnect() {
        mConnection.disconnect();
    }

    public boolean isConnected() {
        return mConnection != null && mConnection.isConnected();
    }

    @Override
    public void onConnectionEstablished() {
        // Send version information and authenticate.
        final Mumble.Version.Builder version = Mumble.Version.newBuilder();
        version.setRelease(mClientName);
        version.setVersion(Constants.PROTOCOL_VERSION);
        version.setOs("Android");
        version.setOsVersion(Build.VERSION.RELEASE);

        final Mumble.Authenticate.Builder auth = Mumble.Authenticate.newBuilder();
        auth.setUsername(mServer.getUsername());
        auth.setPassword(mServer.getPassword());
        auth.addCeltVersions(Constants.CELT_7_VERSION);
        // FIXME: resolve issues with CELT 11 robot voices.
//            auth.addCeltVersions(Constants.CELT_11_VERSION);
        auth.setOpus(mUseOpus);
        auth.addAllTokens(mAccessTokens);

        mConnection.sendTCPMessage(version.build(), JumbleTCPMessageType.Version);
        mConnection.sendTCPMessage(auth.build(), JumbleTCPMessageType.Authenticate);

        try {
            mAudioHandler.initialize();
        } catch (AudioException e) {
            e.printStackTrace();
            onConnectionWarning(e.getMessage());
        }
    }

    @Override
    public void onConnectionSynchronized() {
        Log.v(Constants.TAG, "Connected");
        mWakeLock.acquire();

        try {
            mCallbacks.onConnected();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionHandshakeFailed(X509Certificate[] chain) {
        try {
            final ParcelableByteArray encodedCert = new ParcelableByteArray(chain[0].getEncoded());
            mCallbacks.onTLSHandshakeFailed(encodedCert);
        } catch (CertificateEncodingException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionDisconnected() {
        Log.v(Constants.TAG, "Disconnected");
        if(mWakeLock.isHeld()) mWakeLock.release();

        if (mAudioHandler != null) {
            mAudioHandler.shutdown();
        }

        mMessageLog.clear();

        mConnection = null;
        mModelHandler = null;
        mAudioHandler = null;

        try {
            mCallbacks.onDisconnected();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionError(final JumbleException e) {
        Log.e(Constants.TAG, "Connection error: " + e.getMessage() + ", should reconnect: " + e.isAutoReconnectAllowed());
        mReconnecting = mAutoReconnect && e.isAutoReconnectAllowed();
        if(mReconnecting) {
            Handler mainHandler = new Handler();
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(mReconnecting) connect();
                }
            }, mAutoReconnectDelay);
        }
        try {
            mCallbacks.onConnectionError(e.getMessage(), mReconnecting);
        } catch (RemoteException e1) {
            e1.printStackTrace();
        }
    }

    @Override
    public void onConnectionWarning(String warning) {
        log(Message.Type.WARNING, warning);
    }

    @Override
    public void log(Message.Type type, String message) {
        Message msg = new Message(type, message);
        log(msg);
    }

    @Override
    public void log(Message message) {
        // Only log non-fatal (~INFO) messages post-connect.
        if (mConnection != null &&
                (mConnection.isSynchronized() || message.getType() != Message.Type.INFO)) {
                mMessageLog.add(message);
            try {
                mCallbacks.onMessageLogged(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public class JumbleBinder extends IJumbleService.Stub {
        @Override
        public void disconnect() throws RemoteException {
            JumbleService.this.disconnect();
        }

        @Override
        public boolean isConnected() throws RemoteException {
            return mConnection != null && mConnection.isConnected();
        }

        @Override
        public boolean isConnecting() throws RemoteException {
            return mConnection != null && !mConnection.isConnected();
        }

        @Override
        public boolean isReconnecting() throws RemoteException {
            return mReconnecting;
        }

        @Override
        public void cancelReconnect() throws RemoteException {
            mReconnecting = false;
        }

        @Override
        public long getTCPLatency() throws RemoteException {
            return mConnection.getTCPLatency();
        }

        @Override
        public long getUDPLatency() throws RemoteException {
            return mConnection.getUDPLatency();
        }

        @Override
        public int getMaxBandwidth() throws RemoteException {
            return mConnection.getMaxBandwidth();
        }

        @Override
        public int getCurrentBandwidth() throws RemoteException {
            return 0;
        }

        @Override
        public int getServerVersion() throws RemoteException {
            return mConnection.getServerVersion();
        }

        @Override
        public String getServerRelease() throws RemoteException {
            return mConnection.getServerRelease();
        }

        @Override
        public String getServerOSName() throws RemoteException {
            return mConnection.getServerOSName();
        }

        @Override
        public String getServerOSVersion() throws RemoteException {
            return mConnection.getServerOSVersion();
        }

        @Override
        public int getSession() throws RemoteException {
            return mConnection != null ? mConnection.getSession() : -1;
        }

        @Override
        public User getSessionUser() throws RemoteException {
            return mModelHandler != null ? mModelHandler.getUser(getSession()) : null;
        }

        @Override
        public Channel getSessionChannel() throws RemoteException {
            User user = getSessionUser();
            if (user != null) {
                return getChannel(user.getChannelId());
            }
            return null;
        }

        @Override
        public Server getConnectedServer() throws RemoteException {
            return mServer;
        }

        @Override
        public User getUser(int id) throws RemoteException {
            return mModelHandler.getUser(id);
        }

        @Override
        public Channel getChannel(int id) throws RemoteException {
            return mModelHandler.getChannel(id);
        }

        @Override
        public Channel getRootChannel() throws RemoteException {
            return mModelHandler.getChannel(0);
        }

        @Override
        public int getPermissions() throws RemoteException {
            return mModelHandler.getPermissions();
        }

        @Override
        public List getMessageLog() throws RemoteException {
            return mMessageLog;
        }

        @Override
        public void clearMessageLog() throws RemoteException {
            mMessageLog.clear();
        }

        @Override
        public int getTransmitMode() throws RemoteException {
            return mTransmitMode;
        }

        @Override
        public void setTransmitMode(int transmitMode) throws RemoteException {
            mTransmitMode = transmitMode;
            try {
                mAudioHandler.setTransmitMode(transmitMode);
            } catch (AudioException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void setVADThreshold(float threshold) throws RemoteException {
            mDetectionThreshold = threshold;
            mAudioHandler.setVADThreshold(threshold);
        }

        @Override
        public void setAmplitudeBoost(float boost) throws RemoteException {
            mAmplitudeBoost = boost;
            mAudioHandler.setAmplitudeBoost(boost);
        }

        @Override
        public void setHalfDuplex(boolean enabled) throws RemoteException {
            mAudioHandler.setHalfDuplex(enabled);
        }

        @Override
        public int getCodec() throws RemoteException {
            return mConnection.getCodec();
        }

        @Override
        public boolean isTalking() throws RemoteException {
            return mAudioHandler.isRecording();
        }

        @Override
        public void setTalkingState(boolean talking) throws RemoteException {
            if(getSessionUser() != null &&
                    (getSessionUser().isSelfMuted() || getSessionUser().isMuted())) {
                return;
            }

            if (mTransmitMode != Constants.TRANSMIT_PUSH_TO_TALK) {
                Log.w(Constants.TAG, "Attempted to set talking state when not using PTT");
                return;
            }

            if(talking) {
                try {
                    mAudioHandler.startRecording();
                } catch (AudioException e) {
                    e.printStackTrace();
                    onConnectionWarning(e.getMessage());
                }
            } else {
                mAudioHandler.stopRecording();
            }
        }

        @Override
        public boolean isBluetoothAvailable() throws RemoteException {
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            return audioManager.isBluetoothScoOn();
        }

        @Override
        public void setBluetoothEnabled(boolean enabled) throws RemoteException {
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            if(enabled) audioManager.startBluetoothSco();
            else audioManager.stopBluetoothSco();
        }

        @Override
        public void joinChannel(int channel) throws RemoteException {
            moveUserToChannel(getSession(), channel);
        }

        @Override
        public void moveUserToChannel(int session, int channel) throws RemoteException {
            Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
            usb.setSession(session);
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
            mAccessTokens = new ArrayList<String>(tokens);
            Mumble.Authenticate.Builder ab = Mumble.Authenticate.newBuilder();
            ab.addAllTokens(mAccessTokens);
            mConnection.sendTCPMessage(ab.build(), JumbleTCPMessageType.Authenticate);
        }

        @Override
        public void requestBanList() throws RemoteException {
            throw new UnsupportedOperationException("Not yet implemented"); // TODO
        }

        @Override
        public void requestUserList() throws RemoteException {
            throw new UnsupportedOperationException("Not yet implemented"); // TODO
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
        public void requestAvatar(int session) throws RemoteException {
            Mumble.RequestBlob.Builder rbb = Mumble.RequestBlob.newBuilder();
            rbb.addSessionTexture(session);
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
        public Message sendUserTextMessage(int session, String message) throws RemoteException {
            Mumble.TextMessage.Builder tmb = Mumble.TextMessage.newBuilder();
            tmb.addSession(session);
            tmb.setMessage(message);
            mConnection.sendTCPMessage(tmb.build(), JumbleTCPMessageType.TextMessage);

            User self = getSessionUser();
            User user = getUser(session);
            List<User> users = new ArrayList<User>(1);
            users.add(user);
            Message logMessage = new Message(getSession(), self.getName(), new ArrayList<Channel>(0), new ArrayList<Channel>(0), users, message);
            mMessageLog.add(logMessage);
            return logMessage;
        }

        @Override
        public Message sendChannelTextMessage(int channel, String message, boolean tree) throws RemoteException {
            Mumble.TextMessage.Builder tmb = Mumble.TextMessage.newBuilder();
            if(tree) tmb.addTreeId(channel);
            else tmb.addChannelId(channel);
            tmb.setMessage(message);
            mConnection.sendTCPMessage(tmb.build(), JumbleTCPMessageType.TextMessage);

            User self = getSessionUser();
            Channel targetChannel = getChannel(channel);
            List<Channel> targetChannels = new ArrayList<Channel>();
            targetChannels.add(targetChannel);
            Message logMessage = new Message(getSession(), self.getName(), targetChannels, tree ? targetChannels : new ArrayList<Channel>(0), new ArrayList<User>(0), message);
            mMessageLog.add(logMessage);
            return logMessage;
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
            if (!mute) usb.setSuppress(false);
            mConnection.sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
        }

        @Override
        public void setSelfMuteDeafState(boolean mute, boolean deaf) throws RemoteException {
            Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
            usb.setSelfMute(mute);
            usb.setSelfDeaf(deaf);
            if(!mute && !mAudioHandler.isRecording() && (mTransmitMode == Constants.TRANSMIT_CONTINUOUS || mTransmitMode == Constants.TRANSMIT_VOICE_ACTIVITY))
                try {
                    mAudioHandler.startRecording(); // Resume recording when unmuted for PTT.
                } catch (AudioException e) {
                    e.printStackTrace();
                    onConnectionWarning(e.getMessage());
                }
            else if(mute && mAudioHandler.isRecording())
                mAudioHandler.stopRecording(); // Stop recording when muted.
            mConnection.sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
        }

        @Override
        public void registerObserver(IJumbleObserver observer) throws RemoteException {
            mCallbacks.registerObserver(observer);
        }

        @Override
        public void unregisterObserver(IJumbleObserver observer) throws RemoteException {
            mCallbacks.unregisterObserver(observer);
        }
    }
}
