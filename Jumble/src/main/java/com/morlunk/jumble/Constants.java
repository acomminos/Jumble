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

import com.morlunk.jumble.audio.Audio;
import com.morlunk.jumble.audio.celt11.CELT11;
import com.morlunk.jumble.audio.celt11.CELT11Constants;
import com.morlunk.jumble.audio.celt11.CELT11Mode;
import com.morlunk.jumble.audio.celt7.CELT7;
import com.morlunk.jumble.audio.celt7.CELT7Constants;
import com.morlunk.jumble.audio.celt7.CELT7Mode;

public class Constants {

    /** Set dynamically by JNI calls. */
    public static int CELT_11_VERSION;
    /** Set dynamically by JNI calls. */
    public static int CELT_7_VERSION;

    static {
        // Load CELT bitstream versions from JNI. TODO clean me
        CELT11Mode celt11Mode = CELT11.celt_mode_create(Audio.SAMPLE_RATE, Audio.FRAME_SIZE, null);
        CELT7Mode celt7Mode = CELT7.celt_mode_create(Audio.SAMPLE_RATE, Audio.FRAME_SIZE, null);
        CELT11.celt_mode_info(celt11Mode, CELT11Constants.CELT_GET_BITSTREAM_VERSION, new int[] { CELT_11_VERSION });
        CELT7.celt_mode_info(celt7Mode, CELT7Constants.CELT_GET_BITSTREAM_VERSION, new int[] { CELT_7_VERSION });
        CELT11.celt_mode_destroy(celt11Mode);
        CELT7.celt_mode_destroy(celt7Mode);
    }

    public static final int PROTOCOL_MAJOR = 1;
    public static final int PROTOCOL_MINOR = 2;
    public static final int PROTOCOL_PATCH = 4;

    public static final int PROTOCOL_VERSION = (PROTOCOL_MAJOR << 16) | (PROTOCOL_MINOR << 8) | PROTOCOL_PATCH;
    public static final String PROTOCOL_STRING = PROTOCOL_MAJOR+"."+PROTOCOL_MINOR+"."+PROTOCOL_PATCH;
    public static final int DEFAULT_PORT = 64738;

    public static final String TAG = "Jumble";
}
