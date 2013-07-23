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

import android.content.Intent;
import android.os.RemoteException;
import android.test.ServiceTestCase;
import android.util.Log;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.IJumbleObserver;
import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.User;

import java.io.InputStream;
import java.util.List;

/**
 * Created by andrew on 18/07/13.
 */
public class ServiceTest extends ServiceTestCase<JumbleService> {

    private static final String HOST = "pi.morlunk.com";
    private static final int PORT = Constants.DEFAULT_PORT;
    private static final String USERNAME = "Jumble-Unit-Tests";
    private static final String PASSWORD = "";
    private static final boolean USE_CERTIFICATE = true;
    private static final String CERTIFICATE_NAME = "jumble-test.p12";

    private static final String TEST_COMMENT = "BEEP BOOP I AM JUMBLEBOT";
    private static final int TEST_DELAY = 10000; // Time between tests, used to verify that the desired result has been achieved (i.e. seeing if comment was set).

    private IJumbleService mBinder;

    public ServiceTest() {
        super(JumbleService.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Intent intent = new Intent(JumbleService.ACTION_CONNECT);
        Server server = new Server("Test Server", HOST, PORT, USERNAME, PASSWORD);
        intent.putExtra(JumbleService.EXTRAS_SERVER, server);
        intent.putExtra(JumbleService.EXTRAS_FORCE_TCP, true); // Forcing TCP makes it easier to test.

        if(USE_CERTIFICATE) {
            InputStream cis = getContext().getAssets().open(CERTIFICATE_NAME);
            byte[] certificate = new byte[cis.available()];
            cis.read(certificate);
            cis.close();
            intent.putExtra(JumbleService.EXTRAS_CERTIFICATE, certificate);
        }

        startService(intent);
        mBinder = (IJumbleService) bindService(intent);

        final Object lock = new Object();

        IJumbleObserver.Stub observer = new IJumbleObserver.Stub() {
            @Override
            public void onConnected() throws RemoteException {
                synchronized (lock) {
                    lock.notify();
                }
            }

            @Override
            public void onDisconnected() throws RemoteException {
                synchronized (lock) {
                    lock.notify();
                }
            }

            @Override
            public void onConnectionError(String message, boolean reconnecting) throws RemoteException {
                fail(message);
            }

            @Override
            public void onChannelAdded(Channel channel) throws RemoteException {

            }

            @Override
            public void onChannelStateUpdated(Channel channel) throws RemoteException {

            }

            @Override
            public void onChannelRemoved(Channel channel) throws RemoteException {

            }

            @Override
            public void onUserConnected(User user) throws RemoteException {

            }

            @Override
            public void onUserStateUpdated(User user) throws RemoteException {

            }

            @Override
            public void onUserRemoved(User user) throws RemoteException {

            }

            @Override
            public void onMessageReceived(String message, User actor) throws RemoteException {

            }

            @Override
            public void onLogInfo(String message) throws RemoteException {

            }

            @Override
            public void onLogWarning(String message) throws RemoteException {

            }
        };

        mBinder.registerObserver(observer);

        synchronized (lock) {
            lock.wait();
        }

        mBinder.unregisterObserver(observer);

        assertTrue(mBinder.isConnected());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Thread.sleep(TEST_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mBinder.disconnect();
        super.tearDown();
    }

    /**
     * Tests rapid movement between channels.
     */
    public void testChannelHopping() throws RemoteException {
        List<Channel> channelList = mBinder.getChannelList();
        for(Channel channel : channelList) {
            mBinder.joinChannel(channel.getId());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void testMuteAndDeafen() throws RemoteException {
        mBinder.setSelfMuteDeafState(true, true);
    }

    public void testSetComment() throws RemoteException {
        mBinder.setUserComment(mBinder.getSession(), TEST_COMMENT);
    }
}
