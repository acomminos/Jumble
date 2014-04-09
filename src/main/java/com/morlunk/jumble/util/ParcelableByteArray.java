/*
 * Copyright (C) 2014 Andrew Comminos
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

package com.morlunk.jumble.util;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by andrew on 05/04/14.
 */
public class ParcelableByteArray implements Parcelable {

    private byte[] mByteArray;

    public ParcelableByteArray(byte[] array) {
        mByteArray = array;
    }

    private ParcelableByteArray(Parcel in) {
        int length = in.readInt();
        mByteArray = new byte[length];
        in.readByteArray(mByteArray);
    }

    public byte[] getBytes() {
        return mByteArray;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mByteArray.length);
        dest.writeByteArray(mByteArray);
    }

    public static final Creator<ParcelableByteArray> CREATOR = new Creator<ParcelableByteArray>() {

        @Override
        public ParcelableByteArray createFromParcel(Parcel source) {
            return new ParcelableByteArray(source);
        }

        @Override
        public ParcelableByteArray[] newArray(int size) {
            return new ParcelableByteArray[size];
        }
    };
}
