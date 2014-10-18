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
import com.morlunk.jumble.IJumbleObserver;
import com.morlunk.jumble.R;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.protobuf.Mumble;
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

    private Comparator<Integer> mChannelComparator = new Comparator<Integer>() {
        @Override
        public int compare(Integer lhs, Integer rhs) {
            Channel clhs = getChannel(lhs);
            Channel crhs = getChannel(rhs);
            if(clhs.getPosition() != crhs.getPosition())
                return ((Integer)clhs.getPosition()).compareTo(crhs.getPosition());
            return clhs.getName().compareTo(crhs.getName());
        }
    };

    private Comparator<Integer> mUserComparator = new Comparator<Integer>() {
        @Override
        public int compare(Integer lhs, Integer rhs) {
            User ulhs = mUsers.get(lhs);
            User urhs = mUsers.get(rhs);
            return ulhs.getName().toLowerCase().compareTo(urhs.getName().toLowerCase());
        }
    };

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

    /**
     * Called after users are added or removed from a channel, this method will iterate up in the hierarchy to update parent channels' user counts. Intended to be pretty efficient.
     * @param channel The channel whose user count has been changed.
     * @param change The number of users who have been added or removed- positive if added, negative if removed.
     */
    private void changeSubchannelUsers(Channel channel, int change) {
        channel.setSubchannelUserCount(channel.getSubchannelUserCount() + change);
        int parent = channel.getParent();
        Channel parentChannel = mChannels.get(parent);
        if(parentChannel != null)
            changeSubchannelUsers(parentChannel, change);
    }

    /**
     * Sorts the users in the provided channel alphabetically.
     * @param channel The channel containing the users to sort.
     */
    private void sortUsers(Channel channel) {
        Collections.sort(channel.getUsers(), mUserComparator);
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
            Channel oldParent = mChannels.get(channel.getParent());
            channel.setParent(parent.getId());
            parent.addSubchannel(channel.getId());
            changeSubchannelUsers(parent, channel.getSubchannelUserCount());
            Collections.sort(parent.getSubchannels(), mChannelComparator); // Re-sort after subchannel addition
            if(oldParent != null) {
                oldParent.removeSubchannel(channel.getId());
                changeSubchannelUsers(oldParent, -channel.getSubchannelUserCount());
            }
        }

        if(msg.hasDescriptionHash())
            channel.setDescriptionHash(msg.getDescriptionHash().toByteArray());

        if(msg.hasDescription())
            channel.setDescription(msg.getDescription());

        if(msg.getLinksCount() > 0) {
            channel.clearLinks();
            for(int link : msg.getLinksList())
                channel.addLink(link);
        }

        if(msg.getLinksRemoveCount() > 0) {
            for(int link : msg.getLinksRemoveList())
                channel.removeLink(link);
        }

        if(msg.getLinksAddCount() > 0) {
            for(int link : msg.getLinksAddList())
                channel.addLink(link);
        }

        final Channel finalChannel = channel;
        try {
            if(newChannel)
                mObserver.onChannelAdded(finalChannel);
            else
                mObserver.onChannelStateUpdated(finalChannel);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void messageChannelRemove(Mumble.ChannelRemove msg) {
        final Channel channel = mChannels.get(msg.getChannelId());
        if(channel != null && channel.getId() != 0) {
            mChannels.remove(channel.getId());
            Channel parent = mChannels.get(channel.getParent());
            if(parent != null) {
                parent.removeSubchannel(msg.getChannelId());
                changeSubchannelUsers(parent, -channel.getUsers().size());
            }
            try {
                mObserver.onChannelRemoved(channel);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
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
            try {
                mObserver.onChannelPermissionsUpdated(channel);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
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
                user.setChannelId(0);
                root.addUser(user.getSession());
                root.setSubchannelUserCount(root.getSubchannelUserCount()+1);
                sortUsers(root);
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
            mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_connected, MessageFormatter.highlightString(user.getName())));

        if(msg.hasSelfDeaf() || msg.hasSelfMute()) {
            if(msg.hasSelfMute())
                user.setSelfMuted(msg.getSelfMute());
            if(msg.hasSelfDeaf())
                user.setSelfDeafened(msg.getSelfDeaf());

            if(self != null && user.getSession() != self.getSession() && (user.getChannelId() == self.getChannelId())) {
                if(user.isSelfMuted() && user.isSelfDeafened())
                    mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_now_muted_deafened, MessageFormatter.highlightString(user.getName())));
                else if(user.isSelfMuted())
                    mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_now_muted, MessageFormatter.highlightString(user.getName())));
                else
                    mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_now_unmuted, MessageFormatter.highlightString(user.getName())));
            } else if(self != null && user.getSession() == self.getSession()) {
                if(user.isSelfMuted() && user.isSelfDeafened())
                    mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_muted_deafened, MessageFormatter.highlightString(user.getName())));
                else if(user.isSelfMuted())
                    mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_muted, MessageFormatter.highlightString(user.getName())));
                else
                    mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_unmuted, MessageFormatter.highlightString(user.getName())));
            }
        }

        if(msg.hasRecording()) {
            user.setRecording(msg.getRecording());

            if(self != null) {
                if(user.getSession() == self.getSession()) {
                    if(user.isRecording())
                        mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_self_recording_started));
                    else
                        mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_self_recording_stopped));
                } else {
                    Channel selfChannel = mChannels.get(user.getChannelId());
                    // If in a linked channel OR the same channel as the current user, notify the user about recording
                    if(selfChannel != null && (selfChannel.getLinks().contains(user.getChannelId()) || self.getChannelId() == user.getChannelId())) {
                        if(user.isRecording())
                            mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_user_recording_started, MessageFormatter.highlightString(user.getName())));
                        else
                            mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_user_recording_stopped, MessageFormatter.highlightString(user.getName())));
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

//            if(self != null && ((user.getChannelId() == self.getChannelId()) || (actor.getSession() == self.getSession()))) {
//                if(user.getSession() == self.getSession()) {
//                    if(msg.hasMute() && msg.hasDeaf() && user.isMuted() && user.isDeafened()) {
//                        mLogger.log(Message.Type.INFO, );
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
            final Channel old = mChannels.get(user.getChannelId());

            user.setChannelId(msg.getChannelId());

            if(old != null) {
                old.removeUser(user.getSession());
                changeSubchannelUsers(old, -1);
            }

            channel.addUser(user.getSession());
            changeSubchannelUsers(channel, 1);
            sortUsers(channel);
            if(!newUser) {
                try {
                    mObserver.onUserJoinedChannel(finalUser, channel, old);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            if(old != null && self != null && user.getSession() != self.getSession()) {
                // TODO add logic for other user moving self
                String actorString = actor != null ? MessageFormatter.highlightString(actor.getName()) : mContext.getString(R.string.the_server);
                if(channel.getId() != self.getChannelId() && old.getId() == self.getChannelId()) {
                    // User moved out of self's channel
                    if(actor != null && actor.getSession() == user.getSession()) {
                        // By themselves
                        mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_user_left_channel, MessageFormatter.highlightString(user.getName()), MessageFormatter.highlightString(channel.getName())));
                    } else {
                        // By external actor
                        mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_user_left_channel_by, MessageFormatter.highlightString(user.getName()), MessageFormatter.highlightString(channel.getName()), actorString));
                    }
                } else if(channel.getId() == self.getChannelId()) {
                    // User moved into self's channel
                    if(actor != null && actor.getSession() == user.getSession()) {
                        // By themselves
                        mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_user_joined_channel, MessageFormatter.highlightString(user.getName())));
                    } else {
                        // By external actor
                        mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_user_joined_channel_by, MessageFormatter.highlightString(user.getName()), MessageFormatter.highlightString(old.getName()), actorString));
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

        if (msg.hasTextureHash())
            user.setTextureHash(msg.getTextureHash());

        if (msg.hasTexture()) {
            // FIXME: is it reasonable to create a bitmap here? How expensive?
            byte[] textureData = msg.getTexture().toByteArray();
            Bitmap texture = BitmapFactory.decodeByteArray(textureData, 0, textureData.length);
            user.setTexture(texture);
        }

        if(msg.hasCommentHash())
            user.setCommentHash(msg.getCommentHash());

        if(msg.hasComment())
            user.setComment(msg.getComment());

        final boolean finalNewUser = newUser;

        try {
            if(finalNewUser)
                mObserver.onUserConnected(finalUser);
            else
                mObserver.onUserStateUpdated(finalUser);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void messageUserRemove(Mumble.UserRemove msg) {
        final User user = mUsers.get(msg.getSession());
        final User actor = mUsers.get(msg.getActor());
        final String reason = msg.getReason();

        if(msg.getSession() == mSession)
            mLogger.log(Message.Type.WARNING, mContext.getString(msg.getBan() ? R.string.chat_notify_kick_ban_self : R.string.chat_notify_kick_self, MessageFormatter.highlightString(actor.getName()), reason));
        else if(actor != null)
            mLogger.log(Message.Type.INFO, mContext.getString(msg.getBan() ? R.string.chat_notify_kick_ban : R.string.chat_notify_kick, MessageFormatter.highlightString(actor.getName()), reason, MessageFormatter.highlightString(user.getName())));
        else
            mLogger.log(Message.Type.INFO, mContext.getString(R.string.chat_notify_disconnected, MessageFormatter.highlightString(user.getName())));

        Channel channel = mChannels.get(user.getChannelId());
        channel.removeUser(user.getSession());

       changeSubchannelUsers(channel, -1);
        try {
            mObserver.onUserRemoved(user, reason);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
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
        try {
            mObserver.onPermissionDenied(reason);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
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
        mLogger.log(message);
    }

    @Override
    public void messageServerSync(Mumble.ServerSync msg) {
        mSession = msg.getSession();
        mLogger.log(Message.Type.INFO, msg.getWelcomeText());
    }
}
