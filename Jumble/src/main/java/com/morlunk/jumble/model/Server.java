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

public class Server implements Parcelable {

    private String mName;
    private String mHost;
    private int mPort;
    private String mUsername;
    private String mPassword;

    public static final Parcelable.Creator<Server> CREATOR = new Parcelable.Creator<Server>() {

        @Override
        public Server createFromParcel(Parcel parcel) {
            return new Server(parcel);
        }

        @Override
        public Server[] newArray(int i) {
            return new Server[i];
        }
    };

    public Server() {

    }

    private Server(Parcel in) {
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
