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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Created by andrew on 18/07/13.
 */
public class ChannelHandler extends JumbleMessageHandler.Stub {

    /**
     * Channel comparator that first sorts by position, then alphabetical order.
     */
    class ChannelSortComparator implements Comparator<Integer> {

        @Override
        public int compare(Integer lhs, Integer rhs) {
            Channel clhs = mChannels.get(lhs);
            Channel crhs = mChannels.get(rhs);
            if(clhs.getPosition() > clhs.getPosition()) return 1;
            else if(clhs.getPosition() < clhs.getPosition()) return -1;
            return clhs.getName().compareTo(crhs.getName());
        }
    }

    private JumbleService mService;
    private HashMap<Integer, Channel> mChannels = new HashMap<Integer, Channel>();

    public ChannelHandler(JumbleService service) {
        mService = service;
    }

    public Channel getChannel(int id) {
        return mChannels.get(id);
    }

    public List<Channel> getChannels() {
        return new ArrayList<Channel>(mChannels.values());
    }

    @Override
    public void messageChannelState(Mumble.ChannelState msg) {
        if(!msg.hasChannelId())
            return;

        Channel channel = mChannels.get(msg.getChannelId());
        Channel parent = mChannels.get(msg.getParent());

        final boolean newChannel = channel == null;

        if(channel == null) {
            //if(msg.hasParent() && parent != null && msg.hasName()) {
                channel = new Channel(msg.getChannelId(), msg.getParent(), msg.getName(), msg.getTemporary());
                mChannels.put(msg.getChannelId(), channel);
            //}
            //else
            //    return;
        }

        if(parent != null)
            channel.setParent(parent.getId());

        if(msg.hasName())
            channel.setName(msg.getName());

        if(msg.hasDescriptionHash())
            channel.setDescriptionHash(msg.getDescriptionHash().toByteArray());

        if(msg.hasDescription())
            channel.setDescription(msg.getDescription());

        if(msg.hasPosition())
            channel.setPosition(msg.getPosition());

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
            mService.notifyObservers(new JumbleService.ObserverRunnable() {
                @Override
                public void run(IJumbleObserver observer) throws RemoteException {
                    observer.onChannelRemoved(channel);
                }
            });
        }
    }
}
