/*
 * Copyright (C) 2014 Andrew Comminos
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

package com.morlunk.jumble.util;

import com.morlunk.jumble.net.JumbleConnection;
import com.morlunk.jumble.net.JumbleConnectionException;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.protocol.JumbleTCPMessageListener;
import com.morlunk.jumble.protocol.JumbleUDPMessageListener;

import java.security.cert.X509Certificate;

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
