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

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.protobuf.ByteString;

public class User implements Parcelable {

    public static enum TalkState {
        TALKING,
        SHOUTING,
        PASSIVE,
        WHISPERING
    }

    private int mSession;
    private int mId = -1;
    private String mName;
    private String mComment;
    private ByteString mCommentHash;
    private Bitmap mTexture;
    private ByteString mTextureHash;
    private String mHash;

    private boolean mMuted;
    private boolean mDeafened;
    private boolean mSuppressed;

    private boolean mSelfMuted;
    private boolean mSelfDeafened;

    private boolean mPrioritySpeaker;
    private boolean mRecording;

    private int mChannel = -1;

    private TalkState mTalkState = TalkState.PASSIVE;

    // Local state
    private boolean mLocalMuted;
    private boolean mLocalIgnored;

    /** The number of samples normally available from the user. */
    private float mAverageAvailable;

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
        out.writeInt(mCommentHash.size());
        out.writeByteArray(mCommentHash.toByteArray());
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
        out.writeFloat(mAverageAvailable);
        out.writeString(mTalkState.toString());
    }

    public void readFromParcel(Parcel in) {
        mSession = in.readInt();
        mId = in.readInt();
        mName = in.readString();
        mComment = in.readString();
        byte[] commentHash = new byte[in.readInt()];
        in.readByteArray(commentHash);
        mCommentHash = ByteString.copyFrom(commentHash);
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
        mAverageAvailable = in.readFloat();
        mTalkState = TalkState.valueOf(in.readString());
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

    public ByteString getCommentHash() {
        return mCommentHash;
    }

    public void setCommentHash(ByteString commentHash) {
        mCommentHash = commentHash;
    }

    public Bitmap getTexture() {
        return mTexture;
    }

    public void setTexture(Bitmap texture) {
        mTexture = texture;
    }

    public ByteString getTextureHash() {
        return mTextureHash;
    }

    public void setTextureHash(ByteString textureHash) {
        mTextureHash = textureHash;
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

    public TalkState getTalkState() {
        return mTalkState;
    }

    public void setTalkState(TalkState mTalkState) {
        this.mTalkState = mTalkState;
    }

    public float getAverageAvailable() {
        return mAverageAvailable;
    }

    public void setAverageAvailable(float averageAvailable) {
        mAverageAvailable = averageAvailable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;

        if (mId != user.mId) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return mId;
    }
}
