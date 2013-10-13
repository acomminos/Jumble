/*
 * Copyright (C) 2013 Andrew Comminos
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

package com.morlunk.jumble.test;

import android.test.AndroidTestCase;

import com.morlunk.jumble.audio.opus.Opus;
import com.morlunk.jumble.audio.opus.SWIGTYPE_p_OpusDecoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by andrew on 13/10/13.
 */
public class OpusTest extends AndroidTestCase {

    public static final String PCM_48000_FILE = "speech_48000.wav";
    public static final String OPUS_48000_FILE = "speech_48000.opus";

    /**
     * Tests the decoding of a 48000khz mono opus file into PCM data.
     */
    public void testDecode48000() throws IOException {
        int[] error = new int[1];
        SWIGTYPE_p_OpusDecoder decoder = Opus.opus_decoder_create(48000, 1, error);
        assertEquals(error[0], 0);

        InputStream opusInput = getContext().getAssets().open(OPUS_48000_FILE);
        ByteArrayOutputStream opusOutput = new ByteArrayOutputStream(1024);

        byte[] buffer = new byte[1024];
        while(opusInput.read(buffer) != -1)
            opusOutput.write(buffer);
        opusInput.close();
        opusOutput.close();

        byte[] opusData = opusOutput.toByteArray();
        short[] pcm = new short[2000000];

        Opus.opus_decode(decoder, opusData, opusData.length, pcm, 120, 0);
    }
}
