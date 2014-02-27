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
}
