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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.morlunk.jumble.audio.AudioOutput;
import com.morlunk.jumble.audio.BluetoothScoReceiver;
import com.morlunk.jumble.audio.inputmode.ActivityInputMode;
import com.morlunk.jumble.audio.inputmode.ContinuousInputMode;
import com.morlunk.jumble.audio.inputmode.IInputMode;
import com.morlunk.jumble.audio.inputmode.ToggleInputMode;
import com.morlunk.jumble.audio.javacpp.CELT7;
import com.morlunk.jumble.exception.AudioException;
import com.morlunk.jumble.exception.NotConnectedException;
import com.morlunk.jumble.exception.NotSynchronizedException;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.IChannel;
import com.morlunk.jumble.model.IUser;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.TalkState;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.model.WhisperTarget;
import com.morlunk.jumble.model.WhisperTargetList;
import com.morlunk.jumble.net.JumbleConnection;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.util.IJumbleObserver;
import com.morlunk.jumble.util.JumbleDisconnectedException;
import com.morlunk.jumble.util.JumbleException;
import com.morlunk.jumble.net.JumbleTCPMessageType;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.protocol.AudioHandler;
import com.morlunk.jumble.protocol.ModelHandler;
import com.morlunk.jumble.util.JumbleCallbacks;
import com.morlunk.jumble.util.JumbleLogger;
import com.morlunk.jumble.util.VoiceTargetMode;

import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class JumbleService extends Service implements IJumbleService, IJumbleSession, JumbleConnection.JumbleConnectionListener, JumbleLogger, BluetoothScoReceiver.Listener {

    static {
        // Use Spongy Castle for crypto implementation so we can create and manage PKCS #12 (.p12) certificates.
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    /**
     * An action to immediately connect to a given Mumble server.
     * Requires that {@link #EXTRAS_SERVER} is provided.
     */
    public static final String ACTION_CONNECT = "com.morlunk.jumble.CONNECT";

    /** A {@link Server} specifying the server to connect to. */
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

    // Service settings
    private Server mServer;
    private boolean mAutoReconnect;
    private int mAutoReconnectDelay;
    private byte[] mCertificate;
    private String mCertificatePassword;
    private boolean mUseOpus;
    private boolean mForceTcp;
    private boolean mUseTor;
    private String mClientName;
    private List<String> mAccessTokens;
    private String mTrustStore;
    private String mTrustStorePassword;
    private String mTrustStoreFormat;
    private List<Integer> mLocalMuteHistory;
    private List<Integer> mLocalIgnoreHistory;
    private AudioHandler.Builder mAudioBuilder;
    private int mTransmitMode;

    private byte mVoiceTargetId;
    private WhisperTargetList mWhisperTargetList;

    private PowerManager.WakeLock mWakeLock;
    private Handler mHandler;
    private JumbleCallbacks mCallbacks;

    private JumbleConnection mConnection;
    private ConnectionState mConnectionState;
    private ModelHandler mModelHandler;
    private AudioHandler mAudioHandler;
    private BluetoothScoReceiver mBluetoothReceiver;

    private ActivityInputMode mActivityInputMode;
    private ToggleInputMode mToggleInputMode;
    private ContinuousInputMode mContinuousInputMode;

    private boolean mReconnecting;

    /**
     * Listen for connectivity changes in the reconnection state, and reconnect accordingly.
     */
    private BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mReconnecting) {
                unregisterReceiver(this);
                return;
            }

            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected()) {
                Log.v(Constants.TAG, "Connectivity restored, attempting reconnect.");
                connect();
            }
        }
    };

    private AudioHandler.AudioEncodeListener mAudioInputListener =
            new AudioHandler.AudioEncodeListener() {
                @Override
                public void onAudioEncoded(byte[] data, int length) {
                    if(mConnection != null && mConnection.isSynchronized()) {
                        mConnection.sendUDPMessage(data, length, false);
                    }
                }

                @Override
                public void onTalkingStateChanged(final boolean talking) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // If the server session is inactive, ignore this message.
                                // It's likely that this is leftover from a terminated connection.
                                if (!isSynchronized())
                                    return;

                                final User currentUser = mModelHandler.getUser(mConnection.getSession());
                                if (currentUser == null) return;

                                currentUser.setTalkState(talking ? TalkState.TALKING : TalkState.PASSIVE);
                                mCallbacks.onUserTalkStateUpdated(currentUser);
                            } catch (NotSynchronizedException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            };

    private AudioOutput.AudioOutputListener mAudioOutputListener = new AudioOutput.AudioOutputListener() {
        @Override
        public void onUserTalkStateUpdated(final User user) {
            mCallbacks.onUserTalkStateUpdated(user);
        }

        @Override
        public User getUser(int session) {
            if (mModelHandler != null) {
                return mModelHandler.getUser(session);
            }
            return null;
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                try {
                    configureExtras(extras);
                } catch (AudioException e) {
                    throw new RuntimeException("Attempted to initialize audio in onStartCommand erroneously.");
                }
            }

            if (ACTION_CONNECT.equals(intent.getAction())) {
                if (extras == null || !extras.containsKey(EXTRAS_SERVER)) {
                    // Ensure that we have been provided all required attributes.```
                    throw new RuntimeException(ACTION_CONNECT + " requires a server provided in extras.");
                }
                connect();
            }
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
        mAudioBuilder = new AudioHandler.Builder()
                .setContext(this)
                .setLogger(this)
                .setEncodeListener(mAudioInputListener)
                .setTalkingListener(mAudioOutputListener);
        mConnectionState = ConnectionState.DISCONNECTED;
        mBluetoothReceiver = new BluetoothScoReceiver(this, this);
        registerReceiver(mBluetoothReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));
        mToggleInputMode = new ToggleInputMode();
        mActivityInputMode = new ActivityInputMode(0); // FIXME: reasonable default
        mContinuousInputMode = new ContinuousInputMode();
        mWhisperTargetList = new WhisperTargetList();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mBluetoothReceiver);
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) {
        return new JumbleBinder(this);
    }

    protected void connect() {
        try {
            setReconnecting(false);
            mConnectionState = ConnectionState.DISCONNECTED;
            mVoiceTargetId = 0;
            mWhisperTargetList.clear();

            mConnection = new JumbleConnection(this);
            mConnection.setForceTCP(mForceTcp);
            mConnection.setUseTor(mUseTor);
            mConnection.setKeys(mCertificate, mCertificatePassword);
            mConnection.setTrustStore(mTrustStore, mTrustStorePassword, mTrustStoreFormat);

            mModelHandler = new ModelHandler(this, mCallbacks, this,
                    mLocalMuteHistory, mLocalIgnoreHistory);
            mConnection.addTCPMessageHandlers(mModelHandler);

            mConnectionState = ConnectionState.CONNECTING;

            mCallbacks.onConnecting();

            mConnection.connect(mServer.getHost(), mServer.getPort());
        } catch (JumbleException e) {
            e.printStackTrace();
            mCallbacks.onDisconnected(e);
        }
    }

    public void disconnect() {
        if (mConnection != null) {
            mConnection.disconnect();
        }
    }

    public boolean isConnectionEstablished() {
        return mConnection != null && mConnection.isConnected();
    }

    /**
     * @return true if Jumble has received the ServerSync message, indicating synchronization with
     * the server's model and settings. This is the main state of the service.
     */
    public boolean isSynchronized() {
        return mConnection != null && mConnection.isSynchronized();
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
        auth.addCeltVersions(CELT7.getBitstreamVersion());
        // FIXME: resolve issues with CELT 11 robot voices.
//            auth.addCeltVersions(Constants.CELT_11_VERSION);
        auth.setOpus(mUseOpus);
        auth.addAllTokens(mAccessTokens);

        mConnection.sendTCPMessage(version.build(), JumbleTCPMessageType.Version);
        mConnection.sendTCPMessage(auth.build(), JumbleTCPMessageType.Authenticate);
    }

    @Override
    public void onConnectionSynchronized() {
        mConnectionState = ConnectionState.CONNECTED;

        Log.v(Constants.TAG, "Connected");
        mWakeLock.acquire();

        try {
            mAudioHandler = mAudioBuilder.initialize(
                    mModelHandler.getUser(mConnection.getSession()),
                    mConnection.getMaxBandwidth(), mConnection.getCodec(),
                    mVoiceTargetId);
            mConnection.addTCPMessageHandlers(mAudioHandler);
            mConnection.addUDPMessageHandlers(mAudioHandler);
        } catch (AudioException e) {
            e.printStackTrace();
            onConnectionWarning(e.getMessage());
        } catch (NotSynchronizedException e) {
            throw new RuntimeException("Connection should be synchronized in callback for synchronization!", e);
        }

        mCallbacks.onConnected();
    }

    @Override
    public void onConnectionHandshakeFailed(X509Certificate[] chain) {
        mCallbacks.onTLSHandshakeFailed(chain);
    }

    @Override
    public void onConnectionDisconnected(JumbleException e) {
        if (e != null) {
            Log.e(Constants.TAG, "Error: " + e.getMessage() +
                    " (reason: " + e.getReason().name() + ")");
            mConnectionState = ConnectionState.CONNECTION_LOST;

            setReconnecting(mAutoReconnect
                    && e.getReason() == JumbleException.JumbleDisconnectReason.CONNECTION_ERROR);
        } else {
            Log.v(Constants.TAG, "Disconnected");
            mConnectionState = ConnectionState.DISCONNECTED;
        }

        if(mWakeLock.isHeld()) {
            mWakeLock.release();
        }

        if (mAudioHandler != null) {
            mAudioHandler.shutdown();
        }

        mModelHandler = null;
        mAudioHandler = null;
        mVoiceTargetId = 0;
        mWhisperTargetList.clear();

        // Halt SCO connection on shutdown.
        mBluetoothReceiver.stopBluetoothSco();

        mCallbacks.onDisconnected(e);
    }

    @Override
    public void onConnectionWarning(String warning) {
        logWarning(warning);
    }

    @Override
    public void logInfo(String message) {
        if (mConnection == null || !mConnection.isSynchronized())
            return; // don't log info prior to synchronization
        mCallbacks.onLogInfo(message);
    }

    @Override
    public void logWarning(String message) {
        mCallbacks.onLogWarning(message);
    }

    @Override
    public void logError(String message) {
        mCallbacks.onLogError(message);
    }

    public void setReconnecting(boolean reconnecting) {
        if (mReconnecting == reconnecting)
            return;

        mReconnecting = reconnecting;
        if (reconnecting) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info != null && info.isConnected()) {
                Log.v(Constants.TAG, "Connection lost due to non-connectivity issue. Start reconnect polling.");
                Handler mainHandler = new Handler();
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mReconnecting) connect();
                    }
                }, mAutoReconnectDelay);
            } else {
                // In the event that we've lost connectivity, don't poll. Wait until network
                // returns before we resume connection attempts.
                Log.v(Constants.TAG, "Connection lost due to connectivity issue. Waiting until network returns.");
                try {
                    registerReceiver(mConnectivityReceiver,
                            new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
        } else {
            try {
                unregisterReceiver(mConnectivityReceiver);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Instantiates an audio handler with the current service settings, destroying any previous
     * handler. Requires synchronization with the server, as the maximum bandwidth and session must
     * be known.
     */
    private void createAudioHandler() throws AudioException {
        if (BuildConfig.DEBUG && mConnectionState != ConnectionState.CONNECTED) {
            throw new AssertionError("Attempted to instantiate audio handler when not connected!");
        }

        if (mAudioHandler != null) {
            mConnection.removeTCPMessageHandler(mAudioHandler);
            mConnection.removeUDPMessageHandler(mAudioHandler);
            mAudioHandler.shutdown();
        }

        try {
            mAudioHandler = mAudioBuilder.initialize(
                    mModelHandler.getUser(mConnection.getSession()),
                    mConnection.getMaxBandwidth(), mConnection.getCodec(),
                    mVoiceTargetId);
            mConnection.addTCPMessageHandlers(mAudioHandler);
            mConnection.addUDPMessageHandlers(mAudioHandler);
        } catch (NotSynchronizedException e) {
            throw new RuntimeException("Attempted to create audio handler when not synchronized!");
        }
    }

    /**
     * Loads all defined settings from the given bundle into the JumbleService.
     * Some settings may only take effect after a reconnect.
     * @param extras A bundle with settings.
     * @return true if a reconnect is required for changes to take effect.
     * @see com.morlunk.jumble.JumbleService
     */
    public boolean configureExtras(Bundle extras) throws AudioException {
        boolean reconnectNeeded = false;
        if (extras.containsKey(EXTRAS_SERVER)) {
            mServer = extras.getParcelable(EXTRAS_SERVER);
            reconnectNeeded = true;
        }
        if (extras.containsKey(EXTRAS_AUTO_RECONNECT)) {
            mAutoReconnect = extras.getBoolean(EXTRAS_AUTO_RECONNECT);
        }
        if (extras.containsKey(EXTRAS_AUTO_RECONNECT_DELAY)) {
            mAutoReconnectDelay = extras.getInt(EXTRAS_AUTO_RECONNECT_DELAY);
        }
        if (extras.containsKey(EXTRAS_CERTIFICATE)) {
            mCertificate = extras.getByteArray(EXTRAS_CERTIFICATE);
            reconnectNeeded = true;
        }
        if (extras.containsKey(EXTRAS_CERTIFICATE_PASSWORD)) {
            mCertificatePassword = extras.getString(EXTRAS_CERTIFICATE_PASSWORD);
            reconnectNeeded = true;
        }
        if (extras.containsKey(EXTRAS_DETECTION_THRESHOLD)) {
            mActivityInputMode.setThreshold(extras.getFloat(EXTRAS_DETECTION_THRESHOLD));
        }
        if (extras.containsKey(EXTRAS_AMPLITUDE_BOOST)) {
            mAudioBuilder.setAmplitudeBoost(extras.getFloat(EXTRAS_AMPLITUDE_BOOST));
        }
        if (extras.containsKey(EXTRAS_TRANSMIT_MODE)) {
            mTransmitMode = extras.getInt(EXTRAS_TRANSMIT_MODE);
            IInputMode inputMode;
            switch (mTransmitMode) {
                case Constants.TRANSMIT_PUSH_TO_TALK:
                    inputMode = mToggleInputMode;
                    break;
                case Constants.TRANSMIT_CONTINUOUS:
                    inputMode = mContinuousInputMode;
                    break;
                case Constants.TRANSMIT_VOICE_ACTIVITY:
                    inputMode = mActivityInputMode;
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            mAudioBuilder.setInputMode(inputMode);
        }
        if (extras.containsKey(EXTRAS_INPUT_RATE)) {
            mAudioBuilder.setInputSampleRate(extras.getInt(EXTRAS_INPUT_RATE));
        }
        if (extras.containsKey(EXTRAS_INPUT_QUALITY)) {
            mAudioBuilder.setTargetBitrate(extras.getInt(EXTRAS_INPUT_QUALITY));
        }
        if (extras.containsKey(EXTRAS_USE_OPUS)) {
            mUseOpus = extras.getBoolean(EXTRAS_USE_OPUS);
            reconnectNeeded = true;
        }
        if (extras.containsKey(EXTRAS_USE_TOR)) {
            mUseTor = extras.getBoolean(EXTRAS_USE_TOR);
            mForceTcp |= mUseTor; // Tor requires TCP connections to work- if it's on, force TCP.
            reconnectNeeded = true;
        }
        if (extras.containsKey(EXTRAS_FORCE_TCP)) {
            mForceTcp |= extras.getBoolean(EXTRAS_FORCE_TCP);
            reconnectNeeded = true;
        }
        if (extras.containsKey(EXTRAS_CLIENT_NAME)) {
            mClientName = extras.getString(EXTRAS_CLIENT_NAME);
            reconnectNeeded = true;
        }
        if (extras.containsKey(EXTRAS_ACCESS_TOKENS)) {
            mAccessTokens = extras.getStringArrayList(EXTRAS_ACCESS_TOKENS);
            if (mConnection != null && mConnection.isConnected()) {
                mConnection.sendAccessTokens(mAccessTokens);
            }
        }
        if (extras.containsKey(EXTRAS_AUDIO_SOURCE)) {
            mAudioBuilder.setAudioSource(extras.getInt(EXTRAS_AUDIO_SOURCE));
        }
        if (extras.containsKey(EXTRAS_AUDIO_STREAM)) {
            mAudioBuilder.setAudioStream(extras.getInt(EXTRAS_AUDIO_STREAM));
        }
        if (extras.containsKey(EXTRAS_FRAMES_PER_PACKET)) {
            mAudioBuilder.setTargetFramesPerPacket(extras.getInt(EXTRAS_FRAMES_PER_PACKET));
        }
        if (extras.containsKey(EXTRAS_TRUST_STORE)) {
            mTrustStore = extras.getString(EXTRAS_TRUST_STORE);
            reconnectNeeded = true;
        }
        if (extras.containsKey(EXTRAS_TRUST_STORE_PASSWORD)) {
            mTrustStorePassword = extras.getString(EXTRAS_TRUST_STORE_PASSWORD);
            reconnectNeeded = true;
        }
        if (extras.containsKey(EXTRAS_TRUST_STORE_FORMAT)) {
            mTrustStoreFormat = extras.getString(EXTRAS_TRUST_STORE_FORMAT);
            reconnectNeeded = true;
        }
        if (extras.containsKey(EXTRAS_HALF_DUPLEX)) {
            mAudioBuilder.setHalfDuplexEnabled(
                    extras.getInt(EXTRAS_TRANSMIT_MODE) == Constants.TRANSMIT_PUSH_TO_TALK
                            && extras.getBoolean(EXTRAS_HALF_DUPLEX));
        }
        if (extras.containsKey(EXTRAS_LOCAL_MUTE_HISTORY)) {
            mLocalMuteHistory = extras.getIntegerArrayList(EXTRAS_LOCAL_MUTE_HISTORY);
            reconnectNeeded = true;
        }
        if (extras.containsKey(EXTRAS_LOCAL_IGNORE_HISTORY)) {
            mLocalIgnoreHistory = extras.getIntegerArrayList(EXTRAS_LOCAL_IGNORE_HISTORY);
            reconnectNeeded = true;
        }
        if (extras.containsKey(EXTRAS_ENABLE_PREPROCESSOR)) {
            mAudioBuilder.setPreprocessorEnabled(extras.getBoolean(EXTRAS_ENABLE_PREPROCESSOR));
        }

        // Reload audio subsystem if initialized
        if (mAudioHandler != null && mAudioHandler.isInitialized()) {
            createAudioHandler();
            Log.i(Constants.TAG, "Audio subsystem reloaded after settings change.");
        }
        return reconnectNeeded;
    }

    @Override
    public void onBluetoothScoConnected() {
        // After an SCO connection is established, audio is rerouted to be compatible with SCO.
        mAudioBuilder.setBluetoothEnabled(true);
        if (mAudioHandler != null) {
            try {
                createAudioHandler();
            } catch (AudioException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onBluetoothScoDisconnected() {
        // Restore audio settings after disconnection.
        mAudioBuilder.setBluetoothEnabled(false);
        if (mAudioHandler != null) {
            try {
                createAudioHandler();
            } catch (AudioException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Exposes the current connection. The current connection is set once an attempt to connect to
     * a server is made, and remains set until a subsequent connection. It remains available
     * after disconnection to provide information regarding the terminated connection.
     * @return The active {@link JumbleConnection}.
     */
    public JumbleConnection getConnection() {
        return mConnection;
    }

    /**
     * Returnes the current {@link AudioHandler}. An AudioHandler is instantiated upon connection
     * to a server, and destroyed upon disconnection.
     * @return the active AudioHandler, or null if there is no active connection.
     */
    private AudioHandler getAudioHandler() throws NotSynchronizedException {
        if (!isSynchronized())
            throw new NotSynchronizedException();
        if (mAudioHandler == null && mConnectionState == ConnectionState.CONNECTED)
            throw new RuntimeException("Audio handler should always be instantiated while connected!");
        return mAudioHandler;
    }

    /**
     * Returns the current {@link ModelHandler}, containing the channel tree. A model handler is
     * valid for the lifetime of a connection.
     * @return the active ModelHandler, or null if there is no active connection.
     */
    private ModelHandler getModelHandler() throws NotSynchronizedException {
        if (!isSynchronized())
            throw new NotSynchronizedException();
        if (mModelHandler == null && mConnectionState == ConnectionState.CONNECTED)
            throw new RuntimeException("Model handler should always be instantiated while connected!");
        return mModelHandler;
    }

    /**
     * Returns the bluetooth service provider, established after synchronization.
     * @return The {@link BluetoothScoReceiver} attached to this service.
     */
    private BluetoothScoReceiver getBluetoothReceiver() throws NotSynchronizedException {
        if (!isSynchronized())
            throw new NotSynchronizedException();
        return mBluetoothReceiver;
    }

    @Override
    public JumbleService.ConnectionState getConnectionState() {
        return mConnectionState;
    }

    @Override
    public JumbleException getConnectionError() {
        JumbleConnection connection = getConnection();
        return connection != null ? connection.getError() : null;
    }

    @Override
    public boolean isReconnecting() {
        return mReconnecting;
    }

    @Override
    public void cancelReconnect() {
        setReconnecting(false);
    }

    @Override
    public Server getTargetServer() {
        return mServer;
    }

    @Override
    public IJumbleSession getSession() throws JumbleDisconnectedException {
        if (mConnectionState != ConnectionState.CONNECTED)
            throw new JumbleDisconnectedException();
        return this;
    }

    @Override
    public long getTCPLatency() {
        try {
            return getConnection().getTCPLatency();
        } catch (NotConnectedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public long getUDPLatency() {
        try {
            return getConnection().getUDPLatency();
        } catch (NotConnectedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getMaxBandwidth() {
        try {
            return getConnection().getMaxBandwidth();
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getCurrentBandwidth() {
        try {
            return getAudioHandler().getCurrentBandwidth();
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getServerVersion() {
        try {
            return getConnection().getServerVersion();
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getServerRelease() {
        try {
            return getConnection().getServerRelease();
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getServerOSName() {
        try {
            return getConnection().getServerOSName();
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getServerOSVersion() {
        try {
            return getConnection().getServerOSVersion();
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getSessionId() {
        try {
            return getConnection().getSession();
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public IUser getSessionUser() {
        try {
            return getModelHandler().getUser(getSessionId());
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public IChannel getSessionChannel() {
        IUser user = getSessionUser();
        if (user != null)
            return user.getChannel();
        throw new IllegalStateException("Session user should be set post-synchronization!");
    }

    @Override
    public IUser getUser(int session) {
        try {
            return getModelHandler().getUser(session);
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public IChannel getChannel(int id) {
        try {
            return getModelHandler().getChannel(id);
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public IChannel getRootChannel() {
        return getChannel(0);
    }

    @Override
    public int getPermissions() {
        try {
            return getModelHandler().getPermissions();
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getTransmitMode() {
        return mTransmitMode;
    }

    @Override
    public JumbleUDPMessageType getCodec() {
        try {
            return getConnection().getCodec();
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean usingBluetoothSco() {
        try {
            return getBluetoothReceiver().isBluetoothScoOn();
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void enableBluetoothSco() {
        try {
            getBluetoothReceiver().startBluetoothSco();
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void disableBluetoothSco() {
        try {
            getBluetoothReceiver().stopBluetoothSco();
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean isTalking() {
        return mToggleInputMode.isTalkingOn();
    }

    @Override
    public void setTalkingState(boolean talking) {
        mToggleInputMode.setTalkingOn(talking);
    }

    @Override
    public void joinChannel(int channel) {
        moveUserToChannel(getSessionId(), channel);
    }

    @Override
    public void moveUserToChannel(int session, int channel) {
        Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
        usb.setSession(session);
        usb.setChannelId(channel);
        getConnection().sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
    }

    @Override
    public void createChannel(int parent, String name, String description, int position, boolean temporary) {
        Mumble.ChannelState.Builder csb = Mumble.ChannelState.newBuilder();
        csb.setParent(parent);
        csb.setName(name);
        csb.setDescription(description);
        csb.setPosition(position);
        csb.setTemporary(temporary);
        getConnection().sendTCPMessage(csb.build(), JumbleTCPMessageType.ChannelState);
    }

    @Override
    public void sendAccessTokens(final List<String> tokens) {
        getConnection().sendAccessTokens(tokens);
    }

    @Override
    public void requestBanList() {
        throw new UnsupportedOperationException("Not yet implemented"); // TODO
    }

    @Override
    public void requestUserList() {
        throw new UnsupportedOperationException("Not yet implemented"); // TODO
    }

    @Override
    public void requestPermissions(int channel) {
        Mumble.PermissionQuery.Builder pqb = Mumble.PermissionQuery.newBuilder();
        pqb.setChannelId(channel);
        getConnection().sendTCPMessage(pqb.build(), JumbleTCPMessageType.PermissionQuery);
    }

    @Override
    public void requestComment(int session) {
        Mumble.RequestBlob.Builder rbb = Mumble.RequestBlob.newBuilder();
        rbb.addSessionComment(session);
        getConnection().sendTCPMessage(rbb.build(), JumbleTCPMessageType.RequestBlob);
    }

    @Override
    public void requestAvatar(int session) {
        Mumble.RequestBlob.Builder rbb = Mumble.RequestBlob.newBuilder();
        rbb.addSessionTexture(session);
        getConnection().sendTCPMessage(rbb.build(), JumbleTCPMessageType.RequestBlob);
    }

    @Override
    public void requestChannelDescription(int channel) {
        Mumble.RequestBlob.Builder rbb = Mumble.RequestBlob.newBuilder();
        rbb.addChannelDescription(channel);
        getConnection().sendTCPMessage(rbb.build(), JumbleTCPMessageType.RequestBlob);
    }

    @Override
    public void registerUser(int session) {
        Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
        usb.setSession(session);
        usb.setUserId(0);
        getConnection().sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
    }

    @Override
    public void kickBanUser(int session, String reason, boolean ban) {
        Mumble.UserRemove.Builder urb = Mumble.UserRemove.newBuilder();
        urb.setSession(session);
        urb.setReason(reason);
        urb.setBan(ban);
        getConnection().sendTCPMessage(urb.build(), JumbleTCPMessageType.UserRemove);
    }

    @Override
    public Message sendUserTextMessage(int session, String message) {
        try {
            if (!isSynchronized())
                throw new NotSynchronizedException();

            Mumble.TextMessage.Builder tmb = Mumble.TextMessage.newBuilder();
            tmb.addSession(session);
            tmb.setMessage(message);
            getConnection().sendTCPMessage(tmb.build(), JumbleTCPMessageType.TextMessage);

            User self = getModelHandler().getUser(getSessionId());
            User user = getModelHandler().getUser(session);
            List<User> users = new ArrayList<User>(1);
            users.add(user);
            return new Message(getSessionId(), self.getName(), new ArrayList<Channel>(0), new ArrayList<Channel>(0), users, message);
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Message sendChannelTextMessage(int channel, String message, boolean tree) {
        try {
            if (!isSynchronized())
                throw new NotSynchronizedException();

            Mumble.TextMessage.Builder tmb = Mumble.TextMessage.newBuilder();
            if (tree) tmb.addTreeId(channel);
            else tmb.addChannelId(channel);
            tmb.setMessage(message);
            getConnection().sendTCPMessage(tmb.build(), JumbleTCPMessageType.TextMessage);

            User self = getModelHandler().getUser(getSessionId());
            Channel targetChannel = getModelHandler().getChannel(channel);
            List<Channel> targetChannels = new ArrayList<Channel>();
            targetChannels.add(targetChannel);
            return new Message(getSessionId(), self.getName(), targetChannels, tree ? targetChannels : new ArrayList<Channel>(0), new ArrayList<User>(0), message);
        } catch (NotSynchronizedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void setUserComment(int session, String comment) {
        Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
        usb.setSession(session);
        usb.setComment(comment);
        getConnection().sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
    }

    @Override
    public void setPrioritySpeaker(int session, boolean priority) {
        Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
        usb.setSession(session);
        usb.setPrioritySpeaker(priority);
        getConnection().sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
    }

    @Override
    public void removeChannel(int channel) {
        Mumble.ChannelRemove.Builder crb = Mumble.ChannelRemove.newBuilder();
        crb.setChannelId(channel);
        getConnection().sendTCPMessage(crb.build(), JumbleTCPMessageType.ChannelRemove);
    }

    @Override
    public void setMuteDeafState(int session, boolean mute, boolean deaf) {
        Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
        usb.setSession(session);
        usb.setMute(mute);
        usb.setDeaf(deaf);
        if (!mute) usb.setSuppress(false);
        getConnection().sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
    }

    @Override
    public void setSelfMuteDeafState(boolean mute, boolean deaf) {
        Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
        usb.setSelfMute(mute);
        usb.setSelfDeaf(deaf);
        getConnection().sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
    }

    public void registerObserver(IJumbleObserver observer) {
        mCallbacks.registerObserver(observer);
    }

    public void unregisterObserver(IJumbleObserver observer) {
        mCallbacks.unregisterObserver(observer);
    }

    @Override
    public boolean isConnected() {
        return mConnectionState == ConnectionState.CONNECTED;
    }

    @Override
    public void linkChannels(IChannel channelA, IChannel channelB) {
        Mumble.ChannelState.Builder csb = Mumble.ChannelState.newBuilder();
        csb.setChannelId(channelA.getId());
        csb.addLinksAdd(channelB.getId());
        getConnection().sendTCPMessage(csb.build(), JumbleTCPMessageType.ChannelState);
    }

    @Override
    public void unlinkChannels(IChannel channelA, IChannel channelB) {
        Mumble.ChannelState.Builder csb = Mumble.ChannelState.newBuilder();
        csb.setChannelId(channelA.getId());
        csb.addLinksRemove(channelB.getId());
        getConnection().sendTCPMessage(csb.build(), JumbleTCPMessageType.ChannelState);
    }

    @Override
    public void unlinkAllChannels(IChannel channel) {
        Mumble.ChannelState.Builder csb = Mumble.ChannelState.newBuilder();
        csb.setChannelId(channel.getId());
        for (IChannel linked : channel.getLinks()) {
            csb.addLinksRemove(linked.getId());
        }
        getConnection().sendTCPMessage(csb.build(), JumbleTCPMessageType.ChannelState);
    }

    @Override
    public byte registerWhisperTarget(final WhisperTarget target) {
        byte id = mWhisperTargetList.append(target);
        if (id < 0) {
            return -1;
        }

        Mumble.VoiceTarget.Target voiceTarget = target.createTarget();
        Mumble.VoiceTarget.Builder vtb = Mumble.VoiceTarget.newBuilder();
        vtb.setId(id);
        vtb.addTargets(voiceTarget);
        getConnection().sendTCPMessage(vtb.build(), JumbleTCPMessageType.VoiceTarget);
        return id;
    }

    @Override
    public void unregisterWhisperTarget(byte targetId) {
        mWhisperTargetList.free(targetId);
    }

    @Override
    public void setVoiceTargetId(byte targetId) {
        if ((targetId & ~0x1F) > 0) {
            throw new IllegalArgumentException("Target ID must be at most 5 bits.");
        }
        mVoiceTargetId = targetId;
        mAudioHandler.setVoiceTargetId(targetId);
        mCallbacks.onVoiceTargetChanged(VoiceTargetMode.fromId(targetId));
    }

    @Override
    public byte getVoiceTargetId() {
        return mVoiceTargetId;
    }

    @Override
    public VoiceTargetMode getVoiceTargetMode() {
        return VoiceTargetMode.fromId(mVoiceTargetId);
    }

    @Override
    public WhisperTarget getWhisperTarget() {
        if (VoiceTargetMode.fromId(mVoiceTargetId) == VoiceTargetMode.WHISPER) {
            return mWhisperTargetList.get(mVoiceTargetId);
        }
        return null;
    }

    /**
     * The current connection state of the service.
     */
    public enum ConnectionState {
        /**
         * The default state of Jumble, before connection to a server and after graceful/expected
         * disconnection from a server.
         */
        DISCONNECTED,
        /**
         * A connection to the server is currently in progress.
         */
        CONNECTING,
        /**
         * Jumble has received all data necessary for normal protocol communication with the server.
         */
        CONNECTED,
        /**
         * The connection was lost due to either a kick/ban or socket I/O error.
         * Jumble may be reconnecting in this state.
         * @see #isReconnecting()
         * @see #cancelReconnect()
         */
        CONNECTION_LOST
    }

    public static class JumbleBinder extends Binder {
        private final IJumbleService mService;

        private JumbleBinder(IJumbleService service) {
            mService = service;
        }

        public IJumbleService getService() {
            return mService;
        }
    }
}
