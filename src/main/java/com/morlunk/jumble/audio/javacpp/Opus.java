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

package com.morlunk.jumble.audio.javacpp;

import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Loader;
import com.googlecode.javacpp.Pointer;
import com.googlecode.javacpp.annotation.Cast;
import com.googlecode.javacpp.annotation.Platform;
import com.morlunk.jumble.audio.IDecoder;
import com.morlunk.jumble.exception.NativeAudioException;

import java.nio.ByteBuffer;

/**
 * Created by andrew on 18/10/13.
 */

@Platform(library= "jniopus", cinclude={"<opus.h>","<opus_types.h>"})
public class Opus {
    public static final int OPUS_APPLICATION_VOIP = 2048;

    public static final int OPUS_SET_BITRATE_REQUEST = 4002;
    public static final int OPUS_GET_BITRATE_REQUEST = 4003;
    public static final int OPUS_SET_VBR_REQUEST = 4006;

    public static native int opus_decoder_get_size(int channels);
    public static native Pointer opus_decoder_create(int fs, int channels, IntPointer error);
    public static native int opus_decoder_init(@Cast("OpusDecoder*") Pointer st, int fs, int channels);
    public static native int opus_decode(@Cast("OpusDecoder*") Pointer st, @Cast("const unsigned char*") ByteBuffer data, int len, short[] out, int frameSize, int decodeFec);
    public static native int opus_decode_float(@Cast("OpusDecoder*") Pointer st, @Cast("const unsigned char*") ByteBuffer data, int len, float[] out, int frameSize, int decodeFec);
    //public static native int opus_decoder_ctl(@Cast("OpusDecoder*") Pointer st,  int request);
    public static native void opus_decoder_destroy(@Cast("OpusDecoder*") Pointer st);
    //public static native int opus_packet_parse(@Cast("const unsigned char*") BytePointer data, int len, ...
    public static native int opus_packet_get_bandwidth(@Cast("const unsigned char*") byte[] data);
    public static native int opus_packet_get_samples_per_frame(@Cast("const unsigned char*") byte[] data, int fs);
    public static native int opus_packet_get_nb_channels(@Cast("const unsigned char*") byte[] data);
    public static native int opus_packet_get_nb_frames(@Cast("const unsigned char*") byte[] packet, int len);
    public static native int opus_packet_get_nb_samples(@Cast("const unsigned char*") byte[] packet, int len, int fs);


    public static native int opus_encoder_get_size(int channels);
    public static native Pointer opus_encoder_create(int fs, int channels, int application, IntPointer error);
    public static native int opus_encoder_init(@Cast("OpusEncoder*") Pointer st, int fs, int channels, int application);
    public static native int opus_encode(@Cast("OpusEncoder*") Pointer st, @Cast("const short*") short[] pcm, int frameSize, @Cast("unsigned char*") byte[] data, int maxDataBytes);
    public static native int opus_encode_float(@Cast("OpusEncoder*") Pointer st, @Cast("const float*") float[] pcm, int frameSize, @Cast("unsigned char*") byte[] data, int maxDataBytes);
    public static native void opus_encoder_destroy(@Cast("OpusEncoder*") Pointer st);
    public static native int opus_encoder_ctl(@Cast("OpusEncoder*") Pointer st, int request, Pointer value);
    public static native int opus_encoder_ctl(@Cast("OpusEncoder*") Pointer st, int request, @Cast("opus_int32") int value);

    static {
        Loader.load();
    }

    public static class OpusDecoder implements IDecoder {

        private Pointer mState;

        public OpusDecoder(int sampleRate, int channels) throws NativeAudioException {
            IntPointer error = new IntPointer(1);
            error.put(0);
            mState = opus_decoder_create(sampleRate, channels, error);
            if(error.get() < 0) throw new NativeAudioException("Opus decoder initialization failed with error: "+error.get());
        }

        @Override
        public int decodeFloat(ByteBuffer input, int inputSize, float[] output, int frameSize) throws NativeAudioException {
            int result = opus_decode_float(mState, input, inputSize, output, frameSize, 0);
            if(result < 0) throw new NativeAudioException("Opus decoding failed with error: "+result);
            return result;
        }

        @Override
        public int decodeShort(ByteBuffer input, int inputSize, short[] output, int frameSize) throws NativeAudioException {
            int result = opus_decode(mState, input, inputSize, output, frameSize, 0);
            if(result < 0) throw new NativeAudioException("Opus decoding failed with error: "+result);
            return result;
        }

        @Override
        public void destroy() {
            opus_decoder_destroy(mState);
        }
    }
}
