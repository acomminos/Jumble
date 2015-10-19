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

import android.os.Binder;
import android.util.Log;

import com.morlunk.jumble.exception.AudioException;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.IChannel;
import com.morlunk.jumble.model.IUser;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleConnection;
import com.morlunk.jumble.net.JumbleTCPMessageType;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.util.IJumbleObserver;
import com.morlunk.jumble.util.JumbleException;

import java.util.ArrayList;
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
public class JumbleBinder extends Binder {
    private final JumbleService mService;

    protected JumbleBinder(JumbleService service) {
        mService = service;
    }

    /**
     * Returns the current connection state of the service.
     * @return one of {@link com.morlunk.jumble.JumbleService.ConnectionState}.
     */
    public JumbleService.ConnectionState getConnectionState() {
        return mService.getConnectionState();
    }

    /**
     * If the {@link JumbleService} disconnected due to an error, returns that error.
     * @return The error causing disconnection. If the last disconnection was successful or a
     *         connection has yet to be established, returns null.
     */
    public JumbleException getConnectionError() {
        JumbleConnection connection = mService.getConnection();
        return connection != null ? connection.getError() : null;
    }

    /**
     * Returns the reconnection state of the {@link JumbleService}.
     * @return true if the service will attempt to automatically reconnect in the future.
     */
    public boolean isReconnecting() {
        return mService.isReconnecting();
    }

    /**
     * Cancels any future reconnection attempts. Does nothing if reconnection is not in progress.
     */
    public void cancelReconnect() {
        mService.setReconnecting(false);
    }

    /**
     * @return the latency in milliseconds for the TCP connection.
     * @throws IllegalStateException if not connected.
     */
    public long getTCPLatency() {
        return mService.getConnection().getTCPLatency();
    }

    /**
     * @return the latency in milliseconds for the UDP connection.
     * @throws IllegalStateException if not connected.
     */
    public long getUDPLatency() {
        return mService.getConnection().getUDPLatency();
    }

    /**
     * @return the maximum bandwidth in bps for audio allowed by the server, or -1 if not set.
     * @throws IllegalStateException if not synchronized.
     */
    public int getMaxBandwidth() {
        return mService.getConnection().getMaxBandwidth();
    }

    /**
     * @return the current bandwidth in bps for audio sent to the server, or a negative integer
     *         if unknown (prior to connection or after disconnection).
     * @throws IllegalStateException if not synchronized.
     */
    public int getCurrentBandwidth() {
        return mService.getAudioHandler().getCurrentBandwidth();
    }

    /**
     * Returns the protocol version returned by the server in the format 0xAABBCC, where AA
     * indicates the major version, BB indicates the minor version, and CC indicates the patch
     * version. This is the same formatting used by the Mumble protocol in big-endian format.
     * @return the current bandwidth in bps for audio sent to the server, or a negative integer
     *         if unknown (prior to connection or after disconnection).
     * @throws IllegalStateException if not synchronized.
     */
    public int getServerVersion() {
        return mService.getConnection().getServerVersion();
    }

    /**
     * @return a user-readable string with the server's Mumble release info.
     * @throws IllegalStateException if not synchronized.
     */
    public String getServerRelease() {
        return mService.getConnection().getServerRelease();
    }

    /**
     * @return a user-readable string with the server's OS name.
     * @throws IllegalStateException if not connected.
     */
    public String getServerOSName() {
        return mService.getConnection().getServerOSName();
    }

    /**
     * @return a user-readable string with the server's OS version.
     * @throws IllegalStateException if not synchronized.
     */
    public String getServerOSVersion() {
        return mService.getConnection().getServerOSVersion();
    }

    /**
     * Returns the current user's session. Set during server synchronization.
     * @return an integer identifying the current user's connection.
     * @throws IllegalStateException if not synchronized.
     */
    public int getSession() {
        return mService.getConnection().getSession();
    }

    /**
     * Returns the current user. Set during server synchronization.
     * @return the {@link IUser} representing the current user.
     * @throws IllegalStateException if not synchronized.
     */
    public IUser getSessionUser() {
        return mService.getModelHandler().getUser(getSession());
    }

    /**
     * Returns the user's current channel.
     * @return the {@link IChannel} representing the user's current channel.
     * @throws IllegalStateException if not synchronized.
     */
    public IChannel getSessionChannel() {
        IUser user = getSessionUser();
        if (user != null)
            return user.getChannel();
        throw new IllegalStateException("Session user should be set post-synchronization!");
    }

    /**
     * @return the server that Jumble is currently connected to (or attempted connection to).
     */
    public Server getConnectedServer() {
        return mService.getConnectedServer();
    }

    /**
     * Retrieves the user with the given session ID.
     * @param session An integer ID identifying a user's session. See {@link IUser#getSession()}.
     * @return A user with the given session, or null if not found.
     * @throws IllegalStateException if not synchronized.
     */
    public IUser getUser(int session) {
        return mService.getModelHandler().getUser(session);
    }

    /**
     * Retrieves the channel with the given ID.
     * @param id An integer ID identifying a channel. See {@link IChannel#getId()}.
     * @return A channel with the given session, or null if not found.
     * @throws IllegalStateException if not synchronized.
     */
    public IChannel getChannel(int id) {
        return mService.getModelHandler().getChannel(id);
    }

    /**
     * @return the root channel of the server.
     * @throws IllegalStateException if not synchronized.
     */
    public IChannel getRootChannel() {
        return getChannel(0);
    }

    public int getPermissions() {
        return mService.getModelHandler().getPermissions();
    }

    public int getTransmitMode() {
        return mService.getAudioHandler().getTransmitMode();
    }

    public JumbleUDPMessageType getCodec() {
        return mService.getConnection().getCodec();
    }

    public boolean usingBluetoothSco() {
        return mService.getBluetoothReceiver().isBluetoothScoOn();
    }

    public void enableBluetoothSco() {
        mService.getBluetoothReceiver().startBluetoothSco();
    }

    public void disableBluetoothSco() {
        mService.getBluetoothReceiver().stopBluetoothSco();
    }

    public boolean isTalking() {
        return mService.getAudioHandler().isRecording();
    }

    public void setTalkingState(boolean talking) {
        if (getSessionUser().isSelfMuted() || getSessionUser().isMuted())
            return;

        if (mService.getAudioHandler().getTransmitMode() != Constants.TRANSMIT_PUSH_TO_TALK) {
            Log.w(Constants.TAG, "Attempted to set talking state when not using PTT");
            return;
        }

        try {
            mService.getAudioHandler().setTalking(talking);
        } catch (AudioException e) {
            mService.logError(e.getMessage());
        }
    }

    public void joinChannel(int channel) {
        moveUserToChannel(getSession(), channel);
    }

    public void moveUserToChannel(int session, int channel) {
        Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
        usb.setSession(session);
        usb.setChannelId(channel);
        mService.getConnection().sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
    }

    public void createChannel(int parent, String name, String description, int position, boolean temporary) {
        Mumble.ChannelState.Builder csb = Mumble.ChannelState.newBuilder();
        csb.setParent(parent);
        csb.setName(name);
        csb.setDescription(description);
        csb.setPosition(position);
        csb.setTemporary(temporary);
        mService.getConnection().sendTCPMessage(csb.build(), JumbleTCPMessageType.ChannelState);
    }

    public void sendAccessTokens(final List<String> tokens) {
        mService.getConnection().sendAccessTokens(tokens);
    }

    public void requestBanList() {
        throw new UnsupportedOperationException("Not yet implemented"); // TODO
    }

    public void requestUserList() {
        throw new UnsupportedOperationException("Not yet implemented"); // TODO
    }

    public void requestPermissions(int channel) {
        Mumble.PermissionQuery.Builder pqb = Mumble.PermissionQuery.newBuilder();
        pqb.setChannelId(channel);
        mService.getConnection().sendTCPMessage(pqb.build(), JumbleTCPMessageType.PermissionQuery);
    }

    public void requestComment(int session) {
        Mumble.RequestBlob.Builder rbb = Mumble.RequestBlob.newBuilder();
        rbb.addSessionComment(session);
        mService.getConnection().sendTCPMessage(rbb.build(), JumbleTCPMessageType.RequestBlob);
    }

    public void requestAvatar(int session) {
        Mumble.RequestBlob.Builder rbb = Mumble.RequestBlob.newBuilder();
        rbb.addSessionTexture(session);
        mService.getConnection().sendTCPMessage(rbb.build(), JumbleTCPMessageType.RequestBlob);
    }

    public void requestChannelDescription(int channel) {
        Mumble.RequestBlob.Builder rbb = Mumble.RequestBlob.newBuilder();
        rbb.addChannelDescription(channel);
        mService.getConnection().sendTCPMessage(rbb.build(), JumbleTCPMessageType.RequestBlob);
    }

    public void registerUser(int session) {
        Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
        usb.setSession(session);
        usb.setUserId(0);
        mService.getConnection().sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
    }

    public void kickBanUser(int session, String reason, boolean ban) {
        Mumble.UserRemove.Builder urb = Mumble.UserRemove.newBuilder();
        urb.setSession(session);
        urb.setReason(reason);
        urb.setBan(ban);
        mService.getConnection().sendTCPMessage(urb.build(), JumbleTCPMessageType.UserRemove);
    }

    public Message sendUserTextMessage(int session, String message) {
        Mumble.TextMessage.Builder tmb = Mumble.TextMessage.newBuilder();
        tmb.addSession(session);
        tmb.setMessage(message);
        mService.getConnection().sendTCPMessage(tmb.build(), JumbleTCPMessageType.TextMessage);

        User self = mService.getModelHandler().getUser(getSession());
        User user = mService.getModelHandler().getUser(session);
        List<User> users = new ArrayList<User>(1);
        users.add(user);
        return new Message(getSession(), self.getName(), new ArrayList<Channel>(0), new ArrayList<Channel>(0), users, message);
    }

    public Message sendChannelTextMessage(int channel, String message, boolean tree) {
        Mumble.TextMessage.Builder tmb = Mumble.TextMessage.newBuilder();
        if(tree) tmb.addTreeId(channel);
        else tmb.addChannelId(channel);
        tmb.setMessage(message);
        mService.getConnection().sendTCPMessage(tmb.build(), JumbleTCPMessageType.TextMessage);

        User self = mService.getModelHandler().getUser(getSession());
        Channel targetChannel = mService.getModelHandler().getChannel(channel);
        List<Channel> targetChannels = new ArrayList<Channel>();
        targetChannels.add(targetChannel);
        return new Message(getSession(), self.getName(), targetChannels, tree ? targetChannels : new ArrayList<Channel>(0), new ArrayList<User>(0), message);
    }

    public void setUserComment(int session, String comment) {
        Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
        usb.setSession(session);
        usb.setComment(comment);
        mService.getConnection().sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
    }

    public void setPrioritySpeaker(int session, boolean priority) {
        Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
        usb.setSession(session);
        usb.setPrioritySpeaker(priority);
        mService.getConnection().sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
    }

    public void removeChannel(int channel) {
        Mumble.ChannelRemove.Builder crb = Mumble.ChannelRemove.newBuilder();
        crb.setChannelId(channel);
        mService.getConnection().sendTCPMessage(crb.build(), JumbleTCPMessageType.ChannelRemove);
    }

    public void setMuteDeafState(int session, boolean mute, boolean deaf) {
        Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
        usb.setSession(session);
        usb.setMute(mute);
        usb.setDeaf(deaf);
        if (!mute) usb.setSuppress(false);
        mService.getConnection().sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
    }

    public void setSelfMuteDeafState(boolean mute, boolean deaf) {
        Mumble.UserState.Builder usb = Mumble.UserState.newBuilder();
        usb.setSelfMute(mute);
        usb.setSelfDeaf(deaf);
        mService.getConnection().sendTCPMessage(usb.build(), JumbleTCPMessageType.UserState);
    }

    public void registerObserver(IJumbleObserver observer) {
        mService.registerObserver(observer);
    }

    public void unregisterObserver(IJumbleObserver observer) {
        mService.unregisterObserver(observer);
    }
}
