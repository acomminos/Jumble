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

    private Channel mChannel;

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

    private User(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {

    }

    public void readFromParcel(Parcel in) {
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
