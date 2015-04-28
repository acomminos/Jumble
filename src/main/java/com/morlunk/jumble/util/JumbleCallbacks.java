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
import com.morlunk.jumble.model.IChannel;
import com.morlunk.jumble.model.IMessage;
import com.morlunk.jumble.model.IUser;

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
    public void onConnecting() throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onConnecting();
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onDisconnected(JumbleException e) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onDisconnected(e);
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
    public void onChannelAdded(IChannel channel) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onChannelAdded(channel);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onChannelStateUpdated(IChannel channel) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onChannelStateUpdated(channel);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onChannelRemoved(IChannel channel) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onChannelRemoved(channel);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onChannelPermissionsUpdated(IChannel channel) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onChannelPermissionsUpdated(channel);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onUserConnected(IUser user) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onUserConnected(user);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onUserStateUpdated(IUser user) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onUserStateUpdated(user);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onUserTalkStateUpdated(IUser user) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onUserTalkStateUpdated(user);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onUserJoinedChannel(IUser user, IChannel newChannel, IChannel oldChannel) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onUserJoinedChannel(user, newChannel, oldChannel);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onUserRemoved(IUser user, String reason) throws RemoteException {
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
    public void onMessageLogged(IMessage message) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onMessageLogged(message);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onLogInfo(String message) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onLogInfo(message);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onLogWarning(String message) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onLogWarning(message);
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onLogError(String message) throws RemoteException {
        int i = mCallbacks.beginBroadcast();
        while(i > 0) {
            i--;
            mCallbacks.getBroadcastItem(i).onLogError(message);
        }
        mCallbacks.finishBroadcast();
    }
}
