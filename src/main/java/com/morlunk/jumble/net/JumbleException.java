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

package com.morlunk.jumble.net;

import com.morlunk.jumble.protobuf.Mumble;

/**
 * Created by andrew on 14/07/13.
 */
public class JumbleException extends Exception {
    public enum JumbleDisconnectReason {
        REJECT,
        USER_REMOVE,
        OTHER
    }

    private JumbleDisconnectReason mReason = JumbleDisconnectReason.OTHER;
    /** Whether the user will be allowed to auto-reconnect. */
    private boolean mAutoReconnect;
    /** Indicates that this exception was caused by a reject from the server. */
    private Mumble.Reject mReject;
    /** Indicates that this exception was caused by being kicked/banned from the server. */
    private Mumble.UserRemove mUserRemove;

    public JumbleException(String message, Throwable e, boolean autoReconnect) {
        super(message, e);
        mAutoReconnect = autoReconnect;
    }

    public JumbleException(String message, boolean autoReconnect) {
        super(message);
        mAutoReconnect = autoReconnect;
    }

    public JumbleException(Throwable e, boolean autoReconnect) {
        super(e);
        mAutoReconnect = autoReconnect;
    }

    public JumbleException(Mumble.Reject reject) {
        super("Reject: "+reject.getReason());
        mReject = reject;
        mReason = JumbleDisconnectReason.REJECT;
        mAutoReconnect = false;
    }

    public JumbleException(Mumble.UserRemove userRemove) {
        super((userRemove.getBan() ? "Banned: " : "Kicked: ")+userRemove.getReason());
        mUserRemove = userRemove;
        mReason = JumbleDisconnectReason.USER_REMOVE;
        mAutoReconnect = false;
    }

    public JumbleDisconnectReason getReason() {
        return mReason;
    }

    public boolean isAutoReconnectAllowed() {
        return mAutoReconnect;
    }

    public void setAutoReconnectAllowed(boolean autoReconnect) {
        this.mAutoReconnect = autoReconnect;
    }

    public Mumble.Reject getReject() {
        return mReject;
    }

    public Mumble.UserRemove getUserRemove() {
        return mUserRemove;
    }
}
