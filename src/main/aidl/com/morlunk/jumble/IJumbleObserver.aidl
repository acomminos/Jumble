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

package com.morlunk.jumble;

import com.morlunk.jumble.model.IChannel;
import com.morlunk.jumble.model.IUser;
import com.morlunk.jumble.model.IMessage;
import com.morlunk.jumble.util.ParcelableByteArray;
import com.morlunk.jumble.util.JumbleException;

interface IJumbleObserver {
    // Connection
    void onConnected();
    void onConnecting();
    void onDisconnected(in JumbleException e);

    // Authentication
    void onTLSHandshakeFailed(in ParcelableByteArray cert);

    // Channel
    void onChannelAdded(in IChannel channel);
    void onChannelStateUpdated(in IChannel channel);
    void onChannelRemoved(in IChannel channel);
    void onChannelPermissionsUpdated(in IChannel channel);

    // User
    void onUserConnected(in IUser user);
    void onUserStateUpdated(in IUser user);
    void onUserTalkStateUpdated(in IUser user);
    void onUserJoinedChannel(in IUser user, in IChannel newChannel, in IChannel oldChannel);
    void onUserRemoved(in IUser user, String reason);
    void onPermissionDenied(String reason);

    // Logging & Messaging
    void onMessageLogged(in IMessage message);
    void onLogInfo(String message);
    void onLogWarning(String message);
    void onLogError(String message);
}
