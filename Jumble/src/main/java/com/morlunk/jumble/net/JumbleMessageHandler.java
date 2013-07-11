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

import android.util.Log;
import com.google.protobuf.Message;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.protobuf.Mumble;

/**
 * Reads incoming protobuf TCP messages and performs the necessary action(s).
 * Created by andrew on 24/06/13.
 */
public class JumbleMessageHandler {

    public void handleMessage(Message message, JumbleTCPMessageType messageType) {
        Log.v(Constants.TAG, "IN: "+messageType);

        switch(messageType) {
            case UDPTunnel:
                Mumble.UDPTunnel udpTunnel = (Mumble.UDPTunnel)message;

                break;
        }
    }
}
