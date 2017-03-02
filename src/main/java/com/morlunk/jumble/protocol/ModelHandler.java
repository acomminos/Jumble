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

package com.morlunk.jumble.protocol;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.RemoteException;
import android.util.Log;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.R;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.protocol.JumbleTCPMessageListener;
import com.morlunk.jumble.util.IJumbleObserver;
import com.morlunk.jumble.util.JumbleLogger;
import com.morlunk.jumble.util.MessageFormatter;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles network messages related to the user-channel tree model.
 * This includes channels, users, messages, and permissions.
 * Created by andrew on 18/07/13.
 */
public class ModelHandler extends JumbleTCPMessageListener.Stub {
    private final Context mContext;
    private final Map<Integer, Channel> mChannels;
    private final Map<Integer, User> mUsers;
    private final List<Integer> mLocalMuteHistory;
    private final List<Integer> mLocalIgnoreHistory;
    private final IJumbleObserver mObserver;
    private final JumbleLogger mLogger;
    private int mPermissions;
    private int mSession;

    public ModelHandler(Context context, IJumbleObserver observer, JumbleLogger logger,
                        @Nullable List<Integer> localMuteHistory,
                        @Nullable List<Integer> localIgnoreHistory) {
        mContext = context;
        mChannels = new HashMap<Integer, Channel>();
        mUsers = new HashMap<Integer, User>();
        mLocalMuteHistory = localMuteHistory;
        mLocalIgnoreHistory = localIgnoreHistory;
        mObserver = observer;
        mLogger = logger;
    }

    public Channel getChannel(int id) {
        return mChannels.get(id);
    }

    public User getUser(int session) {
        return mUsers.get(session);
    }

    /**
     * Creates a stub channel with the given ID.
     * Useful for keeping user references when we get a UserState message before a ChannelState.
     * @param id The channel ID.
     * @return The newly created stub channel.
     */
    private Channel createStubChannel(int id) {
        Channel channel = new Channel(id, false);
        mChannels.put(id, channel);
        return channel;
    }

    public Map<Integer, Channel> getChannels() {
        return Collections.unmodifiableMap(mChannels);
    }

    public Map<Integer, User> getUsers() {
        return Collections.unmodifiableMap(mUsers);
    }

    /**
     * Returns the current user's permissions.
     * @return The server-wide permissions.
     */
    public int getPermissions() {
        return mPermissions;
    }

    public void clear() {
        mChannels.clear();
        mUsers.clear();
    }

    @Override
    public void messageChannelState(Mumble.ChannelState msg) {
        if(!msg.hasChannelId())
            return;

        Channel channel = mChannels.get(msg.getChannelId());
        Channel parent = mChannels.get(msg.getParent());

        final boolean newChannel = channel == null;

        if(channel == null) {
            channel = new Channel(msg.getChannelId(), msg.getTemporary());
            mChannels.put(msg.getChannelId(), channel);
        }

        if(msg.hasName())
            channel.setName(msg.getName());

        if(msg.hasPosition())
            channel.setPosition(msg.getPosition());

        if(msg.hasParent()) {
            Channel oldParent = channel.getParent();
            channel.setParent(parent);
            parent.addSubchannel(channel);
            if(oldParent != null) {
                oldParent.removeSubchannel(channel);
            }
        }

        if(msg.hasDescriptionHash())
            channel.setDescriptionHash(msg.getDescriptionHash().toByteArray());

        if(msg.hasDescription())
            channel.setDescription(msg.getDescription());

        if(msg.getLinksCount() > 0) {
            channel.clearLinks();
            for(int link : msg.getLinksList()) {
                Channel linked = mChannels.get(link);
                channel.addLink(linked);
                // Don't add this channel to the other channel's link list- this update occurs on
                // server synchronization, and we will get a message for the other channels' links
                // laster.
            }
        }

        if(msg.getLinksRemoveCount() > 0) {
            for(int link : msg.getLinksRemoveList()) {
                Channel linked = mChannels.get(link);
                channel.removeLink(linked);
                linked.removeLink(channel);
            }
        }

        if(msg.getLinksAddCount() > 0) {
            for(int link : msg.getLinksAddList()) {
                Channel linked = mChannels.get(link);
                channel.addLink(linked);
                linked.addLink(channel);
            }
        }

        if(newChannel)
            mObserver.onChannelAdded(channel);
        else
            mObserver.onChannelStateUpdated(channel);
    }

    @Override
    public void messageChannelRemove(Mumble.ChannelRemove msg) {
        final Channel channel = mChannels.get(msg.getChannelId());
        if(channel != null && channel.getId() != 0) {
            mChannels.remove(channel.getId());
            Channel parent = channel.getParent();
            if(parent != null) {
                parent.removeSubchannel(channel);
            }
            mObserver.onChannelRemoved(channel);
        }
    }

    @Override
    public void messagePermissionQuery(Mumble.PermissionQuery msg) {
        if(msg.getFlush())
            for(Channel channel : mChannels.values())
                channel.setPermissions(0);

        final Channel channel = mChannels.get(msg.getChannelId());
        if(channel != null) {
            channel.setPermissions(msg.getPermissions());
            if(msg.getChannelId() == 0) // If we're provided permissions for the root channel, we'll apply these as our server permissions.
                mPermissions = channel.getPermissions();
            mObserver.onChannelPermissionsUpdated(channel);
        }
    }

    @Override
    public void messageUserState(Mumble.UserState msg) {
        User user = mUsers.get(msg.getSession());
        boolean newUser = false;

        User self = mUsers.get(mSession);

        if(user == null) {
            if(msg.hasName()) {
                user = new User(msg.getSession(), msg.getName());
                mUsers.put(msg.getSession(), user);
                newUser = true;
                // Add user to root channel by default. This works because for some reason, we don't get a channel ID when the user joins into root.
                Channel root = mChannels.get(0);
                if(root == null) root = createStubChannel(0);
                user.setChannel(root);
            }
            else
                return;
        }

        User actor = null;
        if(msg.hasActor())
            actor = getUser(msg.getActor());

        final User finalUser = user;

        if(msg.hasUserId()) {
            user.setUserId(msg.getUserId());
            // Restore local mute and ignore from history
            if (mLocalMuteHistory != null && mLocalMuteHistory.contains(user.getUserId())) {
                user.setLocalMuted(true);
            }
            if (mLocalIgnoreHistory != null && mLocalIgnoreHistory.contains(user.getUserId())) {
                user.setLocalIgnored(true);
            }
        }

        if(msg.hasHash()) {
            user.setHash(msg.getHash());

            /*
             * TODO:
             * - Check if user is local muted in database, if so re-mute them here
             * - Check if user is friend, if so indicate
             */
        }

        if(newUser)
            mLogger.logInfo(mContext.getString(R.string.chat_notify_connected, MessageFormatter.highlightString(user.getName())));

        if(msg.hasSelfDeaf() || msg.hasSelfMute()) {
            if(msg.hasSelfMute())
                user.setSelfMuted(msg.getSelfMute());
            if(msg.hasSelfDeaf())
                user.setSelfDeafened(msg.getSelfDeaf());

            if(self != null && user.getSession() != self.getSession() && user.getChannel().equals(self.getChannel())) {
                if(user.isSelfMuted() && user.isSelfDeafened())
                    mLogger.logInfo(mContext.getString(R.string.chat_notify_now_muted_deafened, MessageFormatter.highlightString(user.getName())));
                else if(user.isSelfMuted())
                    mLogger.logInfo(mContext.getString(R.string.chat_notify_now_muted, MessageFormatter.highlightString(user.getName())));
                else
                    mLogger.logInfo(mContext.getString(R.string.chat_notify_now_unmuted, MessageFormatter.highlightString(user.getName())));
            } else if(self != null && user.getSession() == self.getSession()) {
                if(user.isSelfMuted() && user.isSelfDeafened())
                    mLogger.logInfo(mContext.getString(R.string.chat_notify_muted_deafened, MessageFormatter.highlightString(user.getName())));
                else if(user.isSelfMuted())
                    mLogger.logInfo(mContext.getString(R.string.chat_notify_muted, MessageFormatter.highlightString(user.getName())));
                else
                    mLogger.logInfo(mContext.getString(R.string.chat_notify_unmuted, MessageFormatter.highlightString(user.getName())));
            }
        }

        if(msg.hasRecording()) {
            user.setRecording(msg.getRecording());

            if(self != null) {
                if(user.getSession() == self.getSession()) {
                    if(user.isRecording())
                        mLogger.logInfo(mContext.getString(R.string.chat_notify_self_recording_started));
                    else
                        mLogger.logInfo(mContext.getString(R.string.chat_notify_self_recording_stopped));
                } else {
                    Channel selfChannel = self.getChannel();
                    // If in a linked channel OR the same channel as the current user, notify the user about recording
                    if(selfChannel != null && (selfChannel.getLinks().contains(selfChannel) || selfChannel.equals(user.getChannel()))) {
                        if(user.isRecording())
                            mLogger.logInfo(mContext.getString(R.string.chat_notify_user_recording_started, MessageFormatter.highlightString(user.getName())));
                        else
                            mLogger.logInfo(mContext.getString(R.string.chat_notify_user_recording_stopped, MessageFormatter.highlightString(user.getName())));
                    }
                }
            }
        }

        if(msg.hasDeaf() || msg.hasMute() || msg.hasSuppress() || msg.hasPrioritySpeaker()) {
            if(msg.hasDeaf())
                user.setDeafened(msg.getDeaf());
            if(msg.hasMute())
                user.setMuted(msg.getMute());
            if(msg.hasSuppress())
                user.setSuppressed(msg.getSuppress());
            if(msg.hasPrioritySpeaker())
                user.setPrioritySpeaker(msg.getPrioritySpeaker());

//            if(self != null && ((user.getChannelId() == self.getChannelId()) || (actor.getSessionId() == self.getSessionId()))) {
//                if(user.getSessionId() == self.getSessionId()) {
//                    if(msg.hasMute() && msg.hasDeaf() && user.isMuted() && user.isDeafened()) {
//                        mLogger.logInfo();
//                    }
//                }
//            }

            /*
             * TODO: logging
             * Base this off of Messages.cpp:353
             */
        }

        if(msg.hasChannelId()) {
            final Channel channel = mChannels.get(msg.getChannelId());
            if(channel == null) {
                Log.e(Constants.TAG, "Invalid channel for user!");
                return; // TODO handle better
            }
            final Channel old = user.getChannel();

            user.setChannel(channel);

            if(!newUser) {
                mObserver.onUserJoinedChannel(finalUser, channel, old);
            }

            Channel sessionChannel = self != null ? self.getChannel() : null;

            // Notify the user of other users' current channel changes
            if (self != null && sessionChannel != null && old != null && !self.equals(user)) {
                // TODO add logic for other user moving self
                String actorString = actor != null ? MessageFormatter.highlightString(actor.getName()) : mContext.getString(R.string.the_server);
                if(!sessionChannel.equals(channel) && sessionChannel.equals(old)) {
                    // User moved out of self's channel
                    if(actor != null && actor.getSession() == user.getSession()) {
                        // By themselves
                        mLogger.logInfo(mContext.getString(R.string.chat_notify_user_left_channel, MessageFormatter.highlightString(user.getName()), MessageFormatter.highlightString(channel.getName())));
                    } else {
                        // By external actor
                        mLogger.logInfo(mContext.getString(R.string.chat_notify_user_left_channel_by, MessageFormatter.highlightString(user.getName()), MessageFormatter.highlightString(channel.getName()), actorString));
                    }
                } else if(sessionChannel.equals(channel)) {
                    // User moved into self's channel
                    if(actor != null && actor.getSession() == user.getSession()) {
                        // By themselves
                        mLogger.logInfo(mContext.getString(R.string.chat_notify_user_joined_channel, MessageFormatter.highlightString(user.getName())));
                    } else {
                        // By external actor
                        mLogger.logInfo(mContext.getString(R.string.chat_notify_user_joined_channel_by, MessageFormatter.highlightString(user.getName()), MessageFormatter.highlightString(old.getName()), actorString));
                    }
                }
            }

            /*
             * TODO: logging
             * Base this off of Messages.cpp:454
             */
        }

        if(msg.hasName())
            user.setName(msg.getName());

        if (msg.hasTextureHash()) {
            user.setTextureHash(msg.getTextureHash());
            user.setTexture(null); // clear cached texture when we receive a new hash
        }

        if (msg.hasTexture()) {
            // FIXME: is it reasonable to create a bitmap here? How expensive?
            user.setTexture(msg.getTexture());
        }

        if(msg.hasCommentHash())
            user.setCommentHash(msg.getCommentHash());

        if(msg.hasComment())
            user.setComment(msg.getComment());

        if (newUser)
            mObserver.onUserConnected(user);
        else
            mObserver.onUserStateUpdated(user);
    }

    @Override
    public void messageUserRemove(Mumble.UserRemove msg) {
        final User user = mUsers.get(msg.getSession());
        final User actor = mUsers.get(msg.getActor());
        final String reason = msg.getReason();

        if(msg.getSession() == mSession)
            mLogger.logWarning(mContext.getString(msg.getBan() ? R.string.chat_notify_kick_ban_self : R.string.chat_notify_kick_self, MessageFormatter.highlightString(actor.getName()), reason));
        else if(actor != null)
            mLogger.logWarning(mContext.getString(msg.getBan() ? R.string.chat_notify_kick_ban : R.string.chat_notify_kick, MessageFormatter.highlightString(actor.getName()), reason, MessageFormatter.highlightString(user.getName())));
        else
            mLogger.logInfo(mContext.getString(R.string.chat_notify_disconnected, MessageFormatter.highlightString(user.getName())));

        user.setChannel(null);
        mObserver.onUserRemoved(user, reason);
    }

    @Override
    public void messagePermissionDenied(final Mumble.PermissionDenied msg) {
        final String reason;
        switch (msg.getType()) {
            case ChannelName:
                reason = mContext.getString(R.string.deny_reason_channel_name);
                break;
            case TextTooLong:
                reason = mContext.getString(R.string.deny_reason_text_too_long);
                break;
            case TemporaryChannel:
                reason = mContext.getString(R.string.deny_reason_no_operation_temp);
                break;
            case MissingCertificate:
                reason = mContext.getString(R.string.deny_reason_no_certificate);
                break;
            case UserName:
                reason = mContext.getString(R.string.deny_reason_invalid_username);
                break;
            case ChannelFull:
                reason = mContext.getString(R.string.deny_reason_channel_full);
                break;
            case NestingLimit:
                reason = mContext.getString(R.string.deny_reason_channel_nesting);
                break;
            default:
                if(msg.hasReason()) reason = mContext.getString(R.string.deny_reason_other, msg.getReason());
                else reason = mContext.getString(R.string.perm_denied);

        }
        mObserver.onPermissionDenied(reason);
    }

    @Override
    public void messageTextMessage(Mumble.TextMessage msg) {
        User sender = mUsers.get(msg.getActor());

        if(sender != null && sender.isLocalIgnored())
            return;

        List<Channel> channels = new ArrayList<Channel>(msg.getChannelIdCount());
        for(int channelId : msg.getChannelIdList()) channels.add(mChannels.get(channelId));
        List<Channel> trees = new ArrayList<Channel>(msg.getTreeIdCount());
        for(int treeId : msg.getTreeIdList()) trees.add(mChannels.get(treeId));
        List<User> users = new ArrayList<User>(msg.getSessionCount());
        for(int userId : msg.getSessionList()) users.add(mUsers.get(userId));

        String actorName = sender != null ? sender.getName() : mContext.getString(R.string.server);

        Message message = new Message(msg.getActor(), actorName, channels, trees, users, msg.getMessage());
        mObserver.onMessageLogged(message);
    }

    @Override
    public void messageServerSync(Mumble.ServerSync msg) {
        mSession = msg.getSession();
        mLogger.logInfo(msg.getWelcomeText());
    }
}
