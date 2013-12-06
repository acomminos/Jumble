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
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.util.MessageFormatter;

/**
 * Handles receiving text messages.
 * Created by andrew on 27/07/13.
 */
public class TextMessageHandler extends ProtocolHandler {

    public TextMessageHandler(JumbleService service) {
        super(service);
    }

    @Override
    public void messageTextMessage(Mumble.TextMessage msg) {
        User sender = getService().getUserHandler().getUser(msg.getActor());

        if(sender != null && sender.isLocalIgnored())
            return;

        Message message = new Message(msg.getActor(), msg.getChannelIdList(), msg.getTreeIdList(), msg.getSessionList(), msg.getMessage());
        getService().logMessage(message);
    }
}
