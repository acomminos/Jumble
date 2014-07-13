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

import android.os.RemoteCallbackList;
import android.os.RemoteException;

import com.morlunk.jumble.IJumbleObserver;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.User;

/**
 * A composite wrapper around Jumble observers to easily broadcast to each observer.
 * Created by andrew on 12/07/14.
 */
public class JumbleCallbacks extends JumbleObserver.Stub {
    private final RemoteCallbackList<IJumbleObserver> mCallbacks;

    public JumbleCallbacks() {
        mCallbacks = new RemoteCallbackList<IJumbleObserver>();
    }

    public void registerObserver(IJumbleObserver observer) {
        mCallbacks.register(observer);
    }

    public void unregisterObserver(IJumbleObserver observer) {
        mCallbacks.unregister(observer);
    }

    public void kill() {
        mCallbacks.kill();
    }

    @Override
    public void onConnected() throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onConnected();
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onDisconnected() throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onDisconnected();
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onConnectionError(String message, boolean reconnecting) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onConnectionError(message, reconnecting);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onTLSHandshakeFailed(ParcelableByteArray cert) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onTLSHandshakeFailed(cert);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onChannelAdded(Channel channel) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onChannelAdded(channel);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onChannelStateUpdated(Channel channel) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onChannelStateUpdated(channel);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onChannelRemoved(Channel channel) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onChannelRemoved(channel);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onChannelPermissionsUpdated(Channel channel) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onChannelPermissionsUpdated(channel);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onUserConnected(User user) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onUserConnected(user);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onUserStateUpdated(User user) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onUserStateUpdated(user);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onUserTalkStateUpdated(User user) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onUserTalkStateUpdated(user);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onUserJoinedChannel(User user, Channel newChannel, Channel oldChannel) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onUserJoinedChannel(user, newChannel, oldChannel);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onUserRemoved(User user, String reason) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onUserRemoved(user, reason);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onPermissionDenied(String reason) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onPermissionDenied(reason);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onMessageLogged(Message message) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onMessageLogged(message);
        }
        mCallbacks.finishBroadcast();
    }
}
