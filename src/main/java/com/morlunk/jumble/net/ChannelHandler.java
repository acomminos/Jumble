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

import com.morlunk.jumble.IJumbleObserver;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.protobuf.Mumble;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by andrew on 18/07/13.
 */
public class ChannelHandler extends JumbleMessageHandler.Stub {

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

    private JumbleService mService;
    private Map<Integer, Channel> mChannels = new HashMap<Integer, Channel>();

    public ChannelHandler(JumbleService service) {
        mService = service;
    }

    public Channel getChannel(int id) {
        return mChannels.get(id);
    }

    public List<Channel> getChannels() {
        return new ArrayList<Channel>(mChannels.values());
    }

    /**
     * Called after users are added or removed from a channel, this method will iterate up in the hierarchy to update parent channels' user counts. Intended to be pretty efficient.
     * @param channel The channel whose user count has been changed.
     * @param change The number of users who have been added or removed- positive if added, negative if removed.
     */
    protected void changeSubchannelUsers(Channel channel, int change) {
        channel.setSubchannelUserCount(channel.getSubchannelUserCount() + change);
        int parent = channel.getParent();
        Channel parentChannel = mChannels.get(parent);
        if(parentChannel != null)
            changeSubchannelUsers(parentChannel, change);
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
        mService.notifyObservers(new JumbleService.ObserverRunnable() {
            @Override
            public void run(IJumbleObserver observer) throws RemoteException {
                if(newChannel)
                    observer.onChannelAdded(finalChannel);
                else
                    observer.onChannelStateUpdated(finalChannel);
            }
        });
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
            mService.notifyObservers(new JumbleService.ObserverRunnable() {
                @Override
                public void run(IJumbleObserver observer) throws RemoteException {
                    observer.onChannelRemoved(channel);
                }
            });
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
                mService.setPermissions(msg.getPermissions());
            mService.notifyObservers(new JumbleService.ObserverRunnable() {
                @Override
                public void run(IJumbleObserver observer) throws RemoteException {
                    observer.onChannelPermissionsUpdated(channel);
                }
            });
        }
    }
}
