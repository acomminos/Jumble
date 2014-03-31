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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.morlunk.jumble.audio.Audio;
import com.morlunk.jumble.audio.AudioInput;
import com.morlunk.jumble.audio.AudioOutput;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleConnection;
import com.morlunk.jumble.net.JumbleConnectionException;
import com.morlunk.jumble.net.JumbleTCPMessageType;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.protocol.ChannelHandler;
import com.morlunk.jumble.protocol.TextMessageHandler;
import com.morlunk.jumble.protocol.UserHandler;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;

public class JumbleService extends Service implements JumbleConnection.JumbleConnectionListener {

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

    private JumbleConnection mConnection;
    private ChannelHandler mChannelHandler;
    private UserHandler mUserHandler;
    private TextMessageHandler mTextMessageHandler;
    private AudioOutput mAudioOutput;
    private AudioInput mAudioInput;
    private PowerManager.WakeLock mWakeLock;

    private boolean mBluetoothOn = false;
    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int audioState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR);
            switch (audioState) {
                case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                    Toast.makeText(JumbleService.this, R.string.bluetooth_connected, Toast.LENGTH_LONG).show();
                    mAudioOutput.stopPlaying();
                    if(isConnected()) {
                        mBluetoothOn = true;
                        mAudioOutput.startPlaying(true);
                    }
                    break;
                case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                case AudioManager.SCO_AUDIO_STATE_ERROR:
                    if(mAudioOutput.isPlaying() && mBluetoothOn)
                        Toast.makeText(JumbleService.this, R.string.bluetooth_disconnected, Toast.LENGTH_LONG).show();
                    mAudioOutput.stopPlaying();
                    if(isConnected())
                        mAudioOutput.startPlaying(false);
                    mBluetoothOn = false;
                    break;
            }
        }
    };

    private AudioInput.AudioInputListener mAudioInputListener = new AudioInput.AudioInputListener() {
        @Override
        public void onFrameEncoded(byte[] data, int length, JumbleUDPMessageType messageType) {
            if(isConnected())
                mConnection.sendUDPMessage(data, length, false);
        }

        @Override
        public void onTalkStateChanged(final boolean talking) {

            try {
                if(!isConnected())
                    return;

                final User currentUser = getBinder().getSessionUser();

                new Handler(getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        currentUser.setTalkState(talking ? User.TalkState.TALKING : User.TalkState.PASSIVE);
                        notifyObservers(new ObserverRunnable() {
                            @Override
                            public void run(IJumbleObserver observer) throws RemoteException {
                                observer.onUserTalkStateUpdated(currentUser);
                            }
                        });
                    }
                });
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    private int mPermissions;
    private List<Message> mMessageLog = new ArrayList<Message>();
    private boolean mReconnecting;

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
            if(isConnected())
                JumbleService.this.disconnect();
        }

        @Override
        public boolean isConnected() throws RemoteException {
            return mConnection.isConnected();
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
        public int getPermissions() throws RemoteException {
            return mPermissions;
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

            // Reconfigure audio input/output to accommodate for change in transmit mode.
            if(mConnection != null && mConnection.isConnected()) {
                if(transmitMode == Constants.TRANSMIT_CONTINUOUS || transmitMode == Constants.TRANSMIT_VOICE_ACTIVITY)
                    mAudioInput.startRecording();
                else
                    mAudioInput.stopRecording();
            }
        }

        @Override
        public void setVADThreshold(float threshold) throws RemoteException {
            mDetectionThreshold = threshold;
        }

        @Override
        public void setAmplitudeBoost(float boost) throws RemoteException {
            mAmplitudeBoost = boost;
        }

        @Override
        public int getCodec() throws RemoteException {
            return mConnection.getCodec();
        }

        @Override
        public boolean isTalking() throws RemoteException {
            return mAudioInput.isRecording();
        }

        @Override
        public void setTalkingState(boolean talking) throws RemoteException {
            if(talking) mAudioInput.startRecording();
            else mAudioInput.stopRecording();
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
            Mumble.Authenticate.Builder ab = Mumble.Authenticate.newBuilder();
            ab.addAllTokens(tokens);
            mConnection.sendTCPMessage(ab.build(), JumbleTCPMessageType.Authenticate);
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
            mConnection.sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
        }

        @Override
        public void setSelfMuteDeafState(boolean mute, boolean deaf) throws RemoteException {
            Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
            usb.setSelfMute(mute);
            usb.setSelfDeaf(deaf);
            if(!mute && (mTransmitMode == Constants.TRANSMIT_CONTINUOUS || mTransmitMode == Constants.TRANSMIT_VOICE_ACTIVITY))
                mAudioInput.startRecording(); // Resume recording when unmuted for PTT.
            else
                mAudioInput.stopRecording(); // Stop recording when muted.
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
            mAutoReconnect = extras.getBoolean(EXTRAS_AUTO_RECONNECT, true);
            mAutoReconnectDelay = extras.getInt(EXTRAS_AUTO_RECONNECT_DELAY, 5000);
            mCertificate = extras.getByteArray(EXTRAS_CERTIFICATE);
            mCertificatePassword = extras.getString(EXTRAS_CERTIFICATE_PASSWORD);
            mDetectionThreshold = extras.getFloat(EXTRAS_DETECTION_THRESHOLD, 0.5f);
            mAmplitudeBoost = extras.getFloat(EXTRAS_AMPLITUDE_BOOST, 1.0f);
            mTransmitMode = extras.getInt(EXTRAS_TRANSMIT_MODE, Constants.TRANSMIT_VOICE_ACTIVITY);
            mInputRate = extras.getInt(EXTRAS_INPUT_RATE, Audio.SAMPLE_RATE);
            mInputQuality = extras.getInt(EXTRAS_INPUT_QUALITY, 40000);
            mUseOpus = extras.getBoolean(EXTRAS_USE_OPUS, true);
            mUseTor = extras.getBoolean(EXTRAS_USE_TOR, false);
            mForceTcp = extras.getBoolean(EXTRAS_FORCE_TCP, false) || mUseTor; // Tor requires TCP connections to work- if it's on, force TCP.
            mClientName = extras.containsKey(EXTRAS_CLIENT_NAME) ? extras.getString(EXTRAS_CLIENT_NAME) : "Jumble";
            mAccessTokens = extras.getStringArrayList(EXTRAS_ACCESS_TOKENS);
            mAudioSource = extras.getInt(EXTRAS_AUDIO_SOURCE, MediaRecorder.AudioSource.MIC);
            mAudioStream = extras.getInt(EXTRAS_AUDIO_STREAM, AudioManager.STREAM_MUSIC);
            mFramesPerPacket = extras.getInt(EXTRAS_FRAMES_PER_PACKET, 2);
            connect();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jumble");

        mConnection = new JumbleConnection(this);
        mChannelHandler = new ChannelHandler(this);
        mUserHandler = new UserHandler(this);
        mTextMessageHandler = new TextMessageHandler(this);
        mAudioOutput = new AudioOutput(this);
        mAudioInput = new AudioInput(this, mAudioInputListener);
        mConnection.addTCPMessageHandlers(mChannelHandler, mUserHandler, mTextMessageHandler, mAudioOutput, mAudioInput);
        mConnection.addUDPMessageHandlers(mAudioOutput);
    }

    @Override
    public void onDestroy() {
        mObservers.kill();
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
            mPermissions = 0;
            mReconnecting = false;

            mConnection.connect(mServer.getHost(), mServer.getPort(), mForceTcp, mUseTor, mCertificate, mCertificatePassword);
        } catch (final JumbleConnectionException e) {
            e.printStackTrace();

            notifyObservers(new ObserverRunnable() {
                @Override
                public void run(IJumbleObserver observer) throws RemoteException {
                    observer.onConnectionError(e.getMessage(), e.isAutoReconnectAllowed());
                }
            });
        }
    }

    public void disconnect() {
        mConnection.disconnect();
    }

    public boolean isConnected() {
        return mConnection.isConnected();
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

        mConnection.sendTCPMessage(version.build(), JumbleTCPMessageType.Version);
        mConnection.sendTCPMessage(auth.build(), JumbleTCPMessageType.Authenticate);
    }

    @Override
    public void onConnectionSynchronized() {
        Log.v(Constants.TAG, "Connected");
        mWakeLock.acquire();

        // Send access tokens
        Mumble.Authenticate.Builder ab = Mumble.Authenticate.newBuilder();
        ab.addAllTokens(mAccessTokens);
        mConnection.sendTCPMessage(ab.build(), JumbleTCPMessageType.Authenticate);

        mAudioOutput.startPlaying(false);

        // This sticky broadcast will initialize the audio output.
        registerReceiver(mBluetoothReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));
        if(mTransmitMode == Constants.TRANSMIT_CONTINUOUS || mTransmitMode == Constants.TRANSMIT_VOICE_ACTIVITY)
            mAudioInput.startRecording();

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
        if(mWakeLock.isHeld()) mWakeLock.release();

        try {
            unregisterReceiver(mBluetoothReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        mUserHandler.clear();
        mChannelHandler.clear();
        mAudioOutput.stopPlaying();
        mAudioInput.shutdown();
        mMessageLog.clear();

        // Restore audio manager mode
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.stopBluetoothSco();

        notifyObservers(new ObserverRunnable() {
            @Override
            public void run(IJumbleObserver observer) throws RemoteException {
                observer.onDisconnected();
            }
        });
    }

    @Override
    public void onConnectionError(final JumbleConnectionException e) {
        Log.e(Constants.TAG, "Connection error: "+e.getMessage()+", should reconnect: " + e.isAutoReconnectAllowed());
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
        notifyObservers(new ObserverRunnable() {
            @Override
            public void run(IJumbleObserver observer) throws RemoteException {
                observer.onConnectionError(e.getMessage(), mReconnecting);
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

    public boolean shouldAutoReconnect() {
        return mAutoReconnect;
    }

    public int getAutoReconnectDelay() {
        return mAutoReconnectDelay;
    }

    public byte[] getCertificate() {
        return mCertificate;
    }

    public String getCertificatePassword() {
        return mCertificatePassword;
    }

    public float getDetectionThreshold() {
        return mDetectionThreshold;
    }

    public float getAmplitudeBoost() {
        return mAmplitudeBoost;
    }

    public int getTransmitMode() {
        return mTransmitMode;
    }

    public boolean shouldUseOpus() {
        return mUseOpus;
    }

    public int getInputRate() {
        return mInputRate;
    }

    public int getInputQuality() {
        return mInputQuality;
    }

    public boolean shouldForceTcp() {
        return mForceTcp;
    }

    public boolean shouldUseTor() {
        return mUseTor;
    }

    public String getClientName() {
        return mClientName;
    }

    public List<String> getAccessTokens() {
        return mAccessTokens;
    }

    public int getAudioSource() {
        return mAudioSource;
    }

    public int getAudioStream() {
        return mAudioStream;
    }

    public int getFramesPerPacket() {
        return mFramesPerPacket;
    }

    public Server getServer() {
        return mServer;
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
     * @param warning An HTML warning string to be messaged to the client.
     */
    public void logWarning(final String warning) {
        final Message message = new Message(Message.Type.WARNING, warning);
        logMessage(message);
    }

    /**
     * Logs an info message to the client.
     * @param info An HTML info string to be messaged to the client.
     */
    public void logInfo(final String info) {
        if(!mConnection.isSynchronized())
            return; // Don't log messages while synchronizing.

        final Message message = new Message(Message.Type.INFO, info);
        logMessage(message);
    }

    /**
     * Logs a message to the client.
     * @param message A message to log to the client.
     */
    public void logMessage(final Message message) {
        mMessageLog.add(message);
        notifyObservers(new ObserverRunnable() {
            @Override
            public void run(IJumbleObserver observer) throws RemoteException {
                observer.onMessageLogged(message);
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
