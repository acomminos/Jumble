/*
 * Copyright (C) 2014 Andrew Comminos
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

package com.morlunk.jumble;

import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Pointer;
import com.morlunk.jumble.audio.javacpp.CELT11;
import com.morlunk.jumble.audio.javacpp.CELT7;
import com.morlunk.jumble.protocol.AudioHandler;

public class Constants {
    /** Set dynamically by JNI calls. */
    public static int CELT_11_VERSION ;
    /** Set dynamically by JNI calls. */
    public static int CELT_7_VERSION;

    static {
        // Load CELT bitstream versions from JNI.
        Pointer celt11Mode = CELT11.celt_mode_create(AudioHandler.SAMPLE_RATE, AudioHandler.FRAME_SIZE, null);
        Pointer celt7Mode = CELT7.celt_mode_create(AudioHandler.SAMPLE_RATE, AudioHandler.FRAME_SIZE, null);

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
    public static final int PROTOCOL_PATCH = 5;

    public static final int TRANSMIT_VOICE_ACTIVITY = 0;
    public static final int TRANSMIT_PUSH_TO_TALK = 1;
    public static final int TRANSMIT_CONTINUOUS = 2;

    public static final int PROTOCOL_VERSION = (PROTOCOL_MAJOR << 16) | (PROTOCOL_MINOR << 8) | PROTOCOL_PATCH;
    public static final String PROTOCOL_STRING = PROTOCOL_MAJOR+ "." +PROTOCOL_MINOR+"."+PROTOCOL_PATCH;
    public static final int DEFAULT_PORT = 64738;

    public static final String TAG = "Jumble";
}
