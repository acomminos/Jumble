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

package com.morlunk.jumble;

import com.morlunk.jumble.JumbleParams;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.IJumbleObserver;

interface IJumbleService {
    void connect();
    void disconnect();
    boolean isConnected();
    Server getConnectedServer();
    User getUserWithId(int id);
    Channel getChannelWithId(int id);
    List getChannelList();

    void registerObserver(in IJumbleObserver observer);
    void unregisterObserver(in IJumbleObserver observer);
}