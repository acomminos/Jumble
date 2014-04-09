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

    private static final Pattern URL_PATTERN = Pattern.compile("mumble://((.+?)(:(.+?))?@)?(.+?)(:([0-9]+?))?/");

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
