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
import com.google.protobuf.Message;
import com.morlunk.jumble.protobuf.Mumble;

/**
 * Reads incoming protobuf TCP messages and performs the necessary action(s).
 * Designed to be subclassed at any level of the library, the default implementations do nothing.
 * Created by andrew on 24/06/13.
 */
public class JumbleMessageHandler {

    /**
     * Gets the protobuf message from the passed TCP data.
     * We isolate this so we can first parse the message and then inform all handlers. Saves processing power.
     * @param data Raw protobuf TCP data.
     * @param messageType Type of the message.
     * @return The parsed protobuf message.
     * @throws InvalidProtocolBufferException Called if the messageType does not match the data.
     */
    public static final Message getProtobufMessage(byte[] data, JumbleTCPMessageType messageType) throws InvalidProtocolBufferException {
        switch (messageType) {
            case Authenticate:
                return Mumble.Authenticate.parseFrom(data);
            case BanList:
                return Mumble.BanList.parseFrom(data);
            case Reject:
                return Mumble.Reject.parseFrom(data);
            case ServerSync:
                return Mumble.ServerSync.parseFrom(data);
            case ServerConfig:
                return Mumble.ServerConfig.parseFrom(data);
            case PermissionDenied:
                return Mumble.PermissionDenied.parseFrom(data);
            case UDPTunnel:
                return Mumble.UDPTunnel.parseFrom(data);
            case UserState:
                return Mumble.UserState.parseFrom(data);
            case UserRemove:
                return Mumble.UserRemove.parseFrom(data);
            case ChannelState:
                return Mumble.ChannelState.parseFrom(data);
            case ChannelRemove:
                return Mumble.ChannelRemove.parseFrom(data);
            case TextMessage:
                return Mumble.TextMessage.parseFrom(data);
            case ACL:
                return Mumble.ACL.parseFrom(data);
            case QueryUsers:
                return Mumble.QueryUsers.parseFrom(data);
            case Ping:
                return Mumble.Ping.parseFrom(data);
            case CryptSetup:
                return Mumble.CryptSetup.parseFrom(data);
            case ContextAction:
                return Mumble.ContextAction.parseFrom(data);
            case ContextActionModify:
                return Mumble.ContextActionModify.parseFrom(data);
            case Version:
                return Mumble.Version.parseFrom(data);
            case UserList:
                return Mumble.UserList.parseFrom(data);
            case PermissionQuery:
                return Mumble.PermissionQuery.parseFrom(data);
            case CodecVersion:
                return Mumble.CodecVersion.parseFrom(data);
            case UserStats:
                return Mumble.UserStats.parseFrom(data);
            case RequestBlob:
                return Mumble.RequestBlob.parseFrom(data);
            case SuggestConfig:
                return Mumble.SuggestConfig.parseFrom(data);
            default:
                throw new InvalidProtocolBufferException("Unknown TCP data passed.");
        }
    }


    /**
     * Reroutes TCP messages into the various responder methods of this class.
     * @param msg Protobuf message.
     * @param messageType The type of the message.
     */
    public final void handleTCPMessage(Message msg, JumbleTCPMessageType messageType) {
        switch (messageType) {
            case Authenticate:
                messageAuthenticate((Mumble.Authenticate) msg);
                break;
            case BanList:
                messageBanList((Mumble.BanList) msg);
                break;
            case Reject:
                messageReject((Mumble.Reject) msg);
                break;
            case ServerSync:
                messageServerSync((Mumble.ServerSync) msg);
                break;
            case ServerConfig:
                messageServerConfig((Mumble.ServerConfig) msg);
                break;
            case PermissionDenied:
                messagePermissionDenied((Mumble.PermissionDenied) msg);
                break;
            case UDPTunnel:
                messageUDPTunnel((Mumble.UDPTunnel) msg);
                break;
            case UserState:
                messageUserState((Mumble.UserState) msg);
                break;
            case UserRemove:
                messageUserRemove((Mumble.UserRemove) msg);
                break;
            case ChannelState:
                messageChannelState((Mumble.ChannelState) msg);
                break;
            case ChannelRemove:
                messageChannelRemove((Mumble.ChannelRemove) msg);
                break;
            case TextMessage:
                messageTextMessage((Mumble.TextMessage) msg);
                break;
            case ACL:
                messageACL((Mumble.ACL) msg);
                break;
            case QueryUsers:
                messageQueryUsers((Mumble.QueryUsers) msg);
                break;
            case Ping:
                messagePing((Mumble.Ping) msg);
                break;
            case CryptSetup:
                messageCryptSetup((Mumble.CryptSetup) msg);
                break;
            case ContextAction:
                messageContextAction((Mumble.ContextAction) msg);
                break;
            case ContextActionModify:
                Mumble.ContextActionModify actionModify = (Mumble.ContextActionModify) msg;
                if (actionModify.getOperation() == Mumble.ContextActionModify.Operation.Add)
                    messageContextActionModify(actionModify);
                else if (actionModify.getOperation() == Mumble.ContextActionModify.Operation.Remove)
                    messageRemoveContextAction(actionModify);
                break;
            case Version:
                messageVersion((Mumble.Version) msg);
                break;
            case UserList:
                messageUserList((Mumble.UserList) msg);
                break;
            case PermissionQuery:
                messagePermissionQuery((Mumble.PermissionQuery) msg);
                break;
            case CodecVersion:
                messageCodecVersion((Mumble.CodecVersion) msg);
                break;
            case UserStats:
                messageUserStats((Mumble.UserStats) msg);
                break;
            case RequestBlob:
                messageRequestBlob((Mumble.RequestBlob) msg);
                break;
            case SuggestConfig:
                messageSuggestConfig((Mumble.SuggestConfig) msg);
                break;
            case VoiceTarget:
                messageVoiceTarget((Mumble.VoiceTarget) msg);
                break;
        }
    }

    /**
     * Reroutes UDP messages into the various responder methods of this class.
     * @param data Raw UDP data of the message.
     * @param messageType The type of the message.
     */
    public final void handleUDPMessage(byte[] data, JumbleUDPMessageType messageType) {
        switch (messageType) {
            case UDPVoiceCELTAlpha:
                messageUDPCELTAlpha(data);
                break;
            case UDPPing:
                messageUDPPing(data);
                break;
            case UDPVoiceSpeex:
                messageUDPSpeex(data);
                break;
            case UDPVoiceCELTBeta:
                messageUDPCELTBeta(data);
                break;
            case UDPVoiceOpus:
                messageUDPOpus(data);
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
    public void messageVoiceTarget(Mumble.VoiceTarget msg) {};

    public void messageUDPCELTAlpha(byte[] data) {};
    public void messageUDPPing(byte[] data) {};
    public void messageUDPSpeex(byte[] data) {};
    public void messageUDPCELTBeta(byte[] data) {};
    public void messageUDPOpus(byte[] data) {};
}