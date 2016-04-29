/*
 * Copyright (C) 2015 Andrew Comminos <andrew@comminos.com>
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

import com.morlunk.jumble.model.IChannel;
import com.morlunk.jumble.model.IUser;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.WhisperTarget;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.util.IJumbleObserver;
import com.morlunk.jumble.util.JumbleException;
import com.morlunk.jumble.util.VoiceTargetMode;

import java.util.List;

/**
 * A public interface for clients to communicate with a {@link JumbleService}.
 * The long-term goal for this class is to migrate of the complexity out of this class into a
 * JumbleProtocol class that is owned by a {@link com.morlunk.jumble.net.JumbleConnection}.
 * <br><br>
 * Calls are not guaranteed to be thread-safe, so only call the binder from the main thread.
 * Service state changes related to connection state are only guaranteed to work if isConnected()
 * is checked to be true.
 * <br><br>
 * If not explicitly stated in the method documentation, any call that depends on connection state
 * will throw IllegalStateException if disconnected or not synchronized.
 */
public interface IJumbleService {
    /**
     * Returns the current connection state of the service.
     * @return one of {@link JumbleService.ConnectionState}.
     */
    JumbleService.ConnectionState getConnectionState();

    /**
     * If the {@link JumbleService} disconnected due to an error, returns that error.
     * @return The error causing disconnection. If the last disconnection was successful or a
     *         connection has yet to be established, returns null.
     */
    JumbleException getConnectionError();

    /**
     * Returns the reconnection state of the {@link JumbleService}.
     * @return true if the service will attempt to automatically reconnect in the future.
     */
    boolean isReconnecting();

    /**
     * Cancels any future reconnection attempts. Does nothing if reconnection is not in progress.
     */
    void cancelReconnect();

    /**
     * @return the latency in milliseconds for the TCP connection.
     * @throws IllegalStateException if not connected.
     */
    long getTCPLatency();

    /**
     * @return the latency in milliseconds for the UDP connection.
     * @throws IllegalStateException if not connected.
     */
    long getUDPLatency();

    /**
     * @return the maximum bandwidth in bps for audio allowed by the server, or -1 if not set.
     * @throws IllegalStateException if not synchronized.
     */
    int getMaxBandwidth();

    /**
     * @return the current bandwidth in bps for audio sent to the server, or a negative integer
     *         if unknown (prior to connection or after disconnection).
     * @throws IllegalStateException if not synchronized.
     */
    int getCurrentBandwidth();

    /**
     * Returns the protocol version returned by the server in the format 0xAABBCC, where AA
     * indicates the major version, BB indicates the minor version, and CC indicates the patch
     * version. This is the same formatting used by the Mumble protocol in big-endian format.
     * @return the current bandwidth in bps for audio sent to the server, or a negative integer
     *         if unknown (prior to connection or after disconnection).
     * @throws IllegalStateException if not synchronized.
     */
    int getServerVersion();

    /**
     * @return a user-readable string with the server's Mumble release info.
     * @throws IllegalStateException if not synchronized.
     */
    String getServerRelease();

    /**
     * @return a user-readable string with the server's OS name.
     * @throws IllegalStateException if not connected.
     */
    String getServerOSName();

    /**
     * @return a user-readable string with the server's OS version.
     * @throws IllegalStateException if not synchronized.
     */
    String getServerOSVersion();

    /**
     * Returns the current user's session. Set during server synchronization.
     * @return an integer identifying the current user's connection.
     * @throws IllegalStateException if not synchronized.
     */
    int getSession();

    /**
     * Returns the current user. Set during server synchronization.
     * @return the {@link IUser} representing the current user.
     * @throws IllegalStateException if not synchronized.
     */
    IUser getSessionUser();

    /**
     * Returns the user's current channel.
     * @return the {@link IChannel} representing the user's current channel.
     * @throws IllegalStateException if not synchronized.
     */
    IChannel getSessionChannel();

    /**
     * @return the server that Jumble is currently connected to (or attempted connection to).
     */
    Server getConnectedServer();

    /**
     * Retrieves the user with the given session ID.
     * @param session An integer ID identifying a user's session. See {@link IUser#getSession()}.
     * @return A user with the given session, or null if not found.
     * @throws IllegalStateException if not synchronized.
     */
    IUser getUser(int session);

    /**
     * Retrieves the channel with the given ID.
     * @param id An integer ID identifying a channel. See {@link IChannel#getId()}.
     * @return A channel with the given session, or null if not found.
     * @throws IllegalStateException if not synchronized.
     */
    IChannel getChannel(int id);

    /**
     * @return the root channel of the server.
     * @throws IllegalStateException if not synchronized.
     */
    IChannel getRootChannel();

    int getPermissions();

    int getTransmitMode();

    JumbleUDPMessageType getCodec();

    boolean usingBluetoothSco();

    void enableBluetoothSco();

    void disableBluetoothSco();

    boolean isTalking();

    void setTalkingState(boolean talking);

    void joinChannel(int channel);

    void moveUserToChannel(int session, int channel);

    void createChannel(int parent, String name, String description, int position, boolean temporary);

    void sendAccessTokens(List<String> tokens);

    void requestBanList();

    void requestUserList();

    void requestPermissions(int channel);

    void requestComment(int session);

    void requestAvatar(int session);

    void requestChannelDescription(int channel);

    void registerUser(int session);

    void kickBanUser(int session, String reason, boolean ban);

    Message sendUserTextMessage(int session, String message);

    Message sendChannelTextMessage(int channel, String message, boolean tree);

    void setUserComment(int session, String comment);

    void setPrioritySpeaker(int session, boolean priority);

    void removeChannel(int channel);

    void setMuteDeafState(int session, boolean mute, boolean deaf);

    void setSelfMuteDeafState(boolean mute, boolean deaf);

    void registerObserver(IJumbleObserver observer);

    void unregisterObserver(IJumbleObserver observer);

    /**
     * Links the provided two channels together.
     */
    void linkChannels(IChannel channelA, IChannel channelB);

    /**
     * Unlinks the two provided channels.
     */
    void unlinkChannels(IChannel channelA, IChannel channelB);

    /**
     * Unlinks all channels from the provided channel.
     * @param channel The channel to be unlinked.
     */
    void unlinkAllChannels(IChannel channel);

    /**
     * Registers a whisper target to be used as a voice target on the server.
     * Note that Mumble only supports a maximum of 30 active voice targets at once.
     * @param target The target to register.
     * @return A voice target ID in the range [1, 30], or a negative value if all slots are full.
     */
    byte registerWhisperTarget(final WhisperTarget target);

    /**
     * Unregisters a whisper target from the server.
     * Note that Mumble only supports a maximum of 30 active voice targets at once.
     * @param target The target ID to unregister.
     */
    void unregisterWhisperTarget(byte targetId);

    /**
     * Sets the active voice target to the provided ID.<br>
     * 0: Normal speech<br>
     * 1-30: Whisper targets<br>
     * 31: Server loopback
     * @param targetId A voice target ID in the range [0, 31].
     */
    void setVoiceTargetId(byte targetId);

    /**
     * Gets the current voice target ID in use, in the range [0, 31].
     * @return The active voice target ID.
     */
    byte getVoiceTargetId();

    /**
     * Gets the current voice target mode.
     * @return The active voice target mode.
     */
    VoiceTargetMode getVoiceTargetMode();

    /**
     * Returns the current whisper target.
     * @return the set whisper target, or null if the user is not whispering.
     */
    WhisperTarget getWhisperTarget();
}
