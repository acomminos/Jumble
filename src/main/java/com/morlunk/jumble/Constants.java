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

import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Loader;
import com.googlecode.javacpp.Pointer;
import com.morlunk.jumble.audio.Audio;
import com.morlunk.jumble.audio.javacpp.CELT11;
import com.morlunk.jumble.audio.javacpp.CELT7;

public class Constants {
    /** Set dynamically by JNI calls. */
    public static int CELT_11_VERSION ;
    /** Set dynamically by JNI calls. */
    public static int CELT_7_VERSION;

    static {
        // Load CELT bitstream versions from JNI.
        Pointer celt11Mode = CELT11.celt_mode_create(Audio.SAMPLE_RATE, Audio.FRAME_SIZE, null);
        Pointer celt7Mode = CELT7.celt_mode_create(Audio.SAMPLE_RATE, Audio.FRAME_SIZE, null);

        IntPointer celt11Version = new IntPointer();
        IntPointer celt7Version = new IntPointer();

        CELT11.celt_mode_info(celt11Mode, CELT11.CELT_GET_BITSTREAM_VERSION, celt11Version);
        CELT7.celt_mode_info(celt7Mode, CELT7.CELT_GET_BITSTREAM_VERSION, celt7Version);
        CELT11.celt_mode_destroy(celt11Mode);
        CELT7.celt_mode_destroy(celt7Mode);

        CELT_11_VERSION = celt11Version.get();
        CELT_7_VERSION = celt7Version.get();
    }

    public static final int PROTOCOL_MAJOR = 1;
    public static final int PROTOCOL_MINOR = 2;
    public static final int PROTOCOL_PATCH = 4;

    public static final int TRANSMIT_VOICE_ACTIVITY = 0;
    public static final int TRANSMIT_PUSH_TO_TALK = 1;
    public static final int TRANSMIT_CONTINUOUS = 2;

    public static final int PROTOCOL_VERSION = (PROTOCOL_MAJOR << 16) | (PROTOCOL_MINOR << 8) | PROTOCOL_PATCH;
    public static final String PROTOCOL_STRING = PROTOCOL_MAJOR+"."+PROTOCOL_MINOR+"."+PROTOCOL_PATCH;
    public static final int DEFAULT_PORT = 64738;

    public static final String TAG = "Jumble";
}
