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

import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.util.ParcelableByteArray;

interface IJumbleObserver {
    // Connection
    void onConnected();
    void onDisconnected();
    void onConnectionError(String message, boolean reconnecting);

    // Authentication
    void onTLSHandshakeFailed(in ParcelableByteArray cert);

    // Channel
    void onChannelAdded(in Channel channel);
    void onChannelStateUpdated(in Channel channel);
    void onChannelRemoved(in Channel channel);
    void onChannelPermissionsUpdated(in Channel channel);

    // User
    void onUserConnected(in User user);
    void onUserStateUpdated(in User user);
    void onUserTalkStateUpdated(in User user);
    void onUserJoinedChannel(in User user, in Channel newChannel, in Channel oldChannel);
    void onUserRemoved(in User user, String reason);
    void onPermissionDenied(String reason);

    // Logging & Messaging
    void onMessageLogged(in Message message);
}
