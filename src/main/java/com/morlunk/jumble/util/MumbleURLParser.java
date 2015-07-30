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

package com.morlunk.jumble.util;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.model.Server;

import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of the Mumble URL scheme.
 * @see <a href="http://mumble.sourceforge.net/Mumble_URL">http://mumble.sourceforge.net/Mumble_URL</a>
 * Created by andrew on 03/03/14.
 */
public class MumbleURLParser {

    private static final Pattern URL_PATTERN = Pattern.compile("mumble://(([^:]+)?(:(.+?))?@)?(.+?)(:([0-9]+?))?/");

    /**
     * Parses the passed Mumble URL into a Server object.
     * @param url A URL with the Mumble scheme.
     * @return A server with the data specified in the Mumble URL.
     * @throws MalformedURLException if the URL cannot be parsed.
     */
    public static Server parseURL(String url) throws MalformedURLException {
        Matcher matcher = URL_PATTERN.matcher(url);
        if(matcher.find()) {
            String username = matcher.group(2);
            String password = matcher.group(4);
            String host = matcher.group(5);
            String portString = matcher.group(7);
            int port = portString == null ? Constants.DEFAULT_PORT : Integer.parseInt(portString);
            return new Server(-1, null, host, port, username, password);
        } else {
            throw new MalformedURLException();
        }
    }
}
