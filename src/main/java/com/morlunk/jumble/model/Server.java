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

public class Server implements Parcelable {

    private long mId;
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

    public Server(long id, String name, String host, int port, String username, String password) {
        mId = id;
        mName = name;
        mHost = host;
        mPort = port;
        mUsername = username;
        mPassword = password;
    }

    private Server(Parcel in) {
        readFromParcel(in);

    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(mId);
        parcel.writeString(mName);
        parcel.writeString(mHost);
        parcel.writeInt(mPort);
        parcel.writeString(mUsername);
        parcel.writeString(mPassword);
    }

    public void readFromParcel(Parcel in) {
        mId = in.readLong();
        mName = in.readString();
        mHost = in.readString();
        mPort = in.readInt();
        mUsername = in.readString();
        mPassword = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public long getId() {
        return mId;
    }

    public void setId(long id) {
        mId = id;
    }

    /**
     * Returns a user-defined name for the server, or the host if the user-defined name is not set.
     * @return A user readable name for the server.
     */
    public String getName() {
        return (mName != null && mName.length() > 0) ? mName : mHost;
    }

    public void setName(String mName) {
        this.mName = mName;
    }

    public String getHost() {
        return mHost;
    }

    public void setHost(String mHost) {
        this.mHost = mHost;
    }

    public int getPort() {
        return mPort;
    }

    public void setPort(int mPort) {
        this.mPort = mPort;
    }

    public String getUsername() {
        return mUsername;
    }

    public void setUsername(String mUsername) {
        this.mUsername = mUsername;
    }

    public String getPassword() {
        return mPassword;
    }

    public void setPassword(String mPassword) {
        this.mPassword = mPassword;
    }

    /**
     * Returns whether or not the server is stored in a database.
     * @return true if the server's ID is in the database.
     */
    public boolean isSaved() {
        return mId != -1;
    }
}
