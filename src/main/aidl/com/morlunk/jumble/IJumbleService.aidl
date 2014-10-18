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

import com.morlunk.jumble.model.User;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.IJumbleObserver;

interface IJumbleService {
    // Network
    void disconnect();
    boolean isConnected();
    boolean isConnecting();
    boolean isReconnecting();
    void cancelReconnect();
    /**
     * Gets the TCP latency, in nanoseconds.
     */
    long getTCPLatency();
    /**
     * Gets the UDP latency, in nanoseconds.
     */
    long getUDPLatency();
    int getMaxBandwidth();
    int getCurrentBandwidth();

    // Server information
    int getServerVersion();
    String getServerRelease();
    String getServerOSName();
    String getServerOSVersion();

    // Session and users
    int getSession();
    User getSessionUser();
    Channel getSessionChannel();
    Server getConnectedServer();
    User getUser(int id);
    Channel getChannel(int id);
    Channel getRootChannel();
    int getPermissions();
    List getMessageLog();
    void clearMessageLog();

    // Audio actions and settings
    boolean isTalking();
    void setTalkingState(boolean talking);
    int getTransmitMode();
    void setTransmitMode(int transmitMode);
    void setVADThreshold(float threshold);
    void setAmplitudeBoost(float boost);
    void setHalfDuplex(boolean enabled);
    int getCodec();

    // Bluetooth
    boolean isBluetoothAvailable();
    void setBluetoothEnabled(boolean enabled);

    // Server actions
    void joinChannel(int channel);
    void moveUserToChannel(int session, int channel);
    void createChannel(int parent, String name, String description, int position, boolean temporary);
    void sendAccessTokens(in List tokens);
    //void setTexture(byte[] texture);
    void requestBanList();
    void requestUserList();
    //void requestACL(int channel);
    void requestPermissions(int channel);
    void requestComment(int session);
    void requestAvatar(int session);
    void requestChannelDescription(int channel);
    void registerUser(int session);
    void kickBanUser(int session, String reason, boolean ban);
    Message sendUserTextMessage(int session, String message);
    Message sendChannelTextMessage(int channel, String message, boolean tree);
    void setUserComment(int session, String comment);
    void setPrioritySpeaker(int session, boolean priority);
    void removeChannel(int channel);
    //void addChannelLink(int channel, int link);
    //void requestChannelPermissions(int channel);
    void setMuteDeafState(int session, boolean mute, boolean deaf);
    void setSelfMuteDeafState(boolean mute, boolean deaf);
    //void announceRecordingState(boolean recording);

    // Observation
    void registerObserver(in IJumbleObserver observer);
    void unregisterObserver(in IJumbleObserver observer);
}