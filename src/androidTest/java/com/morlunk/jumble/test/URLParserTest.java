/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.morlunk.jumble.test;

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
