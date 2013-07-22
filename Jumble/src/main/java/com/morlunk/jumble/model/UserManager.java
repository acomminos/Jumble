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

package com.morlunk.jumble.model;

import android.util.Log;
import android.util.SparseArray;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.net.JumbleMessageHandler;
import com.morlunk.jumble.protobuf.Mumble;

/**
 * Created by andrew on 18/07/13.
 */
public class UserManager extends JumbleMessageHandler.Stub {

    private JumbleService mService;
    private SparseArray<User> mUsers = new SparseArray<User>();

    public UserManager(JumbleService service) {
        mService = service;
    }

    public User getUser(int id) {
        return mUsers.get(id);
    }

    public void clear() {
        mUsers.clear();
    }

    @Override
    public void messageUserState(Mumble.UserState msg) {
        User user = mUsers.get(msg.getSession());
        boolean newUser = false;

        if(user == null) {
            if(msg.hasName()) {
                user = new User(msg.getSession(), msg.getName());
                mUsers.put(msg.getSession(), user);
                newUser = true;
            }
            else
                return;
        }

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

        if(msg.hasSelfDeaf() || msg.hasSelfMute()) {
            if(msg.hasSelfMute())
                user.setSelfMuted(msg.getSelfMute());
            if(msg.hasSelfDeaf())
                user.setSelfDeafened(msg.getSelfDeaf());

            /*
             * TODO: logging
             */
        }

        if(msg.hasRecording()) {
            user.setRecording(msg.getRecording());

            /*
             * TODO: logging
             */
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
             * Base this off of Messages.cpp:351
             */
        }

        if(msg.hasChannelId()) {
            Channel channel = mService.getChannelManager().getChannel(msg.getChannelId());
            if(channel == null) {
                Log.e(Constants.TAG, "Invalid channel for user!");
                channel = mService.getChannelManager().getChannel(0);
            }
            Channel old = mService.getChannelManager().getChannel(user.getChannelId());

            if(!channel.equals(old)) {
                // TODO move user
                //old.removeUser(user.getUserId());
                //channel.addUser(user.getUserId());
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
            user.setCommentHash(msg.getCommentHash().toByteArray());

        if(msg.hasComment())
            user.setComment(msg.getComment());
    }

    @Override
    public void messageUserRemove(Mumble.UserRemove msg) {
        String reason = msg.getReason();

        /*
         * TODO: logging
         * Base this off of Messages.cpp:511
         */

        if(msg.getSession() != mService.getSession()) {
            Channel channel = mService.getChannelManager().getChannel(msg.getSession());
            channel.removeUser(msg.getSession());
        }
    }
}
