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

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.protocol.JumbleTCPMessageListener;
import com.morlunk.jumble.protocol.JumbleUDPMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocket;

public class JumbleConnection {
    public interface JumbleConnectionListener {
        public void onConnectionEstablished();
        public void onConnectionDisconnected();
        public void onConnectionError(JumbleConnectionException e);
        public void onConnectionWarning(String warning);
    }

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

    private JumbleService mService;
    private JumbleConnectionListener mListener;

    // Tor connection details
    public static final String TOR_HOST = "localhost";
    public static final int TOR_PORT = 9050;

    // Authentication
    private JumbleSSLSocketFactory mSocketFactory;

    // Threading
    private ExecutorService mExecutorService;
    private ScheduledExecutorService mPingExecutorService;
    private NetworkSendThread mNetworkSendThread;
    private Handler mMainHandler;
    private Handler mNetworkSendHandler;

    // Networking and protocols
    private InetAddress mHost;
    private JumbleTCP mTCP;
    private Future mTCPTask;
    private JumbleUDP mUDP;
    private ScheduledFuture mPingTask;
    private boolean mUsingUDP = true;
    private boolean mConnected;
    private boolean mSynchronized;
    private boolean mExceptionHandled = false;
    private CryptState mCryptState = new CryptState();
    private long mStartTimestamp; // Time that the connection was initiated in nanoseconds

    // Latency
    private long mLastUDPPing;
    private long mLastTCPPing;

    // Server
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
            if(mService.shouldForceTcp()) {
                Mumble.UDPTunnel.Builder utb = Mumble.UDPTunnel.newBuilder();
                utb.setPacket(ByteString.copyFrom(new byte[3]));
                sendTCPMessage(utb.build(), JumbleTCPMessageType.UDPTunnel);
            }

            // Start TCP/UDP ping thread. FIXME is this the right place?
            mPingTask = mPingExecutorService.scheduleAtFixedRate(mPingRunnable, 0, 5, TimeUnit.SECONDS);

            mSession = msg.getSession();
            mMaxBandwidth = msg.getMaxBandwidth();
            mSynchronized = true;

            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnectionEstablished();
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
            handleFatalException(new JumbleConnectionException(msg));
        }

        @Override
        public void messageUserRemove(final Mumble.UserRemove msg) {
            if(msg.getSession() == mSession) {
                mConnected = false;
                handleFatalException(new JumbleConnectionException(msg));
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
                handleFatalException(new JumbleConnectionException("Received invalid cryptographic nonce from server", e, true));
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
                if(!mService.shouldForceTcp() && mListener != null) {
                    if((mCryptState.mUiRemoteGood == 0) && (mCryptState.mUiGood == 0))
                        mListener.onConnectionWarning("UDP packets cannot be sent to or received from the server. Switching to TCP mode.");
                    else if(mCryptState.mUiRemoteGood == 0)
                        mListener.onConnectionWarning("UDP packets cannot be sent to the server. Switching to TCP mode.");
                    else
                        mListener.onConnectionWarning("UDP packets cannot be received from the server. Switching to TCP mode.");
                }
            } else if (!mUsingUDP && (mCryptState.mUiRemoteGood > 3) && (mCryptState.mUiGood > 3)) {
                mUsingUDP = true;
                if (!mService.shouldForceTcp() && mListener != null)
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

            if(!mService.shouldForceTcp()) {
                ByteBuffer buffer = ByteBuffer.allocate(16);
                buffer.put((byte) ((JumbleUDPMessageType.UDPPing.ordinal() << 5) & 0xFF));
                buffer.putLong(t);

                sendUDPMessage(buffer.array(), 16, true);
                Log.v(Constants.TAG, "OUT: UDP Ping");
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
     * @param context An Android context.
     * @throws CertificateException if an exception occurred parsing the p12 file.
     * @throws IOException if there was an error reading the p12 file.
     */
    public JumbleConnection(JumbleService service,
                            JumbleConnectionListener listener) {
        mService = service;
        mListener = listener;
        mMainHandler = new Handler(service.getMainLooper());
        mTCPHandlers.add(mConnectionMessageHandler);
        mUDPHandlers.add(mUDPPingListener);
    }

    public void connect() throws JumbleConnectionException {
        mConnected = false;
        mSynchronized = false;
        mExceptionHandled = false;
        mUsingUDP = !mService.shouldForceTcp();
        mStartTimestamp = System.nanoTime();

        mExecutorService = Executors.newFixedThreadPool(mService.shouldForceTcp() ? 2 : 3); // One TCP receive thread, one UDP receive thread (if not forcing TCP), one network send thread
        mPingExecutorService = Executors.newSingleThreadScheduledExecutor();

        setupSocketFactory(mService.getCertificate(), mService.getCertificatePassword());
        mTCP = new JumbleTCP();
        if(!mService.shouldForceTcp())
            mUDP = new JumbleUDP();
        mNetworkSendThread = new NetworkSendThread();
        mExecutorService.submit(mNetworkSendThread);
        mTCPTask = mExecutorService.submit(mTCP);
        // We'll start UDP thread after TCP is established. FIXME?
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
     * Gracefully shuts down all networking. Blocks until all network threads have stopped.
     */
    public void disconnect() {
        mConnected = false;
        mSynchronized = false;

        // Stop running network resources
        if(mPingTask != null) mPingTask.cancel(true);
        try {
            if(mTCP != null) mTCP.disconnect();
            if(mUDP != null) mUDP.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Block until main listening thread has stopped
        try {
            if(mTCPTask != null) mTCPTask.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        mTCP = null;
        mTCPTask = null;
        mUDP = null;
        mPingTask = null;
        mExecutorService.shutdownNow();
        mPingExecutorService.shutdownNow();
    }

    /**
     * Immediately shuts down all network threads.
     */
    public void forceDisconnect() {
        mConnected = false;
        mSynchronized = false;
        mExecutorService.shutdownNow();
        mPingExecutorService.shutdownNow();
        mTCP = null;
        mUDP = null;
        if(mListener != null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnectionDisconnected();
                }
            });
        }
    }

    /**
     * Handles an exception that would cause termination of the connection.
     * @param e The exception that caused termination.
     */
    private void handleFatalException(final JumbleConnectionException e) {
        if(mExceptionHandled) return;
        mExceptionHandled = true;

        e.printStackTrace();
        if(mListener != null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnectionError(e);
                }
            });
        }
        mNetworkSendHandler.post(new Runnable() {
            @Override
            public void run() {
                disconnect();
            }
        });
    }

    /**
     * Attempts to load the PKCS12 certificate from the passed data, and sets up an SSL socket factory.
     * You must call this method before establishing a TCP connection.
     * @param certificate The binary representation of a PKCS12 (.p12) certificate. May be null.
     * @param certificatePassword The password to decrypt the key store. May be null.
     */
    protected void setupSocketFactory(byte[] certificate, String certificatePassword) throws JumbleConnectionException {
        try {
            KeyStore keyStore = null;
            if(certificate != null) {
                keyStore = KeyStore.getInstance("PKCS12");
                ByteArrayInputStream inputStream = new ByteArrayInputStream(certificate);
                keyStore.load(inputStream, certificatePassword != null ? certificatePassword.toCharArray() : new char[0]);
            }

            mSocketFactory = new JumbleSSLSocketFactory(keyStore, certificatePassword);
        } catch (KeyManagementException e) {
            throw new JumbleConnectionException("Could not recover keys from certificate", e, false);
        } catch (KeyStoreException e) {
            throw new JumbleConnectionException("Could not recover keys from certificate", e, false);
        } catch (UnrecoverableKeyException e) {
            throw new JumbleConnectionException("Could not recover keys from certificate", e, false);
        } catch (IOException e) {
            throw new JumbleConnectionException("Could not read certificate file", e, false);
        } catch (CertificateException e) {
            e.printStackTrace();
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

    public void sendTCPMessage(final Message message, final JumbleTCPMessageType messageType) {
        mNetworkSendHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mTCP.sendMessage(message, messageType);
                } catch (IOException e) {
                    e.printStackTrace(); // TODO handle me
                }
            }
        });
    }

    public void sendUDPMessage(final byte[] data, final int length, final boolean force) {
        if(mServerVersion == 0x10202) applyLegacyCodecWorkaround(data);
        mNetworkSendHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!force && (mService.shouldForceTcp() || !mUsingUDP))
                        mTCP.sendMessage(data, length, JumbleTCPMessageType.UDPTunnel);
                    else if (!mService.shouldForceTcp())
                        mUDP.sendMessage(data, length);
                } catch (IOException e) {
                    e.printStackTrace(); // TODO handle me
                }
            }
        });
    }

    private void handleTCPMessage(byte[] data, int length, JumbleTCPMessageType messageType) {
        if(!UNLOGGED_MESSAGES.contains(messageType))
            Log.v(Constants.TAG, "IN: "+messageType);

        if(messageType == JumbleTCPMessageType.UDPTunnel) {
            handleUDPMessage(data);
            return;
        }

        try {
            Message message = getProtobufMessage(data, messageType);
            for(JumbleTCPMessageListener handler : mTCPHandlers) {
                broadcastTCPMessage(handler, message, messageType);
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    private void handleUDPMessage(byte[] data) {
        if(mServerVersion == 0x10202) applyLegacyCodecWorkaround(data);
        JumbleUDPMessageType dataType = JumbleUDPMessageType.values()[data[0] >> 5 & 0x7];

        for(JumbleUDPMessageListener handler : mUDPHandlers) {
            broadcastUDPMessage(handler, data, dataType);
        }
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
    public static final Message getProtobufMessage(byte[] data, JumbleTCPMessageType messageType) throws InvalidProtocolBufferException {
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

    /**
     * Class to maintain and interface with the TCP connection to a Mumble server.
     */
    private class JumbleTCP implements Runnable {
        private SSLSocket mTCPSocket;
        private DataInputStream mDataInput;
        private DataOutputStream mDataOutput;

        /**
         * Attempts to disconnect gracefully.
         * @throws IOException if the socket couldn't close as expected.
         */
        public void disconnect() throws IOException {
            mDataOutput.close();
            mDataInput.close();
            mTCPSocket.close();
        }

        public void run() {
            Server server = mService.getServer();
            try {
                try {
                    mHost = InetAddress.getByName(server.getHost());
                } catch (UnknownHostException e) {
                    handleFatalException(new JumbleConnectionException("Could not resolve host", e, true));
                    return;
                }

                if(mService.shouldUseTor())
                    mTCPSocket = mSocketFactory.createTorSocket(server.getHost(), server.getPort(), TOR_HOST, TOR_PORT);
                else
                    mTCPSocket = mSocketFactory.createSocket(server.getHost(), server.getPort());

                mTCPSocket.startHandshake();

                Log.v(Constants.TAG, "Started handshake");

                mDataInput = new DataInputStream(mTCPSocket.getInputStream());
                mDataOutput = new DataOutputStream(mTCPSocket.getOutputStream());
            } catch (SocketException e) {
                handleFatalException(new JumbleConnectionException("Could not open a connection to the host", e, false));
                return;
            } catch (IOException e) {
                handleFatalException(new JumbleConnectionException("An error occurred when communicating with the host", e, false));
                return;
            }

            mConnected = true;

            Log.v(Constants.TAG, "Started listening");

            if(!mService.shouldForceTcp())
                mExecutorService.submit(mUDP);


            // Send version information and authenticate.
            final Mumble.Version.Builder version = Mumble.Version.newBuilder();
            version.setRelease(mService.getClientName());
            version.setVersion(Constants.PROTOCOL_VERSION);
            version.setOs("Android");
            version.setOsVersion(Build.VERSION.RELEASE);

            final Mumble.Authenticate.Builder auth = Mumble.Authenticate.newBuilder();
            auth.setUsername(server.getUsername());
            auth.setPassword(server.getPassword());
            auth.addCeltVersions(Constants.CELT_7_VERSION);
            auth.addCeltVersions(Constants.CELT_11_VERSION);
            auth.setOpus(mService.shouldUseOpus());

            sendTCPMessage(version.build(), JumbleTCPMessageType.Version);
            sendTCPMessage(auth.build(), JumbleTCPMessageType.Authenticate);

            while(mConnected) {
                try {
                    final short messageType = mDataInput.readShort();
                    final int messageLength = mDataInput.readInt();
                    final byte[] data = new byte[messageLength];
                    mDataInput.readFully(data);

                    final JumbleTCPMessageType tcpMessageType = JumbleTCPMessageType.values()[messageType];
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleTCPMessage(data, messageLength, tcpMessageType);
                        }
                    });
                } catch (final IOException e) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            // We perform this action on the main thread so that any clean disconnects get handled first.
                            if(mConnected) handleFatalException(new JumbleConnectionException("Lost connection to server", e, true));
                        }
                    });
                    break;
                }
            }

            if(mListener != null) {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onConnectionDisconnected();
                    }
                });
            }
        }

        /**
         * Attempts to send a protobuf message over TCP. Executes on the TCP thread.
         * @param message The message to send.
         * @param messageType The type of the message to send.
         * @throws IOException if we can't write the message to the server.
         */
        public void sendMessage(Message message, JumbleTCPMessageType messageType) throws IOException {
            if(!UNLOGGED_MESSAGES.contains(messageType))
                Log.v(Constants.TAG, "OUT: "+messageType);
            mDataOutput.writeShort(messageType.ordinal());
            mDataOutput.writeInt(message.getSerializedSize());
            message.writeTo(mDataOutput);
        }
        /**
         * Attempts to send a protobuf message over TCP. Executes on the TCP thread.
         * @param message The data to send.
         * @param length The length of the byte array.
         * @param messageType The type of the message to send.
         * @throws IOException if we can't write the message to the server.
         */
        public void sendMessage(byte[] message, int length, JumbleTCPMessageType messageType) throws IOException {
            if(!UNLOGGED_MESSAGES.contains(messageType))
                Log.v(Constants.TAG, "OUT: "+messageType);
            mDataOutput.writeShort(messageType.ordinal());
            mDataOutput.writeInt(length);
            mDataOutput.write(message, 0, length);
        }
    }

    /**
     * Class to maintain and receive packets from the UDP connection to a Mumble server.
     */
    private class JumbleUDP implements Runnable {
        private static final int BUFFER_SIZE = 2048;
        private DatagramSocket mUDPSocket;
        private byte[] mDecryptedBuffer = new byte[BUFFER_SIZE];

        public void disconnect() {
            mUDPSocket.disconnect();
            mUDPSocket.close();
        }

        @Override
        public void run() {
            try {
                mUDPSocket = new DatagramSocket();
            } catch (SocketException e) {
                mListener.onConnectionWarning("Could not initialize UDP socket! Try forcing a TCP connection.");
                return;
            }
            mUDPSocket.connect(mHost, mService.getServer().getPort());
            final DatagramPacket packet = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);

            Log.v(Constants.TAG, "Created UDP socket");

            while(mConnected) {
                try {
                    mUDPSocket.receive(packet);
                    /*
                    if (decryptedData == null &&
                            mCryptState.getLastGoodElapsed() > 5000000 &&
                            mCryptState.getLastRequestElapsed() > 5000000) {
                        // If decryption fails, request resync
                        mCryptState.mLastRequestStart = System.nanoTime();
                        Mumble.CryptSetup.Builder csb = Mumble.CryptSetup.newBuilder();
                        mTCP.sendMessage(csb.build(), JumbleTCPMessageType.CryptSetup);
                    }
                    */
                    // Decrypt UDP packet using OCB-AES128
                    final byte[] data = packet.getData();
                    final int length = packet.getLength();
                    mCryptState.decrypt(data, mDecryptedBuffer, length);

                    handleUDPMessage(mDecryptedBuffer);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        public void sendMessage(byte[] data, int length) throws IOException {
            if(!mCryptState.isValid())
                return;
            byte[] encryptedData = mCryptState.encrypt(data, length);
            DatagramPacket packet = new DatagramPacket(encryptedData, encryptedData.length);
            packet.setAddress(mHost);
            packet.setPort(mService.getServer().getPort());
            mUDPSocket.send(packet);
        }
    }

    /**
     * Thread to send network messages on.
     */
    private class NetworkSendThread implements Runnable {

        @Override
        public void run() {
            Looper.prepare();
            mNetworkSendHandler = new Handler();
            Looper.loop();
        }
    }
}
