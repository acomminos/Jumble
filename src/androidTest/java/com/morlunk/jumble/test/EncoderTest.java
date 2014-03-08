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
import com.morlunk.jumble.audio.NativeAudioException;
import com.morlunk.jumble.audio.javacpp.CELT11;
import com.morlunk.jumble.audio.javacpp.CELT7;
import com.morlunk.jumble.audio.javacpp.Opus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
    }

    public void testCELT11Encode() throws NativeAudioException {
        CELT11.CELT11Encoder encoder = new CELT11.CELT11Encoder(SAMPLE_RATE, 1);
//        encoder.setBitrate(BITRATE);
//        assertEquals(encoder.getBitrate(), BITRATE);

        short[] pcm = new short[FRAME_SIZE];
        byte[] output = new byte[1024];
        encoder.encode(pcm, FRAME_SIZE, output, 1024);
    }

    public void testCELT7Encode() throws NativeAudioException {
        CELT7.CELT7Encoder encoder = new CELT7.CELT7Encoder(SAMPLE_RATE, FRAME_SIZE, 1);
//        encoder.setBitrate(BITRATE);
//        assertEquals(encoder.getBitrate(), BITRATE);

        short[] pcm = new short[FRAME_SIZE];
        byte[] output = new byte[1024];
        encoder.encode(pcm, FRAME_SIZE, output, 1024);
    }
}
