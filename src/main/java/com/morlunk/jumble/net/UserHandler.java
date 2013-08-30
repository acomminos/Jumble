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
public class UserHandler extends JumbleMessageHandler.Stub {

    /**
     * Comparator to sort users alphabetically.
     */
    private Comparator<Integer> mUserComparator = new Comparator<Integer>() {
        @Override
        public int compare(Integer lhs, Integer rhs) {
            User ulhs = mUsers.get(lhs);
            User urhs = mUsers.get(rhs);
            return ulhs.getName().compareTo(urhs.getName());
        }
    };

    private JumbleService mService;
    private HashMap<Integer, User> mUsers = new HashMap<Integer, User>();

    public UserHandler(JumbleService service) {
        mService = service;
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

        User self = mUsers.get(mService.getSession());

        if(user == null) {
            if(msg.hasName()) {
                user = new User(msg.getSession(), msg.getName());
                mUsers.put(msg.getSession(), user);
                newUser = true;
                // Add user to root channel by default. This works because for some reason, we don't get a channel ID when the user joins into root.
                Channel root = mService.getChannelHandler().getChannel(0);
                user.setChannelId(0);
                root.addUser(user.getSession());
                root.setSubchannelUserCount(root.getSubchannelUserCount()+1);
                sortUsers(root);
            }
            else
                return;
        }

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
            mService.logInfo(mService.getString(R.string.chat_notify_connected, MessageFormatter.highlightString(user.getName())));

        if(msg.hasSelfDeaf() || msg.hasSelfMute()) {
            if(msg.hasSelfMute())
                user.setSelfMuted(msg.getSelfMute());
            if(msg.hasSelfDeaf())
                user.setSelfDeafened(msg.getSelfDeaf());

            if(self != null && user.getSession() != self.getSession() && (user.getChannelId() == self.getChannelId())) {
                if(user.isSelfMuted() && user.isSelfDeafened())
                    mService.logInfo(mService.getString(R.string.chat_notify_now_muted_deafened, MessageFormatter.highlightString(user.getName())));
                else if(user.isSelfMuted())
                    mService.logInfo(mService.getString(R.string.chat_notify_now_muted, MessageFormatter.highlightString(user.getName())));
                else
                    mService.logInfo(mService.getString(R.string.chat_notify_now_unmuted, MessageFormatter.highlightString(user.getName())));
            }
        }

        if(msg.hasRecording()) {
            user.setRecording(msg.getRecording());

            if(self != null) {
                if(user.getSession() == self.getSession()) {
                    if(user.isRecording())
                        mService.logInfo(mService.getString(R.string.chat_notify_self_recording_started));
                    else
                        mService.logInfo(mService.getString(R.string.chat_notify_self_recording_stopped));
                } else {
                    Channel selfChannel = mService.getChannelHandler().getChannel(user.getChannelId());
                    // If in a linked channel OR the same channel as the current user, notify the user about recording
                    if(selfChannel != null && (selfChannel.getLinks().contains(user.getChannelId()) || self.getChannelId() == user.getChannelId())) {
                        if(user.isRecording())
                            mService.logInfo(mService.getString(R.string.chat_notify_user_recording_started, MessageFormatter.highlightString(user.getName())));
                        else
                            mService.logInfo(mService.getString(R.string.chat_notify_user_recording_stopped, MessageFormatter.highlightString(user.getName())));
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
            /*
             * TODO: logging
             * Base this off of Messages.cpp:353
             */
        }

        if(msg.hasChannelId()) {
            final Channel channel = mService.getChannelHandler().getChannel(msg.getChannelId());
            if(channel == null) {
                Log.e(Constants.TAG, "Invalid channel for user!");
                return; // TODO handle better
            }
            final Channel old = mService.getChannelHandler().getChannel(user.getChannelId());

            user.setChannelId(msg.getChannelId());

            if(old != null) {
                old.removeUser(user.getSession());
                mService.getChannelHandler().changeSubchannelUsers(old, -1);
            }

            channel.addUser(user.getSession());
            mService.getChannelHandler().changeSubchannelUsers(channel, 1);
            sortUsers(channel);
            if(!newUser) {
                mService.notifyObservers(new JumbleService.ObserverRunnable() {
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

        mService.notifyObservers(new JumbleService.ObserverRunnable() {
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
        final String reason = msg.getReason();

        /*
         * TODO: logging
         * Base this off of Messages.cpp:511
         */

        if(msg.getSession() != mService.getSession()) {
            final User user = mService.getUserHandler().getUser(msg.getSession());
            Channel channel = mService.getChannelHandler().getChannel(user.getChannelId());
            channel.removeUser(user.getSession());

            mService.getChannelHandler().changeSubchannelUsers(channel, -1);
            mService.notifyObservers(new JumbleService.ObserverRunnable() {
                @Override
                public void run(IJumbleObserver observer) throws RemoteException {
                    observer.onUserRemoved(user, reason);
                }
            });
        }
    }
}
