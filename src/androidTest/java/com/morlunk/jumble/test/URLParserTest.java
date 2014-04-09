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

package com.morlunk.jumble.test;

import android.test.suitebuilder.annotation.MediumTest;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.util.MumbleURLParser;

import junit.framework.TestCase;

import java.net.MalformedURLException;

/**
 * Tests the Mumble URL parser.
 * Created by andrew on 03/03/14.
 */
public class URLParserTest extends TestCase {

    public void testURL() {
        String url = "mumble://server.com/";
        try {
            Server server = MumbleURLParser.parseURL(url);
            assertEquals(server.getHost(), "server.com");
            assertEquals(server.getPort(), Constants.DEFAULT_PORT);
        } catch (MalformedURLException e) {
            fail("Failed to parse URL.");
        }
    }

    public void testURLWithPort() {
        String url = "mumble://server.com:5000/";
        try {
            Server server = MumbleURLParser.parseURL(url);
            assertEquals(server.getHost(), "server.com");
            assertEquals(server.getPort(), 5000);
        } catch (MalformedURLException e) {
            fail("Failed to parse URL.");
        }
    }

    public void testURLWithUsername() {
        String url = "mumble://TestUser@server.com/";
        try {
            Server server = MumbleURLParser.parseURL(url);
            assertEquals(server.getHost(), "server.com");
            assertEquals(server.getUsername(), "TestUser");
            assertEquals(server.getPort(), Constants.DEFAULT_PORT);
        } catch (MalformedURLException e) {
            fail("Failed to parse URL.");
        }
    }

    public void testURLWithCredentials() {
        String url = "mumble://TestUser:mypassword@server.com:5000/";
        try {
            Server server = MumbleURLParser.parseURL(url);
            assertEquals(server.getHost(), "server.com");
            assertEquals(server.getUsername(), "TestUser");
            assertEquals(server.getPassword(), "mypassword");
            assertEquals(server.getPort(), 5000);
        } catch (MalformedURLException e) {
            fail("Failed to parse URL.");
        }
    }

    public void testInvalidScheme() {
        String url = "grumble://server.com/";
        try {
            MumbleURLParser.parseURL(url);
            fail("Successfully parsed bad scheme!");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

}
