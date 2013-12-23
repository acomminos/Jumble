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

import com.googlecode.javacpp.BytePointer;
import com.googlecode.javacpp.FloatPointer;
import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Loader;
import com.googlecode.javacpp.Pointer;
import com.googlecode.javacpp.ShortPointer;
import com.googlecode.javacpp.annotation.Cast;
import com.googlecode.javacpp.annotation.NoDeallocator;
import com.googlecode.javacpp.annotation.Platform;

/**
 * Created by andrew on 20/10/13.
 */
@Platform(library="celt7", cinclude={"<celt.h>","<celt_types.h>"})
public class CELT7 {

    public static final int CELT_GET_BITSTREAM_VERSION = 2000;

    static {
        Loader.load();
    }

    public static native @NoDeallocator Pointer celt_mode_create(int sampleRate, int frameSize, IntPointer error);
    public static native int celt_mode_info(@Cast("const CELTMode*") Pointer mode, int request, IntPointer value);
    public static native void celt_mode_destroy(@Cast("CELTMode*") Pointer mode);

    public static native @NoDeallocator Pointer celt_decoder_create(@Cast("CELTMode*") Pointer mode, int channels, IntPointer error);
    public static native int celt_decode(@Cast("CELTDecoder*") Pointer st, @Cast("const unsigned char*") byte[] data, int len, short[] pcm);
    public static native int celt_decode_float(@Cast("CELTDecoder*") Pointer st, @Cast("const unsigned char*") byte[] data, int len, float[] pcm);
    public static native int celt_decoder_ctl(@Cast("CELTDecoder*") Pointer st, int request, Pointer val);
    public static native void celt_decoder_destroy(@Cast("CELTDecoder*") Pointer st);

    public static native @NoDeallocator Pointer celt_encoder_create(@Cast("const CELTMode *") Pointer mode, int channels, IntPointer error);
    public static native int celt_encoder_ctl(@Cast("CELTEncoder*")Pointer state, int request, Pointer val);
    public static native int celt_encode(@Cast("CELTEncoder *") Pointer state, @Cast("const short *") short[] pcm, @Cast("short *") short[] optionalSynthesis, @Cast("unsigned char *") byte[] compressed, int nbCompressedBytes);
    public static native void celt_encoder_destroy(@Cast("CELTEncoder *") Pointer state);
}
