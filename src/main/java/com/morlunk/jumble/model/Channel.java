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

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public final class Channel implements Parcelable, Comparable<Channel> {
    private int mId;
    private int mPosition;
    private int mLevel;
    private boolean mTemporary;
    private int mParent = -1;
    private String mName;
    private String mDescription;
    private byte[] mDescriptionHash;
    private List<Integer> mSubchannels = new ArrayList<Integer>();
    private List<Integer> mUsers = new ArrayList<Integer>();
    private List<Integer> mLinks = new ArrayList<Integer>();
    private int mUserCount;
    private int mPermissions;

    public static final Parcelable.Creator<Channel> CREATOR = new Parcelable.Creator<Channel>() {

        @Override
        public Channel createFromParcel(Parcel parcel) {
            return new Channel(parcel);
        }

        @Override
        public Channel[] newArray(int i) {
            return new Channel[i];
        }
    };

    public Channel() {
    }

    public Channel(int id, boolean temporary) {
        mId = id;
        mTemporary = temporary;
    }

    private Channel(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mId);
        out.writeInt(mPosition);
        out.writeInt(mLevel);
        out.writeValue(mTemporary);
        out.writeInt(mParent);
        out.writeString(mName);
        out.writeString(mDescription);
        out.writeInt(mDescriptionHash.length); // Store length so we can re-initialize byte buffer on read
        out.writeByteArray(mDescriptionHash);
        out.writeList(mSubchannels);
        out.writeList(mUsers);
        out.writeList(mLinks);
        out.writeInt(mUserCount);
        out.writeInt(mPermissions);
    }

    public void readFromParcel(Parcel in) {
        mId = in.readInt();
        mPosition = in.readInt();
        mLevel = in.readInt();
        mTemporary = (Boolean)in.readValue(null);
        mParent = in.readInt();
        mName = in.readString();
        mDescription = in.readString();
        mDescriptionHash = new byte[in.readInt()];
        in.readByteArray(mDescriptionHash);
        mSubchannels = in.readArrayList(null);
        mUsers = in.readArrayList(null);
        mLinks = in.readArrayList(null);
        mUserCount = in.readInt();
        mPermissions = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void addUser(int userId) {
        mUsers.add(userId);
    }

    public void removeUser(int userId) {
        mUsers.remove((Object)userId);
    }

    public List<Integer> getUsers() {
        return mUsers;
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

    public int getParent() {
        return mParent;
    }

    public void setParent(int mParent) {
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

    public List<Integer> getSubchannels() {
        return mSubchannels;
    }

    public void addSubchannel(int channel) {
        mSubchannels.add(channel);
    }

    public void removeSubchannel(int channelId) {
        mSubchannels.remove((Object)channelId);
    }

    public List<Integer> getLinks() {
        return mLinks;
    }

    public void addLink(int channelId) {
        mLinks.add(channelId);
    }

    public void removeLink(int channelId) {
        mLinks.remove((Object)channelId);
    }

    public void clearLinks() {
        mLinks.clear();
    }

    /**
     * @return The sum of users in this channel and its subchannels.
     */
    public int getSubchannelUserCount() {
        return mUserCount;
    }

    public void setSubchannelUserCount(int userCount) {
        mUserCount = userCount;
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
