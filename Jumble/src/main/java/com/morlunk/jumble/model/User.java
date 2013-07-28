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

public class User implements Parcelable {

    private int mSession;
    private int mId;
    private String mName;
    private String mComment;
    private byte[] mCommentHash;
    private String mHash;

    private boolean mMuted;
    private boolean mDeafened;
    private boolean mSuppressed;

    private boolean mSelfMuted;
    private boolean mSelfDeafened;

    private boolean mPrioritySpeaker;
    private boolean mRecording;

    private int mChannel;

    // Local state
    private boolean mLocalMuted;
    private boolean mLocalIgnored;

    public static final Parcelable.Creator<User> CREATOR = new Parcelable.Creator<User>() {

        @Override
        public User createFromParcel(Parcel parcel) {
            return new User(parcel);
        }

        @Override
        public User[] newArray(int i) {
            return new User[i];
        }
    };

    public User() {

    }

    public User(int session, String name) {
        mSession = session;
        mName = name;
    }

    private User(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mSession);
        out.writeInt(mId);
        out.writeString(mName);
        out.writeString(mComment);
        out.writeInt(mCommentHash.length);
        out.writeByteArray(mCommentHash);
        out.writeString(mHash);
        out.writeValue(mMuted);
        out.writeValue(mDeafened);
        out.writeValue(mSuppressed);
        out.writeValue(mSelfMuted);
        out.writeValue(mSelfDeafened);
        out.writeValue(mPrioritySpeaker);
        out.writeValue(mRecording);
        out.writeInt(mChannel);
        out.writeValue(mLocalMuted);
    }

    public void readFromParcel(Parcel in) {
        mSession = in.readInt();
        mId = in.readInt();
        mName = in.readString();
        mComment = in.readString();
        mCommentHash = new byte[in.readInt()];
        in.readByteArray(mCommentHash);
        mHash = in.readString();
        mMuted = (Boolean)in.readValue(null);
        mDeafened = (Boolean)in.readValue(null);
        mSuppressed = (Boolean)in.readValue(null);
        mSelfMuted = (Boolean)in.readValue(null);
        mSelfDeafened = (Boolean)in.readValue(null);
        mPrioritySpeaker = (Boolean)in.readValue(null);
        mRecording = (Boolean)in.readValue(null);
        mChannel = in.readInt();
        mLocalMuted = (Boolean)in.readValue(null);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public int getSession() {
        return mSession;
    }

    public int getChannelId() {
        return mChannel;
    }

    public void setChannelId(int channel) {
        mChannel = channel;
    }

    public int getUserId() {
        return mId;
    }

    public void setUserId(int mId) {
        this.mId = mId;
    }

    public String getName() {
        return mName;
    }

    public void setName(String mName) {
        this.mName = mName;
    }

    public String getComment() {
        return mComment;
    }

    public void setComment(String mComment) {
        this.mComment = mComment;
    }

    public byte[] getCommentHash() {
        return mCommentHash;
    }

    public void setCommentHash(byte[] mCommentHash) {
        this.mCommentHash = mCommentHash;
    }

    public String getHash() {
        return mHash;
    }

    public void setHash(String mHash) {
        this.mHash = mHash;
    }

    public boolean isMuted() {
        return mMuted;
    }

    public void setMuted(boolean mMuted) {
        this.mMuted = mMuted;
    }

    public boolean isDeafened() {
        return mDeafened;
    }

    public void setDeafened(boolean mDeafened) {
        this.mDeafened = mDeafened;
    }

    public boolean isSuppressed() {
        return mSuppressed;
    }

    public void setSuppressed(boolean mSuppressed) {
        this.mSuppressed = mSuppressed;
    }

    public boolean isSelfMuted() {
        return mSelfMuted;
    }

    public void setSelfMuted(boolean mSelfMuted) {
        this.mSelfMuted = mSelfMuted;
    }

    public boolean isSelfDeafened() {
        return mSelfDeafened;
    }

    public void setSelfDeafened(boolean mSelfDeafened) {
        this.mSelfDeafened = mSelfDeafened;
    }

    public boolean isPrioritySpeaker() {
        return mPrioritySpeaker;
    }

    public void setPrioritySpeaker(boolean mPrioritySpeaker) {
        this.mPrioritySpeaker = mPrioritySpeaker;
    }

    public boolean isRecording() {
        return mRecording;
    }

    public void setRecording(boolean mRecording) {
        this.mRecording = mRecording;
    }

    public boolean isLocalMuted() {
        return mLocalMuted;
    }

    public void setLocalMuted(boolean mLocalMuted) {
        this.mLocalMuted = mLocalMuted;
    }

    public boolean isLocalIgnored() {
        return mLocalIgnored;
    }

    public void setLocalIgnored(boolean localIgnored) {
        mLocalIgnored = localIgnored;
    }
}
