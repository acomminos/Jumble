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

package com.morlunk.jumble.test;

import android.test.AndroidTestCase;

import com.googlecode.javacpp.BytePointer;
import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Loader;
import com.googlecode.javacpp.Pointer;
import com.morlunk.jumble.audio.javacpp.Opus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by andrew on 13/10/13.
 */
public class OpusTest extends AndroidTestCase {

    private static final int FRAME_SIZE = 480;

    static {
        Loader.load(Opus.class);
    }

    /**
     * Tests the encoding of an empty 16 bit waveform of frame size 480 (mumble typical).
     * This does not check the integrity of the encoding, it only tests the success
     * of the operation.
     */
    public void testEncodeFrame() throws IOException {
        IntPointer error = new IntPointer(1);
        error.put(0);
        Pointer encoder = Opus.opus_encoder_create(48000, 1, Opus.OPUS_APPLICATION_VOIP, error);
        assertEquals(error.get(), 0);

        short[] pcm = new short[FRAME_SIZE];
        byte[] output = new byte[1024];
        int result = Opus.opus_encode(encoder, pcm, FRAME_SIZE, output, output.length);
        assertTrue(result > 0);
    }
}
