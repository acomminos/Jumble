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

import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.util.MessageFormatter;

/**
 * Handles receiving text messages.
 * Created by andrew on 27/07/13.
 */
public class TextMessageHandler extends JumbleMessageHandler.Stub {

    private JumbleService mService;

    public TextMessageHandler(JumbleService service) {
        mService = service;
    }

    @Override
    public void messageTextMessage(Mumble.TextMessage msg) {
        // TODO format user colors
        User sender = mService.getUserHandler().getUser(msg.getActor());

        if(sender != null && sender.isLocalIgnored())
            return;

        // TODO use more localized strings here
        String senderName = sender != null ? MessageFormatter.highlightString(sender.getName()) : "Server";
        String senderTarget = "";

        if(msg.getTreeIdCount() > 0)
            senderTarget = "(Tree) ";
        else if(msg.getChannelIdCount() > 0)
            senderTarget = "(Channel) ";

        mService.logMessage(String.format("%s%s: %s", senderTarget, senderName, msg.getMessage()));
    }
}
