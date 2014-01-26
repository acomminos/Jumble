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

package com.morlunk.jumble.protocol;

import android.os.RemoteException;
import android.util.Log;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.IJumbleObserver;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.R;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.util.MessageFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Created by andrew on 18/07/13.
 */
public class UserHandler extends ProtocolHandler {

    /**
     * Comparator to sort users alphabetically.
     */
    private Comparator<Integer> mUserComparator = new Comparator<Integer>() {
        @Override
        public int compare(Integer lhs, Integer rhs) {
            User ulhs = mUsers.get(lhs);
            User urhs = mUsers.get(rhs);
            return ulhs.getName().toLowerCase().compareTo(urhs.getName().toLowerCase());
        }
    };

    private HashMap<Integer, User> mUsers = new HashMap<Integer, User>();

    public UserHandler(JumbleService service) {
        super(service);
    }

    public User getUser(int id) {
        return mUsers.get(id);
    }

    public List<User> getUsers() {
        return new ArrayList<User>(mUsers.values());
    }

    public void clear() {
        mUsers.clear();
    }

    /**
     * Sorts the users in the provided channel alphabetically.
     * @param channel The channel containing the users to sort.
     */
    private void sortUsers(Channel channel) {
        Collections.sort(channel.getUsers(), mUserComparator);
    }

    @Override
    public void messageUserState(Mumble.UserState msg) {
        User user = mUsers.get(msg.getSession());
        boolean newUser = false;

        User self = mUsers.get(getService().getSession());

        if(user == null) {
            if(msg.hasName()) {
                user = new User(msg.getSession(), msg.getName());
                mUsers.put(msg.getSession(), user);
                newUser = true;
                // Add user to root channel by default. This works because for some reason, we don't get a channel ID when the user joins into root.
                Channel root = getService().getChannelHandler().getChannel(0);
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

        if(msg.hasUserId())
            user.setUserId(msg.getUserId());

        if(msg.hasHash()) {
            user.setHash(msg.getHash());

            /*
             * TODO:
             * - Check if user is local muted in database, if so re-mute them here
             * - Check if user is friend, if so indicate
             */
        }

        if(newUser)
            getService().logInfo(getService().getString(R.string.chat_notify_connected, MessageFormatter.highlightString(user.getName())));

        if(msg.hasSelfDeaf() || msg.hasSelfMute()) {
            if(msg.hasSelfMute())
                user.setSelfMuted(msg.getSelfMute());
            if(msg.hasSelfDeaf())
                user.setSelfDeafened(msg.getSelfDeaf());

            if(self != null && user.getSession() != self.getSession() && (user.getChannelId() == self.getChannelId())) {
                if(user.isSelfMuted() && user.isSelfDeafened())
                    getService().logInfo(getService().getString(R.string.chat_notify_now_muted_deafened, MessageFormatter.highlightString(user.getName())));
                else if(user.isSelfMuted())
                    getService().logInfo(getService().getString(R.string.chat_notify_now_muted, MessageFormatter.highlightString(user.getName())));
                else
                    getService().logInfo(getService().getString(R.string.chat_notify_now_unmuted, MessageFormatter.highlightString(user.getName())));
            } else if(self != null && user.getSession() == self.getSession()) {
                if(user.isSelfMuted() && user.isSelfDeafened())
                    getService().logInfo(getService().getString(R.string.chat_notify_muted_deafened, MessageFormatter.highlightString(user.getName())));
                else if(user.isSelfMuted())
                    getService().logInfo(getService().getString(R.string.chat_notify_muted, MessageFormatter.highlightString(user.getName())));
                else
                    getService().logInfo(getService().getString(R.string.chat_notify_unmuted, MessageFormatter.highlightString(user.getName())));
            }
        }

        if(msg.hasRecording()) {
            user.setRecording(msg.getRecording());

            if(self != null) {
                if(user.getSession() == self.getSession()) {
                    if(user.isRecording())
                        getService().logInfo(getService().getString(R.string.chat_notify_self_recording_started));
                    else
                        getService().logInfo(getService().getString(R.string.chat_notify_self_recording_stopped));
                } else {
                    Channel selfChannel = getService().getChannelHandler().getChannel(user.getChannelId());
                    // If in a linked channel OR the same channel as the current user, notify the user about recording
                    if(selfChannel != null && (selfChannel.getLinks().contains(user.getChannelId()) || self.getChannelId() == user.getChannelId())) {
                        if(user.isRecording())
                            getService().logInfo(getService().getString(R.string.chat_notify_user_recording_started, MessageFormatter.highlightString(user.getName())));
                        else
                            getService().logInfo(getService().getString(R.string.chat_notify_user_recording_stopped, MessageFormatter.highlightString(user.getName())));
                    }
                }
            }
        }

        if(msg.hasDeaf() || msg.hasMute() || msg.hasSuppress() || msg.hasPrioritySpeaker()) {
            if(msg.hasMute())
                user.setMuted(msg.getMute());
            if(msg.hasDeaf())
                user.setDeafened(msg.getDeaf());
            if(msg.hasPrioritySpeaker())
                user.setPrioritySpeaker(msg.getPrioritySpeaker());

//            if(self != null && ((user.getChannelId() == self.getChannelId()) || (actor.getSession() == self.getSession()))) {
//                if(user.getSession() == self.getSession()) {
//                    if(msg.hasMute() && msg.hasDeaf() && user.isMuted() && user.isDeafened()) {
//                        getService().logInfo();
//                    }
//                }
//            }

            /*
             * TODO: logging
             * Base this off of Messages.cpp:353
             */
        }

        if(msg.hasChannelId()) {
            final Channel channel = getService().getChannelHandler().getChannel(msg.getChannelId());
            if(channel == null) {
                Log.e(Constants.TAG, "Invalid channel for user!");
                return; // TODO handle better
            }
            final Channel old = getService().getChannelHandler().getChannel(user.getChannelId());

            user.setChannelId(msg.getChannelId());

            if(old != null) {
                old.removeUser(user.getSession());
                getService().getChannelHandler().changeSubchannelUsers(old, -1);
            }

            channel.addUser(user.getSession());
            getService().getChannelHandler().changeSubchannelUsers(channel, 1);
            sortUsers(channel);
            if(!newUser) {
                getService().notifyObservers(new JumbleService.ObserverRunnable() {
                    @Override
                    public void run(IJumbleObserver observer) throws RemoteException {
                        observer.onUserJoinedChannel(finalUser, channel, old);
                    }
                });
            }
            /*
             * TODO: logging
             * Base this off of Messages.cpp:454
             */
        }

        if(msg.hasName())
            user.setName(msg.getName());

        /*
         * Maybe support textures in the future here? I don't know. TODO?
         */

        if(msg.hasCommentHash())
            user.setCommentHash(msg.getCommentHash());

        if(msg.hasComment())
            user.setComment(msg.getComment());

        final boolean finalNewUser = newUser;

        getService().notifyObservers(new JumbleService.ObserverRunnable() {
            @Override
            public void run(IJumbleObserver observer) throws RemoteException {
                if(finalNewUser)
                    observer.onUserConnected(finalUser);
                else
                    observer.onUserStateUpdated(finalUser);
            }
        });
    }

    @Override
    public void messageUserRemove(Mumble.UserRemove msg) {
        final User user = getUser(msg.getSession());
        final User actor = getUser(msg.getActor());
        final String reason = msg.getReason();

        if(msg.getSession() == getService().getSession())
            getService().logWarning(getService().getString(msg.getBan() ? R.string.chat_notify_kick_ban_self : R.string.chat_notify_kick_self, MessageFormatter.highlightString(actor.getName()), reason));
        else if(actor != null)
            getService().logInfo(getService().getString(msg.getBan() ? R.string.chat_notify_kick_ban : R.string.chat_notify_kick, MessageFormatter.highlightString(actor.getName()), reason, MessageFormatter.highlightString(user.getName())));
        else
            getService().logInfo(getService().getString(R.string.chat_notify_disconnected, MessageFormatter.highlightString(user.getName())));

        Channel channel = getService().getChannelHandler().getChannel(user.getChannelId());
        channel.removeUser(user.getSession());

        getService().getChannelHandler().changeSubchannelUsers(channel, -1);
        getService().notifyObservers(new JumbleService.ObserverRunnable() {
            @Override
            public void run(IJumbleObserver observer) throws RemoteException {
                observer.onUserRemoved(user, reason);
            }
        });
    }

    @Override
    public void messagePermissionDenied(final Mumble.PermissionDenied msg) {
        final String reason;
        switch (msg.getType()) {
            case ChannelName:
                reason = getService().getString(R.string.deny_reason_channel_name);
                break;
            case TextTooLong:
                reason = getService().getString(R.string.deny_reason_text_too_long);
                break;
            case TemporaryChannel:
                reason = getService().getString(R.string.deny_reason_no_operation_temp);
                break;
            case MissingCertificate:
                reason = getService().getString(R.string.deny_reason_no_certificate);
                break;
            case UserName:
                reason = getService().getString(R.string.deny_reason_invalid_username);
                break;
            case ChannelFull:
                reason = getService().getString(R.string.deny_reason_channel_full);
                break;
            case NestingLimit:
                reason = getService().getString(R.string.deny_reason_channel_nesting);
                break;
            default:
                if(msg.hasReason()) reason = getService().getString(R.string.deny_reason_other, msg.getReason());
                else reason = getService().getString(R.string.perm_denied);

        }
        getService().notifyObservers(new JumbleService.ObserverRunnable() {
            @Override
            public void run(IJumbleObserver observer) throws RemoteException {
                observer.onPermissionDenied(reason);
            }
        });
    }
}
