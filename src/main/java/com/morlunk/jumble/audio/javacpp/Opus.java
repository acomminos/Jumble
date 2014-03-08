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
import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Loader;
import com.googlecode.javacpp.Pointer;
import com.googlecode.javacpp.annotation.Cast;
import com.googlecode.javacpp.annotation.NoDeallocator;
import com.googlecode.javacpp.annotation.Platform;
import com.morlunk.jumble.audio.IEncoder;
import com.morlunk.jumble.audio.NativeAudioException;

/**
 * Created by andrew on 18/10/13.
 */

@Platform(library= "opus", link= "opus", cinclude={"<opus.h>","<opus_types.h>"})
public class Opus {

    public static final int OPUS_APPLICATION_VOIP = 2048;

    public static final int OPUS_SET_BITRATE_REQUEST = 4002;
    public static final int OPUS_GET_BITRATE_REQUEST = 4003;
    public static final int OPUS_SET_VBR_REQUEST = 4006;

    static {
        Loader.load();
    }

    public static class OpusEncoder implements IEncoder {

        private Pointer mState;

        public OpusEncoder(int sampleRate, int channels) throws NativeAudioException {
            IntPointer error = new IntPointer(1);
            error.put(0);
            mState = opus_encoder_create(sampleRate, channels, OPUS_APPLICATION_VOIP, error);
            if(error.get() < 0) throw new NativeAudioException("Opus initialization failed with error: "+error.get());
//            Opus.opus_encoder_ctl(mState, Opus.OPUS_SET_VBR_REQUEST, 0);
        }

        @Override
        public void encode(short[] input, int inputSize, byte[] output, int outputSize) throws NativeAudioException {
            int result = Opus.opus_encode(mState, input, inputSize, output, outputSize);
            if(result < 0) throw new NativeAudioException("Opus encoding failed with error: "+result);
        }

        @Override
        public void setBitrate(int bitrate) {
            Opus.opus_encoder_ctl(mState, Opus.OPUS_SET_BITRATE_REQUEST, bitrate);
        }

        public int getBitrate() {
            IntPointer ptr = new IntPointer(1);
            Opus.opus_encoder_ctl(mState, OPUS_GET_BITRATE_REQUEST, ptr);
            return ptr.get();
        }

        @Override
        public void destroy() {
            Opus.opus_encoder_destroy(mState);
        }
    }

    public static native int opus_decoder_get_size(int channels);
    public static native @NoDeallocator Pointer opus_decoder_create(int fs, int channels, IntPointer error);
    public static native int opus_decoder_init(@Cast("OpusDecoder*") Pointer st, int fs, int channels);
    public static native int opus_decode(@Cast("OpusDecoder*") Pointer st, @Cast("const unsigned char*") byte[] data, int len, short[] out, int frameSize, int decodeFec);
    public static native int opus_decode_float(@Cast("OpusDecoder*") Pointer st, @Cast("const unsigned char*") byte[] data, int len, float[] out, int frameSize, int decodeFec);
    //public static native int opus_decoder_ctl(@Cast("OpusDecoder*") Pointer st,  int request);
    public static native void opus_decoder_destroy(@Cast("OpusDecoder*") Pointer st);
    //public static native int opus_packet_parse(@Cast("const unsigned char*") BytePointer data, int len, ...
    public static native int opus_packet_get_bandwidth(@Cast("const unsigned char*") BytePointer data);
    public static native int opus_packet_get_samples_per_frame(@Cast("const unsigned char*") BytePointer data, int fs);
    public static native int opus_packet_get_nb_channels(@Cast("const unsigned char*") BytePointer data);
    public static native int opus_packet_get_nb_frames(@Cast("const unsigned char*") BytePointer packet, int len);
    public static native int opus_packet_get_nb_samples(@Cast("const unsigned char*") BytePointer packet, int len, int fs);


    public static native int opus_encoder_get_size(int channels);
    public static native @NoDeallocator Pointer opus_encoder_create(int fs, int channels, int application, IntPointer error);
    public static native int opus_encoder_init(@Cast("OpusEncoder*") Pointer st, int fs, int channels, int application);
    public static native int opus_encode(@Cast("OpusEncoder*") Pointer st, @Cast("const short*") short[] pcm, int frameSize, @Cast("unsigned char*") byte[] data, int maxDataBytes);
    public static native int opus_encode_float(@Cast("OpusEncoder*") Pointer st, @Cast("const float*") float[] pcm, int frameSize, @Cast("unsigned char*") byte[] data, int maxDataBytes);
    public static native void opus_encoder_destroy(@Cast("OpusEncoder*") Pointer st);
    public static native int opus_encoder_ctl(@Cast("OpusEncoder*") Pointer st, int request, Pointer value);
    public static native int opus_encoder_ctl(@Cast("OpusEncoder*") Pointer st, int request, @Cast("opus_int32") int value);
}
