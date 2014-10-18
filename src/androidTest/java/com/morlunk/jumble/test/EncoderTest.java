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

package com.morlunk.jumble.test;

import android.test.AndroidTestCase;

import com.googlecode.javacpp.Loader;
import com.morlunk.jumble.audio.javacpp.CELT11;
import com.morlunk.jumble.audio.javacpp.CELT7;
import com.morlunk.jumble.audio.javacpp.Opus;
import com.morlunk.jumble.exception.NativeAudioException;

/**
 * This class tests the Opus and CELT encoders with blank PCM data.
 * The bitrate is set to 40000bps. TODO: add test for varying bitrates.
 * If any of these methods throw a NativeAudioException, then the test will fail.
 * Created by andrew on 13/10/13.
 */
public class EncoderTest extends AndroidTestCase {

    private static final int SAMPLE_RATE = 48000;
    private static final int BITRATE = 40000;
    private static final int FRAME_SIZE = 480;

    static {
        Loader.load(Opus.class);
    }

    public void testOpusEncode() throws NativeAudioException {
        Opus.OpusEncoder encoder = new Opus.OpusEncoder(SAMPLE_RATE, 1);
        encoder.setBitrate(BITRATE);
        assertEquals(encoder.getBitrate(), BITRATE);

        short[] pcm = new short[FRAME_SIZE];
        byte[] output = new byte[1024];
        encoder.encode(pcm, FRAME_SIZE, output, 1024);
        encoder.destroy();
    }

    public void testCELT11Encode() throws NativeAudioException {
        CELT11.CELT11Encoder encoder = new CELT11.CELT11Encoder(SAMPLE_RATE, 1);
//        encoder.setBitrate(BITRATE);
//        assertEquals(encoder.getBitrate(), BITRATE);

        short[] pcm = new short[FRAME_SIZE];
        byte[] output = new byte[1024];
        encoder.encode(pcm, FRAME_SIZE, output, 1024);
        encoder.destroy();
    }

    public void testCELT7Encode() throws NativeAudioException {
        CELT7.CELT7Encoder encoder = new CELT7.CELT7Encoder(SAMPLE_RATE, FRAME_SIZE, 1);
//        encoder.setBitrate(BITRATE);
//        assertEquals(encoder.getBitrate(), BITRATE);

        short[] pcm = new short[FRAME_SIZE];
        byte[] output = new byte[1024];
        encoder.encode(pcm, FRAME_SIZE, output, 1024);
        encoder.destroy();
    }
}
