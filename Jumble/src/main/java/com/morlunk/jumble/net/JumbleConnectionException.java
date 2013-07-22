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

package com.morlunk.jumble.net;

import com.morlunk.jumble.protobuf.Mumble;

/**
 * Created by andrew on 14/07/13.
 */
public class JumbleConnectionException extends Exception {
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

    public JumbleConnectionException(String message, Throwable e, boolean autoReconnect) {
        super(message, e);
        mAutoReconnect = autoReconnect;
    }

    public JumbleConnectionException(String message, boolean autoReconnect) {
        super(message);
        mAutoReconnect = autoReconnect;
    }

    public JumbleConnectionException(Throwable e, boolean autoReconnect) {
        super(e);
        mAutoReconnect = autoReconnect;
    }

    public JumbleConnectionException(Mumble.Reject reject) {
        super(reject.getReason());
        mReject = reject;
        mReason = JumbleDisconnectReason.REJECT;
        mAutoReconnect = false;
    }

    public JumbleConnectionException(Mumble.UserRemove userRemove) {
        super(userRemove.getReason());
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
