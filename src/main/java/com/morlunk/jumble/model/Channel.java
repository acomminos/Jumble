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

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Channel implements Parcelable {
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

    public void addSubchannel(int channelId) {
        mSubchannels.add(channelId);
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
}
