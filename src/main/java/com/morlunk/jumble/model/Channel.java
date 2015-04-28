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

package com.morlunk.jumble.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Channel extends IChannel.Stub implements Comparable<Channel> {
    private int mId;
    private int mPosition;
    private int mLevel;
    private boolean mTemporary;
    private Channel mParent;
    private String mName;
    private String mDescription;
    private byte[] mDescriptionHash;
    private List<Channel> mSubchannels;
    private List<User> mUsers;
    private List<Channel> mLinks;
    private int mPermissions;

    public Channel() {
        mSubchannels = new ArrayList<Channel>();
        mUsers = new ArrayList<User>();
        mLinks = new ArrayList<Channel>();
    }

    public Channel(int id, boolean temporary) {
        this();
        mId = id;
        mTemporary = temporary;
    }

    /**
     * @see User#setChannel(Channel)
     */
    protected void addUser(User user) {
        for (int i = 0; i < mUsers.size() + 1; i++) {
            User u = mUsers.get(i);
            if (u == null || user.compareTo(u) <= 0) {
                mUsers.add(i, user);
                return;
            }
        }
    }

    /**
     * @see User#setChannel(Channel)
     */
    protected void removeUser(User user) {
        mUsers.remove(user);
    }

    public List<User> getUsers() {
        return Collections.unmodifiableList(mUsers);
    }

    public int getId() {
        return mId;
    }

    public void setId(int mId) {
        this.mId = mId;
    }

    public int getPosition() {
        return mPosition;
    }

    public void setPosition(int mPosition) {
        this.mPosition = mPosition;
    }

    public boolean isTemporary() {
        return mTemporary;
    }

    public void setTemporary(boolean mTemporary) {
        this.mTemporary = mTemporary;
    }

    public Channel getParent() {
        return mParent;
    }

    public void setParent(Channel mParent) {
        this.mParent = mParent;
    }

    public String getName() {
        return mName;
    }

    public void setName(String mName) {
        this.mName = mName;
    }

    public String getDescription() {
        return mDescription;
    }

    public void setDescription(String mDescription) {
        this.mDescription = mDescription;
    }

    public byte[] getDescriptionHash() {
        return mDescriptionHash;
    }

    public void setDescriptionHash(byte[] mDescriptionHash) {
        this.mDescriptionHash = mDescriptionHash;
    }

    public List<Channel> getSubchannels() {
        return Collections.unmodifiableList(mSubchannels);
    }

    public void addSubchannel(Channel channel) {
        for (int i = 0; i < mSubchannels.size() + 1; i++) {
            Channel sc = mSubchannels.get(i);
            if (sc == null || channel.compareTo(sc) <= 0) {
                mSubchannels.add(i, channel);
                return;
            }
        }
    }

    public void removeSubchannel(Channel channel) {
        mSubchannels.remove(channel);
    }

    public List<Channel> getLinks() {
        return Collections.unmodifiableList(mLinks);
    }

    public void addLink(Channel channel) {
        for (int i = 0; i < mLinks.size() + 1; i++) {
            Channel sc = mLinks.get(i);
            if (sc == null || channel.compareTo(sc) <= 0) {
                mLinks.add(i, channel);
                return;
            }
        }
    }

    public void removeLink(Channel channel) {
        mLinks.remove(channel);
    }

    public void clearLinks() {
        mLinks.clear();
    }

    /**
     * Recursively fetches the subchannel user count.
     * FIXME: is it necessary to cache this?
     * @return The sum of users in this channel and its subchannel.
     */
    public int getSubchannelUserCount() {
        int userCount = mUsers.size();
        for (Channel subc : mSubchannels) {
            userCount += subc.getSubchannelUserCount();
        }
        return userCount;
    }

    public int getPermissions() {
        return mPermissions;
    }

    public void setPermissions(int permissions) {
        mPermissions = permissions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Channel channel = (Channel) o;

        if (mId != channel.mId) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return mId;
    }

    @Override
    public int compareTo(Channel another) {
        if(mPosition != another.getPosition())
            return ((Integer)mPosition).compareTo(another.getPosition());
        return mName.compareTo(another.getName());
    }
}
