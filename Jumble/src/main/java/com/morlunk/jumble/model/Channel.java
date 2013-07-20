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
import java.util.List;

public final class Channel implements Parcelable {
    private int mId;
    private int mPosition;
    private boolean mTemporary;
    private Channel mParent;
    private String mName;
    private String mDescription;
    private byte[] mDescriptionHash;
    private List<Integer> mSubchannels = new ArrayList<Integer>();
    private List<Integer> mUsers = new ArrayList<Integer>();

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

    private Channel(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mId);
        out.writeInt(mPosition);
        out.writeValue(mTemporary);
        out.writeParcelable(mParent, 0);
        out.writeString(mName);
        out.writeString(mDescription);
        out.writeInt(mDescriptionHash.length); // Store length so we can re-initialize byte buffer on read
        out.writeByteArray(mDescriptionHash);
        out.writeList(mSubchannels);
        out.writeList(mUsers);
    }

    public void readFromParcel(Parcel in) {
        mId = in.readInt();
        mPosition = in.readInt();
        mTemporary = (Boolean)in.readValue(Boolean.class.getClassLoader());
        mParent = in.readParcelable(Channel.class.getClassLoader());
        mName = in.readString();
        mDescription = in.readString();
        int descriptionHashLength = in.readInt();
        mDescriptionHash = new byte[descriptionHashLength];
        in.readByteArray(mDescriptionHash);
        mSubchannels = in.readArrayList(Integer.class.getClassLoader());
        mUsers = in.readArrayList(Integer.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void addUser(int userId) {
        mUsers.add(userId); // TODO sorting
    }

    public void removeUser(int userId) {
        mUsers.remove(userId);
    }
}
