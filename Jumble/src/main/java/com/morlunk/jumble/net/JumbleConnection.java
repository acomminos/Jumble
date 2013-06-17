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

import com.google.protobuf.Message;
import com.morlunk.jumble.model.Server;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;

public class JumbleConnection {

    public interface JumbleConnectionListener {
        public void onConnect();
        public void onDisconnect();
    }

    private Server mServer;

    private SSLSocket mTCPSocket;
    private DatagramSocket mUDPSocket;

    public JumbleConnection() {

    }

    public void connect(Server server) {
        mServer = server;

    }

    public void disconnect() {
        try {
            mTCPSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mUDPSocket.close();

        mServer = null;
    }

    public void sendMessage(Message message) throws IOException {
        byte[] messageBytes = message.toByteArray();
        DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length);
        mUDPSocket.send(packet);
    }

}
