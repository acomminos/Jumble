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

import android.os.RemoteException;

import com.morlunk.jumble.IJumbleObserver;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.User;

/**
 * Stub class for Jumble service observation.
 * Created by andrew on 31/07/13.
 */
public class JumbleObserver extends IJumbleObserver.Stub {
    @Override
    public void onConnected() throws RemoteException {

    }

    @Override
    public void onDisconnected() throws RemoteException {

    }

    @Override
    public void onConnectionError(String message, boolean reconnecting) throws RemoteException {

    }

    @Override
    public void onChannelAdded(Channel channel) throws RemoteException {

    }

    @Override
    public void onChannelStateUpdated(Channel channel) throws RemoteException {

    }

    @Override
    public void onChannelRemoved(Channel channel) throws RemoteException {

    }

    @Override
    public void onUserConnected(User user) throws RemoteException {

    }

    @Override
    public void onUserStateUpdated(User user) throws RemoteException {

    }

    @Override
    public void onUserTalkStateUpdated(User user) throws RemoteException {
        
    }

    @Override
    public void onUserJoinedChannel(User user, Channel newChannel, Channel oldChannel) throws RemoteException {

    }

    @Override
    public void onUserRemoved(User user) throws RemoteException {

    }

    @Override
    public void onLogInfo(String message) throws RemoteException {

    }

    @Override
    public void onLogWarning(String message) throws RemoteException {

    }

    @Override
    public void onMessageReceived(String message) throws RemoteException {

    }
}
