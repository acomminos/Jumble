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

    static {
        Loader.load(Opus.class);
    }

    public static final String PCM_48000_FILE = "speech_48000.wav";
    public static final String OPUS_48000_FILE = "speech_48000.opus";

    /**
     * Tests the decoding of a 48000khz mono opus file into PCM data.
     */
    public void testDecode48000() throws IOException {
        IntPointer error = new IntPointer(1);
        error.put(0);
        Pointer decoder = Opus.opus_decoder_create(48000, 1, error);
        assertEquals(error.get(), 0);

        InputStream opusInput = getContext().getAssets().open(OPUS_48000_FILE);
        ByteArrayOutputStream opusOutput = new ByteArrayOutputStream(1024);

        byte[] buffer = new byte[1024];
        while(opusInput.read(buffer) != -1)
            opusOutput.write(buffer);
        opusInput.close();
        opusOutput.close();

        byte[] opusData = opusOutput.toByteArray();
        short[] pcm = new short[2000000];

        int samplesDecoded = Opus.opus_decode(decoder, opusData, opusData.length, pcm, 120, 0);
    }
}
