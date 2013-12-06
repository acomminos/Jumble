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
    private List<Integer> mChannelIds;
    private List<Integer> mTreeIds;
    private List<Integer> mUserIds;
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

    public Message(int actor, List<Integer> channelIds, List<Integer> treeIds, List<Integer> userIds, String message) {
        this(Type.TEXT_MESSAGE, message);
        mActor = actor;
        mChannelIds = channelIds;
        mTreeIds = treeIds;
        mUserIds = userIds;
    }

    public Message(Parcel parcel) {
        readFromParcel(parcel);
    }

    public void readFromParcel(Parcel in) {
        mType = Type.values()[in.readInt()];
        mActor = in.readInt();
        mChannelIds = in.readArrayList(null);
        mTreeIds = in.readArrayList(null);
        mUserIds = in.readArrayList(null);
        mMessage = in.readString();
        mReceivedTime = new Time();
        mReceivedTime.set(in.readLong());
    }

    public int getActor() {
        return mActor;
    }

    public List<Integer> getChannelIds() {
        return mChannelIds;
    }

    public List<Integer> getTreeIds() {
        return mTreeIds;
    }

    public List<Integer> getUserIds() {
        return mUserIds;
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
        dest.writeList(mChannelIds);
        dest.writeList(mTreeIds);
        dest.writeList(mUserIds);
        dest.writeString(mMessage);
        dest.writeLong(mReceivedTime.toMillis(false));
    }
}
