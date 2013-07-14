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

import com.google.protobuf.InvalidProtocolBufferException;
import com.morlunk.jumble.protobuf.Mumble;

/**
 * Reads incoming protobuf TCP messages and performs the necessary action(s).
 * Designed to be subclassed at any level of the library, the default implementations do nothing.
 * Created by andrew on 24/06/13.
 */
public class JumbleMessageHandler {

    /**
     * Reroutes TCP messages into the various responder methods of this class.
     * @param data Raw TCP data of the message.
     * @param messageType The type of the message.
     * @throws InvalidProtocolBufferException Called if the messageType does not match the data.
     */
    public final void handleMessage(byte[] data, JumbleTCPMessageType messageType) throws InvalidProtocolBufferException {
        switch (messageType) {
            case Authenticate:
                messageAuthenticate(Mumble.Authenticate.parseFrom(data));
                break;
            case BanList:
                messageBanList(Mumble.BanList.parseFrom(data));
                break;
            case Reject:
                messageReject(Mumble.Reject.parseFrom(data));
                break;
            case ServerSync:
                messageServerSync(Mumble.ServerSync.parseFrom(data));
                break;
            case ServerConfig:
                messageServerConfig(Mumble.ServerConfig.parseFrom(data));
                break;
            case PermissionDenied:
                messagePermissionDenied(Mumble.PermissionDenied.parseFrom(data));
                break;
            case UDPTunnel:
                messageUDPTunnel(Mumble.UDPTunnel.parseFrom(data));
                break;
            case UserState:
                messageUserState(Mumble.UserState.parseFrom(data));
                break;
            case UserRemove:
                messageUserRemove(Mumble.UserRemove.parseFrom(data));
                break;
            case ChannelState:
                messageChannelState(Mumble.ChannelState.parseFrom(data));
                break;
            case ChannelRemove:
                messageChannelRemove(Mumble.ChannelRemove.parseFrom(data));
                break;
            case TextMessage:
                messageTextMessage(Mumble.TextMessage.parseFrom(data));
                break;
            case ACL:
                messageACL(Mumble.ACL.parseFrom(data));
                break;
            case QueryUsers:
                messageQueryUsers(Mumble.QueryUsers.parseFrom(data));
                break;
            case Ping:
                messagePing(Mumble.Ping.parseFrom(data));
                break;
            case CryptSetup:
                messageCryptSetup(Mumble.CryptSetup.parseFrom(data));
                break;
            case ContextAction:
                messageContextAction(Mumble.ContextAction.parseFrom(data));
                break;
            case ContextActionModify:
                Mumble.ContextActionModify actionModify = Mumble.ContextActionModify.parseFrom(data);
                if(actionModify.getOperation() == Mumble.ContextActionModify.Operation.Add)
                    messageContextActionModify(actionModify);
                else if(actionModify.getOperation() == Mumble.ContextActionModify.Operation.Remove)
                    messageRemoveContextAction(actionModify);
                break;
            case Version:
                messageVersion(Mumble.Version.parseFrom(data));
                break;
            case UserList:
                messageUserList(Mumble.UserList.parseFrom(data));
                break;
            case PermissionQuery:
                messagePermissionQuery(Mumble.PermissionQuery.parseFrom(data));
                break;
            case CodecVersion:
                messageCodecVersion(Mumble.CodecVersion.parseFrom(data));
                break;
            case UserStats:
                messageUserStats(Mumble.UserStats.parseFrom(data));
                break;
            case RequestBlob:
                messageRequestBlob(Mumble.RequestBlob.parseFrom(data));
                break;
            case SuggestConfig:
                messageSuggestConfig(Mumble.SuggestConfig.parseFrom(data));
                break;
        }
    }

    public void messageAuthenticate(Mumble.Authenticate msg) {};
    public void messageBanList(Mumble.BanList msg) {};
    public void messageReject(Mumble.Reject msg) {};
    public void messageServerSync(Mumble.ServerSync msg) {};
    public void messageServerConfig(Mumble.ServerConfig msg) {};
    public void messagePermissionDenied(Mumble.PermissionDenied msg) {};
    public void messageUDPTunnel(Mumble.UDPTunnel msg) {};
    public void messageUserState(Mumble.UserState msg) {};
    public void messageUserRemove(Mumble.UserRemove msg) {};
    public void messageChannelState(Mumble.ChannelState msg) {};
    public void messageChannelRemove(Mumble.ChannelRemove msg) {};
    public void messageTextMessage(Mumble.TextMessage msg) {};
    public void messageACL(Mumble.ACL msg) {};
    public void messageQueryUsers(Mumble.QueryUsers msg) {};
    public void messagePing(Mumble.Ping msg) {};
    public void messageCryptSetup(Mumble.CryptSetup msg) {};
    public void messageContextAction(Mumble.ContextAction msg) {};
    public void messageContextActionModify(Mumble.ContextActionModify msg) {};
    public void messageRemoveContextAction(Mumble.ContextActionModify msg) {};
    public void messageVersion(Mumble.Version msg) {};
    public void messageUserList(Mumble.UserList msg) {};
    public void messagePermissionQuery(Mumble.PermissionQuery msg) {};
    public void messageCodecVersion(Mumble.CodecVersion msg) {};
    public void messageUserStats(Mumble.UserStats msg) {};
    public void messageRequestBlob(Mumble.RequestBlob msg) {};
    public void messageSuggestConfig(Mumble.SuggestConfig msg) {};
}