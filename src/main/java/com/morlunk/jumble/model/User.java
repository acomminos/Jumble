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

import com.google.protobuf.ByteString;

public class User implements IUser, Comparable<User> {

    private int mSession;
    private int mId = -1;
    private String mName;
    private String mComment;
    private ByteString mCommentHash;
    private ByteString mTexture;
    private ByteString mTextureHash;
    private String mHash;

    private boolean mMuted;
    private boolean mDeafened;
    private boolean mSuppressed;

    private boolean mSelfMuted;
    private boolean mSelfDeafened;

    private boolean mPrioritySpeaker;
    private boolean mRecording;

    private Channel mChannel;

    private TalkState mTalkState = TalkState.PASSIVE;

    // Local state
    private boolean mLocalMuted;
    private boolean mLocalIgnored;

    /** The number of samples normally available from the user. */
    private float mAverageAvailable;

    public User() {

    }

    public User(int session, String name) {
        mSession = session;
        mName = name;
    }

    @Override
    public int getSession() {
        return mSession;
    }

    @Override
    public Channel getChannel() {
        return mChannel;
    }

    /**
     * Changes the user's channel, removing them from their last channel (if set).
     * @param channel The user's new channel.
     */
    public void setChannel(Channel channel) {
        if (mChannel != null)
            mChannel.removeUser(this);

        mChannel = channel;

        if (mChannel != null)
            mChannel.addUser(this);
    }

    @Override
    public int getUserId() {
        return mId;
    }

    public void setUserId(int mId) {
        this.mId = mId;
    }

    @Override
    public String getName() {
        return mName;
    }

    public void setName(String mName) {
        this.mName = mName;
    }

    @Override
    public String getComment() {
        return mComment;
    }

    public void setComment(String mComment) {
        this.mComment = mComment;
    }

    @Override
    public byte[] getCommentHash() {
        return mCommentHash != null ? mCommentHash.toByteArray() : null;
    }

    public void setCommentHash(ByteString commentHash) {
        mCommentHash = commentHash;
    }

    @Override
    public byte[] getTexture() {
        return mTexture != null ? mTexture.toByteArray() : null;
    }

    public void setTexture(ByteString texture) {
        mTexture = texture;
    }

    @Override
    public byte[] getTextureHash() {
        return mTextureHash != null ? mTextureHash.toByteArray() : null;
    }

    public void setTextureHash(ByteString textureHash) {
        mTextureHash = textureHash;
    }

    @Override
    public String getHash() {
        return mHash;
    }

    public void setHash(String mHash) {
        this.mHash = mHash;
    }

    @Override
    public boolean isMuted() {
        return mMuted;
    }

    public void setMuted(boolean mMuted) {
        this.mMuted = mMuted;
    }

    @Override
    public boolean isDeafened() {
        return mDeafened;
    }

    public void setDeafened(boolean mDeafened) {
        this.mDeafened = mDeafened;
    }

    @Override
    public boolean isSuppressed() {
        return mSuppressed;
    }

    public void setSuppressed(boolean mSuppressed) {
        this.mSuppressed = mSuppressed;
    }

    @Override
    public boolean isSelfMuted() {
        return mSelfMuted;
    }

    public void setSelfMuted(boolean mSelfMuted) {
        this.mSelfMuted = mSelfMuted;
    }

    @Override
    public boolean isSelfDeafened() {
        return mSelfDeafened;
    }

    public void setSelfDeafened(boolean mSelfDeafened) {
        this.mSelfDeafened = mSelfDeafened;
    }

    @Override
    public boolean isPrioritySpeaker() {
        return mPrioritySpeaker;
    }

    public void setPrioritySpeaker(boolean mPrioritySpeaker) {
        this.mPrioritySpeaker = mPrioritySpeaker;
    }

    @Override
    public boolean isRecording() {
        return mRecording;
    }

    public void setRecording(boolean mRecording) {
        this.mRecording = mRecording;
    }

    @Override
    public boolean isLocalMuted() {
        return mLocalMuted;
    }

    public void setLocalMuted(boolean mLocalMuted) {
        this.mLocalMuted = mLocalMuted;
    }

    @Override
    public boolean isLocalIgnored() {
        return mLocalIgnored;
    }

    public void setLocalIgnored(boolean localIgnored) {
        mLocalIgnored = localIgnored;
    }

    @Override
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

        if (mSession != user.mSession) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return mId;
    }

    @Override
    public int compareTo(User another) {
        return getName().toLowerCase().compareTo(another.getName().toLowerCase());
    }
}
