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

package com.morlunk.jumble.protocol;

import com.morlunk.jumble.protobuf.Mumble;

/**
 * Created by andrew on 21/01/14.
 */

public interface JumbleUDPMessageListener {

    public void messageUDPPing(byte[] data);
    public void messageVoiceData(byte[] data);

    public static class Stub implements JumbleUDPMessageListener {

        public void messageUDPPing(byte[] data) {}
        public void messageVoiceData(byte[] data) {}
    }
}