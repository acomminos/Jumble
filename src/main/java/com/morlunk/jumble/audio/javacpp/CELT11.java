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

package com.morlunk.jumble.audio.javacpp;

import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Loader;
import com.googlecode.javacpp.Pointer;
import com.googlecode.javacpp.annotation.Cast;
import com.googlecode.javacpp.annotation.NoDeallocator;
import com.googlecode.javacpp.annotation.Platform;
import com.morlunk.jumble.audio.Audio;
import com.morlunk.jumble.audio.IEncoder;
import com.morlunk.jumble.audio.NativeAudioException;

/**
 * Created by andrew on 20/10/13.
 */
@Platform(library="celt11", cinclude={"<celt.h>","<celt_types.h>"})
public class CELT11 {

    public static final int CELT_GET_BITSTREAM_VERSION = 2000;
    public static final int CELT_SET_BITRATE_REQUEST = 6;
    public static final int CELT_SET_PREDICTION_REQUEST = 4;

    static {
        Loader.load();
    }

    public static class CELT11Encoder implements IEncoder {

        private Pointer mState;

        public CELT11Encoder(int sampleRate, int channels) throws NativeAudioException {
            IntPointer error = new IntPointer(1);
            error.put(0);
            mState = celt_encoder_create(sampleRate, channels, error);
            if(error.get() < 0) throw new NativeAudioException("CELT 0.11.0 initialization failed with error: "+error.get());
        }

        @Override
        public void encode(short[] input, int frameSize, byte[] output, int outputSize) throws NativeAudioException {
            int result = celt_encode(mState, input, frameSize, output, outputSize);
            if(result < 0) throw new NativeAudioException("CELT 0.11.0 encoding failed with error: "+result);
        }

        @Override
        public void setBitrate(int bitrate) {
            // FIXME
//            IntPointer ptr = new IntPointer(1);
//            ptr.put(bitrate);
//            celt_encoder_ctl(mState, CELT_SET_BITRATE_REQUEST, ptr);
        }

        @Override
        public void destroy() {
            celt_encoder_destroy(mState);
        }
    }

    public static native @NoDeallocator Pointer celt_mode_create(int sampleRate, int frameSize, IntPointer error);
    public static native int celt_mode_info(@Cast("const CELTMode*") Pointer mode, int request, IntPointer value);
    public static native void celt_mode_destroy(@Cast("CELTMode*") Pointer mode);

    public static native @NoDeallocator Pointer celt_decoder_create(int sampleRate, int channels, IntPointer error);
    public static native int celt_decode(@Cast("CELTDecoder*") Pointer st, @Cast("const unsigned char*") byte[] data, int len, short[] pcm, int frameSize);
    public static native int celt_decode_float(@Cast("CELTDecoder*") Pointer st, @Cast("const unsigned char*") byte[] data, int len, float[] pcm, int frameSize);
    public static native int celt_decoder_ctl(@Cast("CELTDecoder*") Pointer st, int request, Pointer val);
    public static native void celt_decoder_destroy(@Cast("CELTDecoder*") Pointer st);

    public static native @NoDeallocator Pointer celt_encoder_create(int sampleRate, int channels, IntPointer error);
    public static native int celt_encoder_ctl(@Cast("CELTEncoder*")Pointer state, int request, Pointer val);
    public static native int celt_encoder_ctl(@Cast("CELTEncoder*")Pointer state, int request, int val);
    public static native int celt_encode(@Cast("CELTEncoder*") Pointer state, @Cast("const short*") short[] pcm, int frameSize, @Cast("unsigned char*") byte[] compressed, int maxCompressedBytes);
    public static native void celt_encoder_destroy(@Cast("CELTEncoder*") Pointer state);
}