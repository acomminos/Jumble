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

package com.morlunk.jumble.protocol;

import com.morlunk.jumble.protobuf.Mumble;

public interface JumbleMessageListener {
    public void messageAuthenticate(Mumble.Authenticate msg);
    public void messageBanList(Mumble.BanList msg);
    public void messageReject(Mumble.Reject msg);
    public void messageServerSync(Mumble.ServerSync msg);
    public void messageServerConfig(Mumble.ServerConfig msg);
    public void messagePermissionDenied(Mumble.PermissionDenied msg);
    public void messageUDPTunnel(Mumble.UDPTunnel msg);
    public void messageUserState(Mumble.UserState msg);
    public void messageUserRemove(Mumble.UserRemove msg);
    public void messageChannelState(Mumble.ChannelState msg);
    public void messageChannelRemove(Mumble.ChannelRemove msg);
    public void messageTextMessage(Mumble.TextMessage msg);
    public void messageACL(Mumble.ACL msg);
    public void messageQueryUsers(Mumble.QueryUsers msg);
    public void messagePing(Mumble.Ping msg);
    public void messageCryptSetup(Mumble.CryptSetup msg);
    public void messageContextAction(Mumble.ContextAction msg);
    public void messageContextActionModify(Mumble.ContextActionModify msg);
    public void messageRemoveContextAction(Mumble.ContextActionModify msg);
    public void messageVersion(Mumble.Version msg);
    public void messageUserList(Mumble.UserList msg);
    public void messagePermissionQuery(Mumble.PermissionQuery msg);
    public void messageCodecVersion(Mumble.CodecVersion msg);
    public void messageUserStats(Mumble.UserStats msg);
    public void messageRequestBlob(Mumble.RequestBlob msg);
    public void messageSuggestConfig(Mumble.SuggestConfig msg);
    public void messageVoiceTarget(Mumble.VoiceTarget msg);

    public void messageUDPPing(byte[] data);
    public void messageVoiceData(byte[] data);

    /**
     * Reads incoming protobuf TCP messages and performs the necessary action(s).
     * Designed to be subclassed at any level of the library, the default implementations do nothing.
     * Created by andrew on 24/06/13.
     */
    public static class Stub implements JumbleMessageListener {

        public void messageAuthenticate(Mumble.Authenticate msg) {}
        public void messageBanList(Mumble.BanList msg) {}
        public void messageReject(Mumble.Reject msg) {}
        public void messageServerSync(Mumble.ServerSync msg) {}
        public void messageServerConfig(Mumble.ServerConfig msg) {}
        public void messagePermissionDenied(Mumble.PermissionDenied msg) {}
        public void messageUDPTunnel(Mumble.UDPTunnel msg) {}
        public void messageUserState(Mumble.UserState msg) {}
        public void messageUserRemove(Mumble.UserRemove msg) {}
        public void messageChannelState(Mumble.ChannelState msg) {}
        public void messageChannelRemove(Mumble.ChannelRemove msg) {}
        public void messageTextMessage(Mumble.TextMessage msg) {}
        public void messageACL(Mumble.ACL msg) {}
        public void messageQueryUsers(Mumble.QueryUsers msg) {}
        public void messagePing(Mumble.Ping msg) {}
        public void messageCryptSetup(Mumble.CryptSetup msg) {}
        public void messageContextAction(Mumble.ContextAction msg) {}
        public void messageContextActionModify(Mumble.ContextActionModify msg) {}
        public void messageRemoveContextAction(Mumble.ContextActionModify msg) {}
        public void messageVersion(Mumble.Version msg) {}
        public void messageUserList(Mumble.UserList msg) {}
        public void messagePermissionQuery(Mumble.PermissionQuery msg) {}
        public void messageCodecVersion(Mumble.CodecVersion msg) {}
        public void messageUserStats(Mumble.UserStats msg) {}
        public void messageRequestBlob(Mumble.RequestBlob msg) {}
        public void messageSuggestConfig(Mumble.SuggestConfig msg) {}
        public void messageVoiceTarget(Mumble.VoiceTarget msg) {}
        public void messageUDPPing(byte[] data) {}
        public void messageVoiceData(byte[] data) {}
    }
}