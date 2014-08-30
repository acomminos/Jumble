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

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.protocol.JumbleTCPMessageListener;
import com.morlunk.jumble.protocol.JumbleUDPMessageListener;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class JumbleConnection implements JumbleTCP.TCPConnectionListener, JumbleUDP.UDPConnectionListener {

    /**
     * Message types that aren't shown in logcat.
     * For annoying types like UDPTunnel.
     */
    public static final Set<JumbleTCPMessageType> UNLOGGED_MESSAGES;

    static {
        UNLOGGED_MESSAGES = new HashSet<JumbleTCPMessageType>();
        UNLOGGED_MESSAGES.add(JumbleTCPMessageType.UDPTunnel);
        UNLOGGED_MESSAGES.add(JumbleTCPMessageType.Ping);
    }
    private JumbleConnectionListener mListener;

    // Tor connection details
    public static final String TOR_HOST = "localhost";
    public static final int TOR_PORT = 9050;

    // Authentication
    private byte[] mCertificate;
    private String mCertificatePassword;
    private String mTrustStorePath;
    private String mTrustStorePassword;
    private String mTrustStoreFormat;

    // Threading
    private ScheduledExecutorService mPingExecutorService;
    private Handler mMainHandler;

    // Networking and protocols
    private JumbleTCP mTCP;
    private JumbleUDP mUDP;
    private ScheduledFuture mPingTask;
    private boolean mUsingUDP = true;
    private boolean mForceTCP;
    private boolean mUseTor;
    private boolean mConnected;
    private boolean mSynchronized;
    private boolean mExceptionHandled = false;
    private long mStartTimestamp; // Time that the connection was initiated in nanoseconds
    private final CryptState mCryptState = new CryptState();

    // Latency
    private long mLastUDPPing;
    private long mLastTCPPing;

    // Server
    private String mHost;
    private int mPort;
    private int mServerVersion;
    private String mServerRelease;
    private String mServerOSName;
    private String mServerOSVersion;
    private int mMaxBandwidth;
    private int mCodec;

    // Session
    private int mSession;

    // Message handlers
    private ConcurrentLinkedQueue<JumbleTCPMessageListener> mTCPHandlers = new ConcurrentLinkedQueue<JumbleTCPMessageListener>();
    private ConcurrentLinkedQueue<JumbleUDPMessageListener> mUDPHandlers = new ConcurrentLinkedQueue<JumbleUDPMessageListener>();

    /**
     * Handles packets received that are critical to the connection state.
     */
    private JumbleTCPMessageListener mConnectionMessageHandler = new JumbleTCPMessageListener.Stub() {

        @Override
        public void messageServerSync(Mumble.ServerSync msg) {
            // Protocol says we're supposed to send a dummy UDPTunnel packet here to let the server know we don't like UDP.
            if (shouldForceTCP()) {
                enableForceTCP();
            }

            // Start TCP/UDP ping thread. FIXME is this the right place?
            mPingTask = mPingExecutorService.scheduleAtFixedRate(mPingRunnable, 0, 5, TimeUnit.SECONDS);

            mSession = msg.getSession();
            mMaxBandwidth = msg.getMaxBandwidth();
            mSynchronized = true;

            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnectionSynchronized();
                }
            });

        }

        @Override
        public void messageCodecVersion(Mumble.CodecVersion msg) {
            if(msg.hasOpus() && msg.getOpus())
                mCodec = JumbleUDPMessageType.UDPVoiceOpus.ordinal();
            else if(msg.hasBeta() && !msg.getPreferAlpha())
                mCodec = JumbleUDPMessageType.UDPVoiceCELTBeta.ordinal();
            else
                mCodec = JumbleUDPMessageType.UDPVoiceCELTAlpha.ordinal();
        }

        @Override
        public void messageReject(final Mumble.Reject msg) {
            mConnected = false;
            handleFatalException(new JumbleException(msg));
        }

        @Override
        public void messageUserRemove(final Mumble.UserRemove msg) {
            if(msg.getSession() == mSession) {
                mConnected = false;
                handleFatalException(new JumbleException(msg));
            }
        }

        @Override
        public void messageCryptSetup(Mumble.CryptSetup msg) {
            try {
                if(msg.hasKey() && msg.hasClientNonce() && msg.hasServerNonce()) {
                    ByteString key = msg.getKey();
                    ByteString clientNonce = msg.getClientNonce();
                    ByteString serverNonce = msg.getServerNonce();

                    if(key.size() == CryptState.AES_BLOCK_SIZE &&
                            clientNonce.size() == CryptState.AES_BLOCK_SIZE &&
                            serverNonce.size() == CryptState.AES_BLOCK_SIZE)
                        mCryptState.setKeys(key.toByteArray(), clientNonce.toByteArray(), serverNonce.toByteArray());
                } else if(msg.hasServerNonce()) {
                    ByteString serverNonce = msg.getServerNonce();
                    if(serverNonce.size() == CryptState.AES_BLOCK_SIZE) {
                        mCryptState.mUiResync++;
                        mCryptState.mDecryptIV = serverNonce.toByteArray();
                    }
                } else {
                    Mumble.CryptSetup.Builder csb = Mumble.CryptSetup.newBuilder();
                    csb.setClientNonce(ByteString.copyFrom(mCryptState.mEncryptIV));
                    sendTCPMessage(csb.build(), JumbleTCPMessageType.CryptSetup);
                }
            } catch (InvalidKeyException e) {
                handleFatalException(new JumbleException("Received invalid cryptographic nonce from server", e, true));
            }
        }

        @Override
        public void messageVersion(Mumble.Version msg) {
            mServerVersion = msg.getVersion();
            mServerRelease = msg.getRelease();
            mServerOSName = msg.getOs();
            mServerOSVersion = msg.getOsVersion();
        }

        @Override
        public void messagePing(Mumble.Ping msg) {
            mCryptState.mUiRemoteGood = msg.getGood();
            mCryptState.mUiRemoteLate = msg.getLate();
            mCryptState.mUiRemoteLost = msg.getLost();
            mCryptState.mUiRemoteResync = msg.getResync();

            // In microseconds
            long elapsed = getElapsed();
            mLastTCPPing = elapsed-msg.getTimestamp();

            if(((mCryptState.mUiRemoteGood == 0) || (mCryptState.mUiGood == 0)) && mUsingUDP && elapsed > 20000000) {
                mUsingUDP = false;
                if(!shouldForceTCP() && mListener != null) {
                    if((mCryptState.mUiRemoteGood == 0) && (mCryptState.mUiGood == 0))
                        mListener.onConnectionWarning("UDP packets cannot be sent to or received from the server. Switching to TCP mode.");
                    else if(mCryptState.mUiRemoteGood == 0)
                        mListener.onConnectionWarning("UDP packets cannot be sent to the server. Switching to TCP mode.");
                    else
                        mListener.onConnectionWarning("UDP packets cannot be received from the server. Switching to TCP mode.");
                }
            } else if (!mUsingUDP && (mCryptState.mUiRemoteGood > 3) && (mCryptState.mUiGood > 3)) {
                mUsingUDP = true;
                if (!shouldForceTCP() && mListener != null)
                    mListener.onConnectionWarning("UDP packets can be sent to and received from the server. Switching back to UDP mode.");
            }
        }
    };

    private JumbleUDPMessageListener mUDPPingListener = new JumbleUDPMessageListener.Stub() {

        @Override
        public void messageUDPPing(byte[] data) {
//            Log.v(Constants.TAG, "IN: UDP Ping");
            byte[] timedata = new byte[8];
            System.arraycopy(data, 1, timedata, 0, 8);
            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.put(timedata);
            buffer.flip();

            long timestamp = buffer.getLong();
            long now = getElapsed();
            mLastUDPPing = now-timestamp;
            // TODO refresh UDP?
        }
    };

    private Runnable mPingRunnable = new Runnable() {
        @Override
        public void run() {

            // In microseconds
            long t = getElapsed();

            if (!shouldForceTCP()) {
                ByteBuffer buffer = ByteBuffer.allocate(16);
                buffer.put((byte) ((JumbleUDPMessageType.UDPPing.ordinal() << 5) & 0xFF));
                buffer.putLong(t);

                sendUDPMessage(buffer.array(), 16, true);
//                Log.v(Constants.TAG, "OUT: UDP Ping");
            }

            Mumble.Ping.Builder pb = Mumble.Ping.newBuilder();
            pb.setTimestamp(t);
            pb.setGood(mCryptState.mUiGood);
            pb.setLate(mCryptState.mUiLate);
            pb.setLost(mCryptState.mUiLost);
            pb.setResync(mCryptState.mUiResync);
            // TODO accumulate stats and send with ping
            sendTCPMessage(pb.build(), JumbleTCPMessageType.Ping);
        }
    };

    /**
     * Creates a new JumbleConnection object to facilitate server connections.
     */
    public JumbleConnection(JumbleConnectionListener listener) {
        mListener = listener;
        mMainHandler = new Handler(Looper.getMainLooper());
        mTCPHandlers.add(mConnectionMessageHandler);
        mUDPHandlers.add(mUDPPingListener);
    }

    public void connect(String host, int port) throws JumbleException {
        mHost = host;
        mPort = port;
        mConnected = false;
        mSynchronized = false;
        mExceptionHandled = false;
        mUsingUDP = !shouldForceTCP();
        mStartTimestamp = System.nanoTime();

        mPingExecutorService = Executors.newSingleThreadScheduledExecutor();

        JumbleSSLSocketFactory socketFactory = createSocketFactory();

        try {
            mTCP = new JumbleTCP(socketFactory);
            mTCP.setTCPConnectionListener(this);
            mTCP.connect(host, port, mUseTor);
            // UDP thread is formally started after TCP connection.
        } catch (ConnectException e) {
            throw new JumbleException(e, false);
        }
    }

    public boolean isConnected() {
        return mConnected;
    }

    /**
     * Returns whether or not the service is fully synchronized with the remote server- this happens when we get the ServerSync message.
     * You shouldn't log any user actions until the connection is synchronized.
     * @return true or false, depending on whether or not we have received the ServerSync message.
     */
    public boolean isSynchronized() {
        return mSynchronized;
    }

    public long getElapsed() {
        return (System.nanoTime()-mStartTimestamp)/1000;
    }

    public void addTCPMessageHandlers(JumbleTCPMessageListener... handlers) {
        Collections.addAll(mTCPHandlers, handlers);
    }

    public void removeTCPMessageHandler(JumbleTCPMessageListener handler) {
        mTCPHandlers.remove(handler);
    }
    public void addUDPMessageHandlers(JumbleUDPMessageListener... handlers) {
        Collections.addAll(mUDPHandlers, handlers);
    }

    public void removeUDPMessageHandler(JumbleUDPMessageListener handler) {
        mUDPHandlers.remove(handler);
    }

    /**
     * Set whether to proxy all connections over a local Orbot instance.
     * This will force TCP tunneling for voice packets.
     * @param useTor true if Tor should be enabled and TCP forced.
     */
    public void setUseTor(boolean useTor) {
        mUseTor = useTor;
    }

    /**
     * Set whether to tunnel all voice packets over TCP, disabling the UDP thread.
     * @param forceTcp true if voice packets should tunnel over TCP.
     * @see #setUseTor
     */
    public void setForceTCP(boolean forceTcp) {
        mForceTCP = forceTcp;
    }

    /**
     * Sets the PKCS12 certificate data and password to use when authenticating.
     * @param certificate A PKCS12-formatted certificate.
     * @param password An optional password used to encrypt the certificate.
     */
    public void setKeys(byte[] certificate, String password) {
        mCertificate = certificate;
        mCertificatePassword = password;
    }

    public void setTrustStore(String path, String password, String format) {
        mTrustStorePath = path;
        mTrustStorePassword = password;
        mTrustStoreFormat = format;
    }

    public int getServerVersion() {
        return mServerVersion;
    }

    public String getServerRelease() {
        return mServerRelease;
    }

    public String getServerOSName() {
        return mServerOSName;
    }

    public String getServerOSVersion() {
        return mServerOSVersion;
    }

    public long getTCPLatency() {
        return mLastTCPPing;
    }

    public long getUDPLatency() {
        return mLastUDPPing;
    }

    public int getSession() {
        return mSession;
    }

    public int getMaxBandwidth() {
        return mMaxBandwidth;
    }

    public int getCodec() {
        return mCodec;
    }

    /**
     * Return whether or not voice packets should be tunneled over TCP.
     * @return true if TCP is manually forced or Tor has been disabled.
     */
    public boolean shouldForceTCP() {
        return mForceTCP || mUseTor;
    }

    /**
     * Gracefully shuts down all networking. Blocks until all network threads have stopped.
     */
    public void disconnect() {
        mConnected = false;
        mSynchronized = false;
        mHost = null;
        mPort = 0;

        // Stop running network resources
        if(mPingTask != null) mPingTask.cancel(true);
        if(mTCP != null) mTCP.disconnect();
        if(mUDP != null) mUDP.disconnect();
        mPingExecutorService.shutdown();

        mTCP = null;
        mUDP = null;
        mPingTask = null;
    }

    /**
     * Handles an exception that would cause termination of the connection.
     * @param e The exception that caused termination.
     */
    private void handleFatalException(final JumbleException e) {
        if(mExceptionHandled) return;
        mExceptionHandled = true;

        e.printStackTrace();
        mListener.onConnectionError(e);

        disconnect();
    }

    /**
     * Attempts to create a socket factory using the JumbleConnection's certificate and trust
     * store configuration.
     * @return A socket factory set to authenticate with a certificate and trust store, if set.
     */
    private JumbleSSLSocketFactory createSocketFactory() throws JumbleException {
        try {
            KeyStore keyStore = null;
            if(mCertificate != null) {
                keyStore = KeyStore.getInstance("PKCS12", new BouncyCastleProvider());
                ByteArrayInputStream inputStream = new ByteArrayInputStream(mCertificate);
                keyStore.load(inputStream, mCertificatePassword != null ?
                        mCertificatePassword.toCharArray() : new char[0]);
            }

            return new JumbleSSLSocketFactory(keyStore, mCertificatePassword, mTrustStorePath,
                    mTrustStorePassword, mTrustStoreFormat);
        } catch (KeyManagementException e) {
            throw new JumbleException("Could not recover keys from certificate", e, false);
        } catch (KeyStoreException e) {
            throw new JumbleException("Could not recover keys from certificate", e, false);
        } catch (UnrecoverableKeyException e) {
            throw new JumbleException("Could not recover keys from certificate", e, false);
        } catch (IOException e) {
            throw new JumbleException("Could not read certificate file", e, false);
        } catch (CertificateException e) {
            throw new JumbleException("Could not read certificate", e, false);
        } catch (NoSuchAlgorithmException e) {
                /*
                 * This will actually NEVER occur.
                 * We use Spongy Castle to provide the algorithm and provider implementations.
                 * There's no platform dependency.
                 */
            throw new RuntimeException("We use Spongy Castle- what? ", e);
        } catch (NoSuchProviderException e) {
                /*
                 * This will actually NEVER occur.
                 * We use Spongy Castle to provide the algorithm and provider implementations.
                 * There's no platform dependency.
                 */
            throw new RuntimeException("We use Spongy Castle- what? ", e);
        }
    }

    /**
     * Sends a protobuf message over TCP. Can silently fail.
     * @param message A built protobuf message.
     * @param messageType The corresponding protobuf message type.
     */
    public void sendTCPMessage(Message message, JumbleTCPMessageType messageType) {
        if(!mConnected || mTCP == null) return;
        mTCP.sendMessage(message, messageType);
    }

    /**
     * Sends a datagram message over UDP. Can silently fail, or be tunneled through TCP unless forced.
     * @param data Raw data to send over UDP.
     * @param length Length of the data to send.
     * @param force Whether to avoid tunneling this data over TCP.
     */
    public void sendUDPMessage(final byte[] data, final int length, final boolean force) {
        if (!mConnected) return;
        if (length > data.length) {
            throw new IllegalArgumentException("Requested length " + length + " is longer than " +
                    "available data length " + data.length + "!");
        }
        if (mServerVersion == 0x10202) applyLegacyCodecWorkaround(data);
        if (!force && (shouldForceTCP() || !mUsingUDP))
            mTCP.sendMessage(data, length, JumbleTCPMessageType.UDPTunnel);
        else if (!shouldForceTCP())
            mUDP.sendMessage(data, length);
    }

    /**
     * Sends a message to the server, asking it to tunnel future voice packets over TCP.
     */
    private void enableForceTCP() {
        if(!mConnected) return;
        Mumble.UDPTunnel.Builder utb = Mumble.UDPTunnel.newBuilder();
        utb.setPacket(ByteString.copyFrom(new byte[3]));
        sendTCPMessage(utb.build(), JumbleTCPMessageType.UDPTunnel);
    }

    @Override
    public void onTCPMessageReceived(JumbleTCPMessageType type, int length, byte[] data) {
        if(!UNLOGGED_MESSAGES.contains(type))
            Log.v(Constants.TAG, "IN: "+type);

        if(type == JumbleTCPMessageType.UDPTunnel) {
            onUDPDataReceived(data);
            return;
        }

        try {
            Message message = getProtobufMessage(data, type);
            for(JumbleTCPMessageListener handler : mTCPHandlers) {
                broadcastTCPMessage(handler, message, type);
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTCPConnectionEstablished() {
        mConnected = true;

        // Attempt to start UDP thread once connected.
        if(!shouldForceTCP()) {
            try {
                mUDP = new JumbleUDP(mCryptState);
                mUDP.setUDPConnectionListener(this);
                mUDP.connect(mHost, mPort);
            } catch (ConnectException e) {
                onUDPConnectionError(e);
            }
        }

        if(mListener != null) mListener.onConnectionEstablished();
    }

    @Override
    public void onTLSHandshakeFailed(X509Certificate[] chain) {
        disconnect();
        if(mListener != null) {
            mListener.onConnectionHandshakeFailed(chain);
            mListener.onConnectionDisconnected();
        }
    }

    @Override
    public void onTCPConnectionFailed(JumbleException e) {
        handleFatalException(e);
    }

    @Override
    public void onTCPConnectionDisconnect() {
        disconnect();
        if(mListener != null) mListener.onConnectionDisconnected();
    }

    @Override
    public void onUDPDataReceived(byte[] data) {
        if(mServerVersion == 0x10202) applyLegacyCodecWorkaround(data);
        int dataType = data[0] >> 5 & 0x7;
        if(dataType < 0 || dataType > JumbleUDPMessageType.values().length - 1) return; // Discard invalid data types
        JumbleUDPMessageType udpDataType = JumbleUDPMessageType.values()[dataType];

        for(JumbleUDPMessageListener handler : mUDPHandlers) {
            broadcastUDPMessage(handler, data, udpDataType);
        }
    }

    @Override
    public void onUDPConnectionError(Exception e) {
        e.printStackTrace();
        if(mListener != null) mListener.onConnectionWarning("UDP connection thread failed. Falling back to TCP.");
        enableForceTCP();
        // TODO recover UDP thread automagically
    }

    @Override
    public void resyncCryptState() {
        // Send an empty cryptstate message to resync.
        Mumble.CryptSetup.Builder csb = Mumble.CryptSetup.newBuilder();
        mTCP.sendMessage(csb.build(), JumbleTCPMessageType.CryptSetup);
    }

    /**
     * Workaround for 1.2.2 servers that report the old types for CELT alpha and beta.
     * @param data The UDP data to be patched, if we're on a 1.2.2 server.
     */
    private void applyLegacyCodecWorkaround(byte[] data) {
        JumbleUDPMessageType dataType = JumbleUDPMessageType.values()[data[0] >> 5 & 0x7];
        if(dataType == JumbleUDPMessageType.UDPVoiceCELTBeta)
            dataType = JumbleUDPMessageType.UDPVoiceCELTAlpha;
        else if(dataType == JumbleUDPMessageType.UDPVoiceCELTAlpha)
            dataType = JumbleUDPMessageType.UDPVoiceCELTBeta;
        data[0] = (byte) ((dataType.ordinal() << 5) & 0xFF);
    }

    /**
     * Gets the protobuf message from the passed TCP data.
     * We isolate this so we can first parse the message and then inform all handlers. Saves processing power.
     * @param data Raw protobuf TCP data.
     * @param messageType Type of the message.
     * @return The parsed protobuf message.
     * @throws InvalidProtocolBufferException Called if the messageType does not match the data.
     */
    public static Message getProtobufMessage(byte[] data, JumbleTCPMessageType messageType) throws InvalidProtocolBufferException {
        switch (messageType) {
            case Authenticate:
                return Mumble.Authenticate.parseFrom(data);
            case BanList:
                return Mumble.BanList.parseFrom(data);
            case Reject:
                return Mumble.Reject.parseFrom(data);
            case ServerSync:
                return Mumble.ServerSync.parseFrom(data);
            case ServerConfig:
                return Mumble.ServerConfig.parseFrom(data);
            case PermissionDenied:
                return Mumble.PermissionDenied.parseFrom(data);
            case UDPTunnel:
                return Mumble.UDPTunnel.parseFrom(data);
            case UserState:
                return Mumble.UserState.parseFrom(data);
            case UserRemove:
                return Mumble.UserRemove.parseFrom(data);
            case ChannelState:
                return Mumble.ChannelState.parseFrom(data);
            case ChannelRemove:
                return Mumble.ChannelRemove.parseFrom(data);
            case TextMessage:
                return Mumble.TextMessage.parseFrom(data);
            case ACL:
                return Mumble.ACL.parseFrom(data);
            case QueryUsers:
                return Mumble.QueryUsers.parseFrom(data);
            case Ping:
                return Mumble.Ping.parseFrom(data);
            case CryptSetup:
                return Mumble.CryptSetup.parseFrom(data);
            case ContextAction:
                return Mumble.ContextAction.parseFrom(data);
            case ContextActionModify:
                return Mumble.ContextActionModify.parseFrom(data);
            case Version:
                return Mumble.Version.parseFrom(data);
            case UserList:
                return Mumble.UserList.parseFrom(data);
            case PermissionQuery:
                return Mumble.PermissionQuery.parseFrom(data);
            case CodecVersion:
                return Mumble.CodecVersion.parseFrom(data);
            case UserStats:
                return Mumble.UserStats.parseFrom(data);
            case RequestBlob:
                return Mumble.RequestBlob.parseFrom(data);
            case SuggestConfig:
                return Mumble.SuggestConfig.parseFrom(data);
            default:
                throw new InvalidProtocolBufferException("Unknown TCP data passed.");
        }
    }


    /**
     * Reroutes TCP messages into the various responder methods of the handler.
     * @param handler Handler.
     * @param msg Protobuf message.
     * @param messageType The type of the message.
     */
    public final void broadcastTCPMessage(JumbleTCPMessageListener handler, Message msg, JumbleTCPMessageType messageType) {
        switch (messageType) {
            case Authenticate:
                handler.messageAuthenticate((Mumble.Authenticate) msg);
                break;
            case BanList:
                handler.messageBanList((Mumble.BanList) msg);
                break;
            case Reject:
                handler.messageReject((Mumble.Reject) msg);
                break;
            case ServerSync:
                handler.messageServerSync((Mumble.ServerSync) msg);
                break;
            case ServerConfig:
                handler.messageServerConfig((Mumble.ServerConfig) msg);
                break;
            case PermissionDenied:
                handler.messagePermissionDenied((Mumble.PermissionDenied) msg);
                break;
            case UDPTunnel:
                handler.messageUDPTunnel((Mumble.UDPTunnel) msg);
                break;
            case UserState:
                handler.messageUserState((Mumble.UserState) msg);
                break;
            case UserRemove:
                handler.messageUserRemove((Mumble.UserRemove) msg);
                break;
            case ChannelState:
                handler.messageChannelState((Mumble.ChannelState) msg);
                break;
            case ChannelRemove:
                handler.messageChannelRemove((Mumble.ChannelRemove) msg);
                break;
            case TextMessage:
                handler.messageTextMessage((Mumble.TextMessage) msg);
                break;
            case ACL:
                handler.messageACL((Mumble.ACL) msg);
                break;
            case QueryUsers:
                handler.messageQueryUsers((Mumble.QueryUsers) msg);
                break;
            case Ping:
                handler.messagePing((Mumble.Ping) msg);
                break;
            case CryptSetup:
                handler.messageCryptSetup((Mumble.CryptSetup) msg);
                break;
            case ContextAction:
                handler.messageContextAction((Mumble.ContextAction) msg);
                break;
            case ContextActionModify:
                Mumble.ContextActionModify actionModify = (Mumble.ContextActionModify) msg;
                if (actionModify.getOperation() == Mumble.ContextActionModify.Operation.Add)
                    handler.messageContextActionModify(actionModify);
                else if (actionModify.getOperation() == Mumble.ContextActionModify.Operation.Remove)
                    handler.messageRemoveContextAction(actionModify);
                break;
            case Version:
                handler.messageVersion((Mumble.Version) msg);
                break;
            case UserList:
                handler.messageUserList((Mumble.UserList) msg);
                break;
            case PermissionQuery:
                handler.messagePermissionQuery((Mumble.PermissionQuery) msg);
                break;
            case CodecVersion:
                handler.messageCodecVersion((Mumble.CodecVersion) msg);
                break;
            case UserStats:
                handler.messageUserStats((Mumble.UserStats) msg);
                break;
            case RequestBlob:
                handler.messageRequestBlob((Mumble.RequestBlob) msg);
                break;
            case SuggestConfig:
                handler.messageSuggestConfig((Mumble.SuggestConfig) msg);
                break;
            case VoiceTarget:
                handler.messageVoiceTarget((Mumble.VoiceTarget) msg);
                break;
        }
    }

    /**
     * Reroutes UDP messages into the various responder methods of the passed handler.
     * @param handler Handler to notify.
     * @param data Raw UDP data of the message.
     * @param messageType The type of the message.
     */
    public final void broadcastUDPMessage(JumbleUDPMessageListener handler, byte[] data, JumbleUDPMessageType messageType) {
        switch (messageType) {
            case UDPPing:
                handler.messageUDPPing(data);
                break;
            case UDPVoiceCELTAlpha:
            case UDPVoiceSpeex:
            case UDPVoiceCELTBeta:
            case UDPVoiceOpus:
                handler.messageVoiceData(data, messageType);
                break;
        }
    }

    public interface JumbleConnectionListener {
        public void onConnectionEstablished();
        public void onConnectionSynchronized();
        public void onConnectionHandshakeFailed(X509Certificate[] chain);
        public void onConnectionDisconnected();
        public void onConnectionError(JumbleException e);
        public void onConnectionWarning(String warning);
    }
}
