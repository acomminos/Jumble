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

package com.morlunk.jumble.test;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

import com.morlunk.jumble.JumbleParams;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.net.JumbleConnection;
import com.morlunk.jumble.net.JumbleConnectionException;
import com.morlunk.jumble.net.JumbleTCPMessageType;
import com.morlunk.jumble.net.JumbleUDPMessageType;

import java.util.UUID;

/**
 * Created by andrew on 09/07/13.
 */
public class ConnectionTest extends AndroidTestCase {

    /**
     * Standard host testing specifications:
     * - Passwordless
     * - Does not require certificate
     * - Runs protocol version 1.2.4
     */
    private static final String HOST = "morlunk.com";
    private static final int HOST_PORT = 64738;

    @LargeTest
    public void testConnection() throws JumbleConnectionException, InterruptedException {
        JumbleParams params = new JumbleParams();

        final Object lock = new Object();

        params.server = new Server("Test Server", HOST, HOST_PORT, "Jumble-Test-" + UUID.randomUUID().toString(), "");
        JumbleConnection.JumbleConnectionListener connectionListener = new JumbleConnection.JumbleConnectionListener() {
            @Override
            public void onConnectionEstablished() {
                synchronized (lock) {
                    lock.notify();
                }
            }

            @Override
            public void onConnectionDisconnected() {
                synchronized (lock) {
                    lock.notify();
                }
            }

            @Override
            public void onConnectionError(JumbleConnectionException e) {
                switch (e.getReason()) {
                    case REJECT:
                        fail("Reject: "+e.getReject().getReason());
                    case USER_REMOVE:
                        fail("UserRemove: "+e.getUserRemove().getReason());
                    case OTHER:
                        fail("Other: "+e.getMessage());
                }
            }

            @Override
            public void onTCPDataReceived(byte[] data, JumbleTCPMessageType messageType) {
            }

            @Override
            public void onUDPDataReceived(byte[] data, JumbleUDPMessageType dataType) {

            }
        };

        JumbleConnection connection = new JumbleConnection(getContext(), connectionListener, params);
        connection.connect();

        synchronized (lock) {
            lock.wait();
        }

        connection.disconnect();
    }
}
