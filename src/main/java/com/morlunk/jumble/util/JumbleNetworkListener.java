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

import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.protocol.JumbleTCPMessageListener;
import com.morlunk.jumble.protocol.JumbleUDPMessageListener;

/**
 * Created by andrew on 23/04/14.
 */
public class JumbleNetworkListener implements JumbleTCPMessageListener, JumbleUDPMessageListener {
    @Override
    public void messageAuthenticate(Mumble.Authenticate msg) {

    }

    @Override
    public void messageBanList(Mumble.BanList msg) {

    }

    @Override
    public void messageReject(Mumble.Reject msg) {

    }

    @Override
    public void messageServerSync(Mumble.ServerSync msg) {

    }

    @Override
    public void messageServerConfig(Mumble.ServerConfig msg) {

    }

    @Override
    public void messagePermissionDenied(Mumble.PermissionDenied msg) {

    }

    @Override
    public void messageUDPTunnel(Mumble.UDPTunnel msg) {

    }

    @Override
    public void messageUserState(Mumble.UserState msg) {

    }

    @Override
    public void messageUserRemove(Mumble.UserRemove msg) {

    }

    @Override
    public void messageChannelState(Mumble.ChannelState msg) {

    }

    @Override
    public void messageChannelRemove(Mumble.ChannelRemove msg) {

    }

    @Override
    public void messageTextMessage(Mumble.TextMessage msg) {

    }

    @Override
    public void messageACL(Mumble.ACL msg) {

    }

    @Override
    public void messageQueryUsers(Mumble.QueryUsers msg) {

    }

    @Override
    public void messagePing(Mumble.Ping msg) {

    }

    @Override
    public void messageCryptSetup(Mumble.CryptSetup msg) {

    }

    @Override
    public void messageContextAction(Mumble.ContextAction msg) {

    }

    @Override
    public void messageContextActionModify(Mumble.ContextActionModify msg) {

    }

    @Override
    public void messageRemoveContextAction(Mumble.ContextActionModify msg) {

    }

    @Override
    public void messageVersion(Mumble.Version msg) {

    }

    @Override
    public void messageUserList(Mumble.UserList msg) {

    }

    @Override
    public void messagePermissionQuery(Mumble.PermissionQuery msg) {

    }

    @Override
    public void messageCodecVersion(Mumble.CodecVersion msg) {

    }

    @Override
    public void messageUserStats(Mumble.UserStats msg) {

    }

    @Override
    public void messageRequestBlob(Mumble.RequestBlob msg) {

    }

    @Override
    public void messageSuggestConfig(Mumble.SuggestConfig msg) {

    }

    @Override
    public void messageVoiceTarget(Mumble.VoiceTarget msg) {

    }

    @Override
    public void messageUDPPing(byte[] data) {

    }

    @Override
    public void messageVoiceData(byte[] data, JumbleUDPMessageType messageType) {

    }
}
