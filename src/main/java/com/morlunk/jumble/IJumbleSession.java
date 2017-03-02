package com.morlunk.jumble;

import com.morlunk.jumble.model.IChannel;
import com.morlunk.jumble.model.IUser;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.WhisperTarget;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.util.IJumbleObserver;
import com.morlunk.jumble.util.VoiceTargetMode;

import java.util.List;

/**
 * An interface representing a live connection to the server.
 * Created by andrew on 28/02/17.
 */

public interface IJumbleSession {
    /**
     * @return the latency in milliseconds for the TCP connection.
     */
    long getTCPLatency();

    /**
     * @return the latency in milliseconds for the UDP connection.
     */
    long getUDPLatency();

    /**
     * @return the maximum bandwidth in bps for audio allowed by the server, or -1 if not set.
     */
    int getMaxBandwidth();

    /**
     * @return the current bandwidth in bps for audio sent to the server, or a negative integer
     *         if unknown (prior to connection or after disconnection).
     */
    int getCurrentBandwidth();

    /**
     * Returns the protocol version returned by the server in the format 0xAABBCC, where AA
     * indicates the major version, BB indicates the minor version, and CC indicates the patch
     * version. This is the same formatting used by the Mumble protocol in big-endian format.
     * @return the current bandwidth in bps for audio sent to the server, or a negative integer
     *         if unknown (prior to connection or after disconnection).
     */
    int getServerVersion();

    /**
     * @return a user-readable string with the server's Mumble release info.
     */
    String getServerRelease();

    /**
     * @return a user-readable string with the server's OS name.
     */
    String getServerOSName();

    /**
     * @return a user-readable string with the server's OS version.
     */
    String getServerOSVersion();

    /**
     * Returns the current user's session. Set during server synchronization.
     * @return an integer identifying the current user's connection.
     */
    int getSessionId();

    /**
     * Returns the current user. Set during server synchronization.
     * @return the {@link IUser} representing the current user.
     */
    IUser getSessionUser();

    /**
     * Returns the user's current channel.
     * @return the {@link IChannel} representing the user's current channel.
     */
    IChannel getSessionChannel();

    /**
     * Retrieves the user with the given session ID.
     * @param session An integer ID identifying a user's session. See {@link IUser#getSession()}.
     * @return A user with the given session, or null if not found.
     */
    IUser getUser(int session);

    /**
     * Retrieves the channel with the given ID.
     * @param id An integer ID identifying a channel. See {@link IChannel#getId()}.
     * @return A channel with the given session, or null if not found.
     */
    IChannel getChannel(int id);

    /**
     * @return the root channel of the server.
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
