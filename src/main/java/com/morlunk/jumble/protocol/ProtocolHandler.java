/*
 * Copyright (C) 2013 Andrew Comminos
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

package com.morlunk.jumble.protocol;

import com.morlunk.jumble.JumbleService;

/**
 * A protocol handler implements a discrete type (or class) of TCP or UDP protobuf messages.
 * The purpose of protocol handlers is to divide logic for each type of data received from the
 * server (i.e. audio, user state, channels) into their own classes.
 * Created by andrew on 21/11/13.
 */
public class ProtocolHandler extends JumbleMessageListener.Stub {

    private JumbleService mService;

    public ProtocolHandler(JumbleService service) {
        mService = service;
    }

    protected JumbleService getService() {
        return mService;
    }
}
