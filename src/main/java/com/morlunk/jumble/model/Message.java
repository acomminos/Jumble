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
import android.text.format.Time;

import java.util.List;

/**
 * A class encapsulating a text message from a Mumble server.
 * NOTE: Always prefer using getActorName(). You CANNOT rely on getActor() to provide this info, as
 * we might keep message history for a user that is no longer in the {@link UserManager}.
 * Created by andrew on 03/12/13.
 */
public class Message implements Parcelable {

    public static final Parcelable.Creator<Message> CREATOR = new Parcelable.Creator<Message>() {

        @Override
        public Message createFromParcel(Parcel parcel) {
            return new Message(parcel);
        }

        @Override
        public Message[] newArray(int i) {
            return new Message[i];
        }
    };

    /**
     * The type of message this object represents.
     */
    public enum Type {
        INFO,
        WARNING,
        TEXT_MESSAGE
    }

    private Message.Type mType;
    private int mActor = -1;
    private String mActorName;
    private List<Channel> mChannels;
    private List<Channel> mTrees;
    private List<User> mUsers;
    private String mMessage;
    private Time mReceivedTime;

    public Message() {

    }

    public Message(Message.Type messageType, String message) {
        mType = messageType;
        mMessage = message;
        mReceivedTime = new Time();
        mReceivedTime.setToNow();
    }

    public Message(int actor, String actorName, List<Channel> channels, List<Channel> trees, List<User> users, String message) {
        this(Type.TEXT_MESSAGE, message);
        mActor = actor;
        mActorName = actorName;
        mChannels = channels;
        mTrees = trees;
        mUsers = users;
    }

    public Message(Parcel parcel) {
        readFromParcel(parcel);
    }

    public void readFromParcel(Parcel in) {
        mType = Type.values()[in.readInt()];
        mActor = in.readInt();
        mActorName = in.readString();
        mChannels = in.readArrayList(null);
        mTrees = in.readArrayList(null);
        mUsers = in.readArrayList(null);
        mMessage = in.readString();
        mReceivedTime = new Time();
        mReceivedTime.set(in.readLong());
    }

    public int getActor() {
        return mActor;
    }

    public String getActorName() {
        return mActorName;
    }

    public List<Channel> getChannels() {
        return mChannels;
    }

    public List<Channel> getTrees() {
        return mTrees;
    }

    public List<User> getUsers() {
        return mUsers;
    }

    public String getMessage() {
        return mMessage;
    }

    public Time getReceivedTime() {
        return mReceivedTime;
    }

    public Type getType() {
        return mType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType.ordinal());
        dest.writeInt(mActor);
        dest.writeString(mActorName);
        dest.writeList(mChannels);
        dest.writeList(mTrees);
        dest.writeList(mUsers);
        dest.writeString(mMessage);
        dest.writeLong(mReceivedTime.toMillis(false));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Message message = (Message) o;

        if (mActor != message.mActor) return false;
        if (!mActorName.equals(message.mActorName)) return false;
        if (!mChannels.equals(message.mChannels)) return false;
        if (!mMessage.equals(message.mMessage)) return false;
        if (!mReceivedTime.equals(message.mReceivedTime)) return false;
        if (!mTrees.equals(message.mTrees)) return false;
        if (mType != message.mType) return false;
        if (!mUsers.equals(message.mUsers)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = mType.hashCode();
        result = 31 * result + mActor;
        result = 31 * result + mActorName.hashCode();
        result = 31 * result + mChannels.hashCode();
        result = 31 * result + mTrees.hashCode();
        result = 31 * result + mUsers.hashCode();
        result = 31 * result + mMessage.hashCode();
        result = 31 * result + mReceivedTime.hashCode();
        return result;
    }
}
