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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.morlunk.jumble.audio.AudioOutput;
import com.morlunk.jumble.audio.BluetoothScoReceiver;
import com.morlunk.jumble.exception.AudioException;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.TalkState;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleConnection;
import com.morlunk.jumble.util.IJumbleObserver;
import com.morlunk.jumble.util.JumbleException;
import com.morlunk.jumble.net.JumbleTCPMessageType;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.protocol.AudioHandler;
import com.morlunk.jumble.protocol.ModelHandler;
import com.morlunk.jumble.util.JumbleCallbacks;
import com.morlunk.jumble.util.JumbleLogger;

import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.List;

public class JumbleService extends Service implements JumbleConnection.JumbleConnectionListener, JumbleLogger, BluetoothScoReceiver.Listener {

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

    private PowerManager.WakeLock mWakeLock;
    private Handler mHandler;
    private JumbleCallbacks mCallbacks;

    private JumbleConnection mConnection;
    private ConnectionState mConnectionState;
    private ModelHandler mModelHandler;
    private AudioHandler mAudioHandler;
    private BluetoothScoReceiver mBluetoothReceiver;

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
        public void onTalkStateChange(final TalkState state) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(!isConnectionEstablished()) return;
                    final User currentUser = mModelHandler.getUser(mConnection.getSession());
                    if(currentUser == null) return;

                    currentUser.setTalkState(state);
                    mCallbacks.onUserTalkStateUpdated(currentUser);
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
            return mModelHandler.getUser(session);
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
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mBluetoothReceiver);
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) {
        return new JumbleBinder(this);
    }

    private void connect() {
        try {
            setReconnecting(false);
            mConnectionState = ConnectionState.DISCONNECTED;

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
        mConnection.disconnect();
    }

    public boolean isConnectionEstablished() {
        return mConnection != null && mConnection.isConnected();
    }

    /**
     * @return true if Jumble has received the ServerSync message, indicating synchronization with
     * the server's model and settings. This is the main state of the service.
     */
    public boolean isSynchronized() {
        return mConnectionState == ConnectionState.CONNECTED;
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
    }

    @Override
    public void onConnectionSynchronized() {
        mConnectionState = ConnectionState.CONNECTED;

        Log.v(Constants.TAG, "Connected");
        mWakeLock.acquire();

        try {
            mAudioHandler = mAudioBuilder.initialize(
                    mModelHandler.getUser(mConnection.getSession()),
                    mConnection.getMaxBandwidth(), mConnection.getCodec());
            mConnection.addTCPMessageHandlers(mAudioHandler);
            mConnection.addUDPMessageHandlers(mAudioHandler);
        } catch (AudioException e) {
            e.printStackTrace();
            onConnectionWarning(e.getMessage());
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

        mAudioHandler = mAudioBuilder.initialize(
                mModelHandler.getUser(mConnection.getSession()),
                mConnection.getMaxBandwidth(), mConnection.getCodec());
        mConnection.addTCPMessageHandlers(mAudioHandler);
        mConnection.addUDPMessageHandlers(mAudioHandler);
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
            mAudioBuilder.setVADThreshold(extras.getFloat(EXTRAS_DETECTION_THRESHOLD));
        }
        if (extras.containsKey(EXTRAS_AMPLITUDE_BOOST)) {
            mAudioBuilder.setAmplitudeBoost(extras.getFloat(EXTRAS_AMPLITUDE_BOOST));
        }
        if (extras.containsKey(EXTRAS_TRANSMIT_MODE)) {
            mAudioBuilder.setTransmitMode(extras.getInt(EXTRAS_TRANSMIT_MODE));
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
            mAudioBuilder.setHalfDuplexEnabled(extras.getBoolean(EXTRAS_HALF_DUPLEX));
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
     * @return The active {@link JumbleConnection}, or null if a connection has not been
     *         established yet.
     */
    public JumbleConnection getConnection() {
        return mConnection;
    }

    /**
     * Returnes the current {@link AudioHandler}. An AudioHandler is instantiated upon connection
     * to a server, and destroyed upon disconnection.
     * @return the active AudioHandler, or null if there is no active connection.
     */
    public AudioHandler getAudioHandler() {
        if (!isSynchronized())
            throw new IllegalStateException("Not synchronized");
        if (mAudioHandler == null && mConnectionState == ConnectionState.CONNECTED)
            throw new RuntimeException("Audio handler should always be instantiated while connected!");
        return mAudioHandler;
    }

    /**
     * Returns the current {@link ModelHandler}, containing the channel tree. A model handler is
     * valid for the lifetime of a connection.
     * @return the active ModelHandler, or null if there is no active connection.
     */
    public ModelHandler getModelHandler() {
        if (!isSynchronized())
            throw new IllegalStateException("Not synchronized");
        if (mModelHandler == null && mConnectionState == ConnectionState.CONNECTED)
            throw new RuntimeException("Model handler should always be instantiated while connected!");
        return mModelHandler;
    }

    /**
     * Returns the bluetooth service provider, established after synchronization.
     * @return The {@link BluetoothScoReceiver} attached to this service.
     * @throws IllegalStateException if not synchronized or disconnected.
     */
    public BluetoothScoReceiver getBluetoothReceiver() {
        if (!isSynchronized())
            throw new IllegalStateException("Not synchronized");
        return mBluetoothReceiver;
    }

    public boolean isReconnecting() {
        return mReconnecting;
    }

    public ConnectionState getConnectionState() {
        return mConnectionState;
    }

    public Server getConnectedServer() {
        return mServer;
    }

    public void registerObserver(IJumbleObserver observer) {
        mCallbacks.registerObserver(observer);
    }

    public void unregisterObserver(IJumbleObserver observer) {
        mCallbacks.unregisterObserver(observer);
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
         * @see JumbleBinder#isReconnecting()
         * @see JumbleBinder#cancelReconnect()
         */
        CONNECTION_LOST
    }
}
