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

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.IJumbleObserver;
import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.User;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Created by andrew on 18/07/13.
 */
public class ServiceTest extends ServiceTestCase<JumbleService> {

    private static final String HOST = "morlunk.com";
    private static final int PORT = Constants.DEFAULT_PORT;
    private static final String USERNAME = "Jumble-Unit-Tests";
    private static final String PASSWORD = "";
    private static final boolean USE_CERTIFICATE = true;
    private static final String CERTIFICATE_NAME = "jumble-test.p12";

    private static final String TEST_COMMENT = "I AM BENDER, PLEASE INSERT GURVIS<br><br>SESSION UUID %s";
    private static final String TEST_CHANNEL_NAME = "Jumble Test Channel %s";
    private static final String TEST_CHANNEL_MESSAGE = "Hello channel '%s'";
    private static final String TEST_USER_MESSAGE = "Hello user '%s'";
    private static final String TEST_KICK_MESSAGE = "Kicking '%s'";
    private static final int TEST_NETWORK_DELAY = 5000; // Time to make sure the connection does not terminate before packets are delivered
    private static final int TEST_OBSERVATION_DELAY = 5000; // Time between tests, used to verify that the desired result has been achieved (i.e. seeing if comment was set).

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
            public void onMessageReceived(String message) throws RemoteException {

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
        mBinder.disconnect();
        try {
            Thread.sleep(TEST_NETWORK_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        super.tearDown();
    }

    /**
     * Tests rapid movement between channels.
     */
    public void _testChannelHopping() throws RemoteException {
        List<Channel> channelList = mBinder.getChannelList();
        for(Channel channel : channelList) {
            mBinder.joinChannel(channel.getId());
        }
        try {
            Thread.sleep(TEST_OBSERVATION_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void _testSendMessages() throws RemoteException {
        List<Channel> channelList = mBinder.getChannelList();
        List<User> userList = mBinder.getUserList();
        for(Channel channel : channelList)
            mBinder.sendChannelTextMessage(channel.getId(), String.format(TEST_CHANNEL_MESSAGE, channel.getName()), false);
        for(User user : userList)
            mBinder.sendUserTextMessage(user.getSession(), String.format(TEST_USER_MESSAGE, user.getName()));
    }
    public void _testMuteAndDeafen() throws RemoteException {
        mBinder.setSelfMuteDeafState(true, true);
        try {
            Thread.sleep(TEST_OBSERVATION_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void _testSetComment() throws RemoteException {
        mBinder.setUserComment(mBinder.getSession(), String.format(TEST_COMMENT, UUID.randomUUID().toString()));
        try {
            Thread.sleep(TEST_OBSERVATION_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void _testKickUsers() throws RemoteException {
        List<User> userList = mBinder.getUserList();
        for(User user : userList)
            mBinder.kickBanUser(user.getSession(), String.format(TEST_KICK_MESSAGE, user.getName()), false);
    }

    public void _testCreateChannel() throws RemoteException {
        mBinder.createChannel(0, String.format(TEST_CHANNEL_NAME, UUID.randomUUID().toString()), "", 0, false);
    }

    /**
     * Gives the tester time to just observe the client from another client. Useful for testing something, I'm sure.
     */
    public void testSandbox() throws InterruptedException{
        Thread.sleep(500000);
    }
}
