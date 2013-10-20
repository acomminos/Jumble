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
import com.googlecode.javacpp.annotation.Platform;

/**
 * Created by andrew on 18/10/13.
 */

@Platform(library="opus", link="opus", cinclude={"<opus.h>","<opus_types.h>"})
public class Opus {
    static {
        Loader.load();
    }

    public static native int opus_decoder_get_size(int channels);
    public static native Pointer opus_decoder_create(int fs, int channels, IntPointer error);
    public static native int opus_decoder_init(@Cast("OpusDecoder*") Pointer st, int fs, int channels);
    public static native int opus_decode(@Cast("OpusDecoder*") Pointer st, @Cast("const unsigned char*") BytePointer data, int len, ShortPointer out, int frameSize, int decodeFec);
    public static native int opus_decode_float(@Cast("OpusDecoder*") Pointer st, @Cast("const unsigned char*") BytePointer data, int len, FloatPointer out, int frameSize, int decodeFec);
    //public static native int opus_decoder_ctl(@Cast("OpusDecoder*") Pointer st,  int request);
    public static native void opus_decoder_destroy(@Cast("OpusDecoder*") Pointer st);
    //public static native int opus_packet_parse(@Cast("const unsigned char*") BytePointer data, int len, ...
    public static native int opus_packet_get_bandwidth(@Cast("const unsigned char*") BytePointer data);
    public static native int opus_packet_get_samples_per_frame(@Cast("const unsigned char*") BytePointer data, int fs);
    public static native int opus_packet_get_nb_channels(@Cast("const unsigned char*") BytePointer data);
    public static native int opus_packet_get_nb_frames(@Cast("const unsigned char*") BytePointer packet, int len);
    public static native int opus_packet_get_nb_samples(@Cast("const unsigned char*") BytePointer packet, int len, int fs);

}
