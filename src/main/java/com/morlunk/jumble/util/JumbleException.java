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

package com.morlunk.jumble.util;

import android.os.Parcel;
import android.os.Parcelable;

import com.morlunk.jumble.protobuf.Mumble;

/**
 * Created by andrew on 14/07/13.
 */
public class JumbleException extends Exception implements Parcelable {

    public static final Creator<JumbleException> CREATOR = new Creator<JumbleException>() {
        @Override
        public JumbleException createFromParcel(Parcel source) {
            return new JumbleException(source);
        }

        @Override
        public JumbleException[] newArray(int size) {
            return new JumbleException[size];
        }
    };

    private JumbleDisconnectReason mReason;
    /** Indicates that this exception was caused by a reject from the server. */
    private Mumble.Reject mReject;
    /** Indicates that this exception was caused by being kicked/banned from the server. */
    private Mumble.UserRemove mUserRemove;

    public JumbleException(String message, Throwable e, JumbleDisconnectReason reason) {
        super(message, e);
        mReason = reason;
    }

    public JumbleException(String message, JumbleDisconnectReason reason) {
        super(message);
        mReason = reason;
    }

    public JumbleException(Throwable e, JumbleDisconnectReason reason) {
        super(e);
        mReason = reason;
    }

    public JumbleException(Mumble.Reject reject) {
        super("Reject: "+reject.getReason());
        mReject = reject;
        mReason = JumbleDisconnectReason.REJECT;
    }

    public JumbleException(Mumble.UserRemove userRemove) {
        super((userRemove.getBan() ? "Banned: " : "Kicked: ")+userRemove.getReason());
        mUserRemove = userRemove;
        mReason = JumbleDisconnectReason.USER_REMOVE;
    }

    private JumbleException(Parcel in) {
        super(in.readString(), (Throwable) in.readSerializable());
        mReason = JumbleDisconnectReason.values()[in.readInt()];
        mReject = (Mumble.Reject) in.readSerializable();
        mUserRemove = (Mumble.UserRemove) in.readSerializable();
    }

    public JumbleDisconnectReason getReason() {
        return mReason;
    }

    public Mumble.Reject getReject() {
        return mReject;
    }

    public Mumble.UserRemove getUserRemove() {
        return mUserRemove;
    }
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getMessage());
        dest.writeSerializable(getCause());
        dest.writeInt(mReason.ordinal());
        dest.writeSerializable(mReject);
        dest.writeSerializable(mUserRemove);
    }

    public enum JumbleDisconnectReason {
        REJECT,
        USER_REMOVE,
        CONNECTION_ERROR,
        OTHER_ERROR
    }
}
