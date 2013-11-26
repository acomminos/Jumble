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
