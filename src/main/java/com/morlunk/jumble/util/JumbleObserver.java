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

import android.os.RemoteException;

import com.morlunk.jumble.IJumbleObserver;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Message;
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
    public void onTLSHandshakeFailed(ParcelableByteArray cert) throws RemoteException {

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
    public void onChannelPermissionsUpdated(Channel channel) throws RemoteException {

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
    public void onUserRemoved(User user, String reason) throws RemoteException {

    }

    @Override
    public void onPermissionDenied(String reason) throws RemoteException {

    }

    @Override
    public void onMessageLogged(Message message) throws RemoteException {

    }

}
