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
import com.googlecode.javacpp.annotation.MemberGetter;
import com.googlecode.javacpp.annotation.MemberSetter;
import com.googlecode.javacpp.annotation.Name;
import com.googlecode.javacpp.annotation.Platform;
import com.morlunk.jumble.audio.IDecoder;
import com.morlunk.jumble.exception.NativeAudioException;

import java.nio.ByteBuffer;

/**
 * JavaCPP interface for Speex JNI.
 * Created by andrew on 18/10/13.
 */
@Platform(library= "jnispeex", cinclude={"<speex/speex.h>","<speex/speex_types.h>", "<speex/speex_bits.h>","<speex/speex_jitter.h>", "<speex/speex_preprocess.h>", "<speex/speex_resampler.h>"})
public class Speex {

    /**
     * Set enhancement on/off (decoder only)
     */
    public static final int SPEEX_SET_ENH = 0;
    /**
     * Get enhancement state (decoder only)
     */
    public static final int SPEEX_GET_ENH = 1;

/*Would be SPEEX_SET_FRAME_SIZE, but it's (currently) invalid*/
    /**
     * Obtain frame size used by encoder/decoder
     */
    public static final int SPEEX_GET_FRAME_SIZE = 3;
    /**
     * Set quality value
     */
    public static final int SPEEX_SET_QUALITY = 4;

/** Get current quality setting */
/* public static final int SPEEX_GET_QUALITY = 5; -- Doesn't make much sense, does it? */

    /**
     * Set sub-mode to use
     */
    public static final int SPEEX_SET_MODE = 6;
    /**
     * Get current sub-mode in use
     */
    public static final int SPEEX_GET_MODE = 7;
    /**
     * Set low-band sub-mode to use (wideband only)
     */
    public static final int SPEEX_SET_LOW_MODE = 8;
    /**
     * Get current low-band mode in use (wideband only)
     */
    public static final int SPEEX_GET_LOW_MODE = 9;
    /**
     * Set high-band sub-mode to use (wideband only)
     */
    public static final int SPEEX_SET_HIGH_MODE = 10;
    /**
     * Get current high-band mode in use (wideband only)
     */
    public static final int SPEEX_GET_HIGH_MODE = 11;
    /**
     * Set VBR on (1) or off (0)
     */
    public static final int SPEEX_SET_VBR = 12;
    /**
     * Get VBR status (1 for on, 0 for off)
     */
    public static final int SPEEX_GET_VBR = 13;
    /**
     * Set quality value for VBR encoding (0-10)
     */
    public static final int SPEEX_SET_VBR_QUALITY = 14;
    /**
     * Get current quality value for VBR encoding (0-10)
     */
    public static final int SPEEX_GET_VBR_QUALITY = 15;
    /**
     * Set complexity of the encoder (0-10)
     */
    public static final int SPEEX_SET_COMPLEXITY = 16;
    /**
     * Get current complexity of the encoder (0-10)
     */
    public static final int SPEEX_GET_COMPLEXITY = 17;
    /**
     * Set bit-rate used by the encoder (or lower)
     */
    public static final int SPEEX_SET_BITRATE = 18;
    /**
     * Get current bit-rate used by the encoder or decoder
     */
    public static final int SPEEX_GET_BITRATE = 19;
    /**
     * Define a handler function for in-band Speex request
     */
    public static final int SPEEX_SET_HANDLER = 20;
    /**
     * Define a handler function for in-band user-defined request
     */
    public static final int SPEEX_SET_USER_HANDLER = 22;
    /**
     * Set sampling rate used in bit-rate computation
     */
    public static final int SPEEX_SET_SAMPLING_RATE = 24;
    /**
     * Get sampling rate used in bit-rate computation
     */
    public static final int SPEEX_GET_SAMPLING_RATE = 25;
    /**
     * Reset the encoder/decoder memories to zero
     */
    public static final int SPEEX_RESET_STATE = 26;
    /**
     * Get VBR info (mostly used internally)
     */
    public static final int SPEEX_GET_RELATIVE_QUALITY = 29;
    /**
     * Set VAD status (1 for on, 0 for off)
     */
    public static final int SPEEX_SET_VAD = 30;
    /**
     * Get VAD status (1 for on, 0 for off)
     */
    public static final int SPEEX_GET_VAD = 31;
    /**
     * Set Average Bit-Rate (ABR) to n bits per seconds
     */
    public static final int SPEEX_SET_ABR = 32;
    /**
     * Get Average Bit-Rate (ABR) setting (in bps)
     */
    public static final int SPEEX_GET_ABR = 33;
    /**
     * Set DTX status (1 for on, 0 for off)
     */
    public static final int SPEEX_SET_DTX = 34;
    /**
     * Get DTX status (1 for on, 0 for off)
     */
    public static final int SPEEX_GET_DTX = 35;
    /**
     * Set submode encoding in each frame (1 for yes, 0 for no, setting to no breaks the standard)
     */
    public static final int SPEEX_SET_SUBMODE_ENCODING = 36;
    /**
     * Get submode encoding in each frame
     */
    public static final int SPEEX_GET_SUBMODE_ENCODING = 37;

/*public static final int SPEEX_SET_LOOKAHEAD = 38;*/
    /**
     * Returns the lookahead used by Speex separately for an encoder and a decoder.
     * Sum encoder and decoder lookahead values to get the total codec lookahead.
     */
    public static final int SPEEX_GET_LOOKAHEAD = 39;
    /**
     * Sets tuning for packet-loss concealment (expected loss rate)
     */
    public static final int SPEEX_SET_PLC_TUNING = 40;
    /**
     * Gets tuning for PLC
     */
    public static final int SPEEX_GET_PLC_TUNING = 41;
    /**
     * Sets the max bit-rate allowed in VBR mode
     */
    public static final int SPEEX_SET_VBR_MAX_BITRATE = 42;
    /**
     * Gets the max bit-rate allowed in VBR mode
     */
    public static final int SPEEX_GET_VBR_MAX_BITRATE = 43;
    /**
     * Turn on/off input/output high-pass filtering
     */
    public static final int SPEEX_SET_HIGHPASS = 44;
    /**
     * Get status of input/output high-pass filtering
     */
    public static final int SPEEX_GET_HIGHPASS = 45;
    /**
     * Get "activity level" of the last decoded frame, i.e.
     * how much damage we cause if we remove the frame
     */
    public static final int SPEEX_GET_ACTIVITY = 47;

    /** Number of defined modes in Speex */
    public static final int SPEEX_NB_MODES = 3;

    /** modeID for the defined narrowband mode */
    public static final int SPEEX_MODEID_NB = 0;

    /** modeID for the defined wideband mode */
    public static final int SPEEX_MODEID_WB = 1;

    /** modeID for the defined ultra-wideband mode */
    public static final int SPEEX_MODEID_UWB = 2;

    static {
        Loader.load();
    }

    // Resampler
    private static native Pointer speex_resampler_init(int channels, int inSampleRate, int outSampleRate, int quality, IntPointer error);
    private static native int speex_resampler_process_int(@Cast("SpeexResamplerState*") Pointer state, int channelIndex, @Cast("short*") short[] in, @Cast("unsigned int*") int[] inLen, @Cast("short*") short[] out, @Cast("unsigned int*") int[] outLen);
    private static native void speex_resampler_destroy(@Cast("SpeexResamplerState*") Pointer state);

    // Jitter buffer
    private static native Pointer jitter_buffer_init(int tick);
    private static native void jitter_buffer_reset(@Cast("JitterBuffer*") Pointer jitterBuffer);
    private static native void jitter_buffer_destroy(@Cast("JitterBuffer*") Pointer jitterBuffer);
    private static native void jitter_buffer_put(@Cast("JitterBuffer*") Pointer jitterBuffer, JitterBufferPacket packet);
    private static native int jitter_buffer_get(@Cast("JitterBuffer*") Pointer jitterBuffer, JitterBufferPacket packet, int frameSize, IntPointer startOffset);
    private static native int jitter_buffer_get_pointer_timestamp(@Cast("JitterBuffer*") Pointer jitterBuffer);
    private static native void jitter_buffer_tick(@Cast("JitterBuffer*") Pointer jitterBuffer);
    private static native int jitter_buffer_ctl(@Cast("JitterBuffer*") Pointer jitterBuffer, int request, @Cast("void *") Pointer pointer);
    private static native int jitter_buffer_update_delay(@Cast("JitterBuffer*") Pointer jitterBuffer, JitterBufferPacket packet, IntPointer startOffset);

    // Preprocessor
    private static native Pointer speex_preprocess_state_init(int frameSize, int samplingRate);
    private static native void speex_preprocess_state_destroy(@Cast("SpeexPreprocessState*") Pointer state);
    private static native int speex_preprocess_run(@Cast("SpeexPreprocessState*") Pointer state, short x[]);
    private static native int speex_preprocess(@Cast("SpeexPreprocessState*") Pointer state, short[] x, int[] echo);
    private static native void speex_preprocess_estimate_update(@Cast("SpeexPreprocessState*") Pointer state, short[] x);
    private static native int speex_preprocess_ctl(@Cast("SpeexPreprocessState*") Pointer state, int request, Pointer ptr);

    // Bits
    private static native void speex_bits_init(@Cast("SpeexBits*") SpeexBits bits);
    private static native void speex_bits_read_from(@Cast("SpeexBits*") SpeexBits bits, @Cast("const char*") ByteBuffer data, int size);
    private static native void speex_bits_destroy(@Cast("SpeexBits*") SpeexBits bits);

    // Modes
    public static native @Cast("const void*") Pointer speex_lib_get_mode(int mode);

    // Decoder
    public static native Pointer speex_decoder_init(@Cast("const SpeexMode*") Pointer mode);
    public static native void speex_decoder_ctl(Pointer state, int request, Pointer value);
    public static native int speex_decode(Pointer state, @Cast("SpeexBits*") SpeexBits bits, float[] out);
    public static native void speex_decoder_destroy(Pointer state);

    // Encoder
    public static native Pointer speex_encoder_init(@Cast("const SpeexMode*") Pointer mode);
    public static native void speex_encoder_ctl(Pointer state, int request, Pointer value);
    public static native void speex_encoder_destroy(Pointer state);

    @Name("_JitterBufferPacket")
    public static class JitterBufferPacket extends Pointer {

        public JitterBufferPacket(ByteBuffer data, int length, int timestamp, int span, int sequence, int userData) {
            allocate();
            setData(data);
            setLength(length);
            setTimestamp(timestamp);
            setSpan(span);
            setSequence(sequence);
            setUserData(userData);
        }

        public JitterBufferPacket(byte[] data, int length, int timestamp, int span, int sequence, int userData) {
            allocate();
            setData(data);
            setLength(length);
            setTimestamp(timestamp);
            setSpan(span);
            setSequence(sequence);
            setUserData(userData);
        }

        private native void allocate();

        @MemberGetter @Name("data") public native @Cast("char *") ByteBuffer getData();
        @MemberSetter @Name("data") public native void setData(@Cast("char *") ByteBuffer data);
        @MemberSetter @Name("data") public native void setData(@Cast("char *") byte[] data);
        @MemberGetter @Name("len") public native int getLength();
        @MemberSetter @Name("len") public native void setLength(int length);
        @MemberGetter @Name("timestamp") public native int getTimestamp();
        @MemberSetter @Name("timestamp") public native void setTimestamp(int timestamp);
        @MemberGetter @Name("span") public native int getSpan();
        @MemberSetter @Name("span") public native void setSpan(int span);
        @MemberGetter @Name("sequence") public native int sequence();
        @MemberSetter @Name("sequence") public native void setSequence(int sequence);
        @MemberGetter @Name("user_data") public native int getUserData();
        @MemberSetter @Name("user_data") public native void setUserData(int userData);
    }

    /**
     * Custom OO wrapper for the native adaptive Speex jitter buffer.
     */
    public static class JitterBuffer {

        public static final int JITTER_BUFFER_OK = 0;
        public static final int JITTER_BUFFER_MISSING = 1;
        public static final int JITTER_BUFFER_INCOMPLETE = 2;
        public static final int JITTER_BUFFER_INTERNAL_ERROR = -1;
        public static final int JITTER_BUFFER_BAD_ARGUMENT = -2;
        public static final int JITTER_BUFFER_SET_MARGIN = 0;
        public static final int JITTER_BUFFER_GET_MARGIN = 1;
        public static final int JITTER_BUFFER_GET_AVAILABLE_COUNT = 3;


        private Pointer mNativeBuffer;
        private int mFrameSize;

        public JitterBuffer(int frameSize) {
            mNativeBuffer = jitter_buffer_init(frameSize);
            mFrameSize = frameSize;
        }

        public int getPointerTimestamp() {
            return jitter_buffer_get_pointer_timestamp(mNativeBuffer);
        }

        public void put(JitterBufferPacket packet) {
            jitter_buffer_put(mNativeBuffer, packet);
        }

        public int get(JitterBufferPacket packet, IntPointer startOfs) {
            return jitter_buffer_get(mNativeBuffer, packet, mFrameSize, startOfs);
        }

        public int control(int request, Pointer value) {
            return jitter_buffer_ctl(mNativeBuffer, request, value);
        }

        public int updateDelay(JitterBufferPacket packet, IntPointer startOfs) {
            return jitter_buffer_update_delay(mNativeBuffer, packet, startOfs);
        }

        public void tick() {
            jitter_buffer_tick(mNativeBuffer);
        }

        public void reset() {
            jitter_buffer_reset(mNativeBuffer);
        }

        public void destroy() {
            jitter_buffer_destroy(mNativeBuffer);
        }

    }

    public static class SpeexPreprocessState {

        public static final int SPEEX_PREPROCESS_SET_DENOISE = 0;
        public static final int SPEEX_PREPROCESS_GET_DENOISE = 1;
        public static final int SPEEX_PREPROCESS_SET_AGC = 2;
        public static final int SPEEX_PREPROCESS_GET_AGC = 3;
        public static final int SPEEX_PREPROCESS_SET_VAD = 4;
        public static final int SPEEX_PREPROCESS_GET_VAD = 5;
        public static final int SPEEX_PREPROCESS_SET_AGC_LEVEL = 6;
        public static final int SPEEX_PREPROCESS_GET_AGC_LEVEL = 7;
        public static final int SPEEX_PREPROCESS_SET_DEREVERB = 8;
        public static final int SPEEX_PREPROCESS_GET_DEREVERB = 9;
        public static final int SPEEX_PREPROCESS_SET_DEREVERB_LEVEL = 10;
        public static final int SPEEX_PREPROCESS_GET_DEREVERB_LEVEL = 11;
        public static final int SPEEX_PREPROCESS_SET_DEREVERB_DECAY = 12;
        public static final int SPEEX_PREPROCESS_GET_DEREVERB_DECAY = 13;
        public static final int SPEEX_PREPROCESS_SET_PROB_START = 14;
        public static final int SPEEX_PREPROCESS_GET_PROB_START = 15;
        public static final int SPEEX_PREPROCESS_SET_PROB_CONTINUE = 16;
        public static final int SPEEX_PREPROCESS_GET_PROB_CONTINUE = 17;
        public static final int SPEEX_PREPROCESS_SET_NOISE_SUPPRESS = 18;
        public static final int SPEEX_PREPROCESS_GET_NOISE_SUPPRESS = 19;
        public static final int SPEEX_PREPROCESS_SET_ECHO_SUPPRESS = 20;
        public static final int SPEEX_PREPROCESS_GET_ECHO_SUPPRESS = 21;
        public static final int SPEEX_PREPROCESS_SET_ECHO_SUPPRESS_ACTIVE = 22;
        public static final int SPEEX_PREPROCESS_GET_ECHO_SUPPRESS_ACTIVE = 23;
        public static final int SPEEX_PREPROCESS_SET_ECHO_STATE = 24;
        public static final int SPEEX_PREPROCESS_GET_ECHO_STATE = 25;
        public static final int SPEEX_PREPROCESS_SET_AGC_INCREMENT = 26;
        public static final int SPEEX_PREPROCESS_GET_AGC_INCREMENT = 27;
        public static final int SPEEX_PREPROCESS_SET_AGC_DECREMENT = 28;
        public static final int SPEEX_PREPROCESS_GET_AGC_DECREMENT = 29;
        public static final int SPEEX_PREPROCESS_SET_AGC_MAX_GAIN = 30;
        public static final int SPEEX_PREPROCESS_GET_AGC_MAX_GAIN = 31;
        public static final int SPEEX_PREPROCESS_GET_AGC_LOUDNESS = 33;
        public static final int SPEEX_PREPROCESS_GET_AGC_GAIN = 35;
        public static final int SPEEX_PREPROCESS_GET_PSD_SIZE = 37;
        public static final int SPEEX_PREPROCESS_GET_PSD = 39;
        public static final int SPEEX_PREPROCESS_GET_NOISE_PSD_SIZE = 41;
        public static final int SPEEX_PREPROCESS_GET_NOISE_PSD = 43;
        public static final int SPEEX_PREPROCESS_GET_PROB = 45;
        public static final int SPEEX_PREPROCESS_SET_AGC_TARGET = 46;
        public static final int SPEEX_PREPROCESS_GET_AGC_TARGET = 47;

        private Pointer mNativeState;

        public SpeexPreprocessState(int frameSize, int samplingRate) {
            mNativeState = speex_preprocess_state_init(frameSize, samplingRate);
        }

        public void preprocess(short[] data) {
            speex_preprocess_run(mNativeState, data);
        }

        public int control(int request, Pointer pointer) {
            return speex_preprocess_ctl(mNativeState, request, pointer);
        }

        public void destroy() {
            speex_preprocess_state_destroy(mNativeState);
        }

    }

    public static class SpeexResampler {
        private Pointer mNativeState;

        public SpeexResampler(int channels, int inSampleRate, int outSampleRate, int quality) {
            mNativeState = speex_resampler_init(channels, inSampleRate, outSampleRate, quality, null);
        }

        public void resample(short[] in, short[] out) {
            speex_resampler_process_int(mNativeState, 0, in, new int[] { in.length }, out, new int[] { out.length });
        }

        public void destroy() {
            speex_resampler_destroy(mNativeState);
        }

    }

    @Name("SpeexBits")
    public static class SpeexBits extends Pointer {

        public SpeexBits() {
            allocate();
            speex_bits_init(this);
        }

        private native void allocate();

        public void read(ByteBuffer data, int len) {
            speex_bits_read_from(this, data, len);
        }

        public void destroy() {
            speex_bits_destroy(this);
        }
    }

    public static class SpeexDecoder implements IDecoder {

        private SpeexBits mBits;
        private Pointer mState;

        public SpeexDecoder() {
            mBits = new Speex.SpeexBits();
            mState = Speex.speex_decoder_init(Speex.speex_lib_get_mode(Speex.SPEEX_MODEID_UWB));
            IntPointer enh = new IntPointer();
            enh.put(1);
            Speex.speex_decoder_ctl(mState, Speex.SPEEX_SET_ENH, enh);
        }

        @Override
        public int decodeFloat(ByteBuffer input, int inputSize, float[] output, int frameSize) throws NativeAudioException {
            speex_bits_read_from(mBits, input, inputSize);
            int result = speex_decode(mState, mBits, output);
            if(result < 0) throw new NativeAudioException("Speex decoding failed with error: "+result);
            for(int i=0; i < frameSize; i++) {
                output[i] *= (1.0f / Short.MAX_VALUE);
            }
            return frameSize;
        }

        @Override
        public int decodeShort(ByteBuffer input, int inputSize, short[] output, int frameSize) throws NativeAudioException {
            float[] foutput = new float[frameSize];
            speex_bits_read_from(mBits, input, inputSize);
            int result = speex_decode(mState, mBits, foutput);
            if(result < 0) throw new NativeAudioException("Speex decoding failed with error: "+result);
            for(int i=0; i < frameSize; i++) {
                output[i] = (short) foutput[i];
            }
            return frameSize;
        }

        @Override
        public void destroy() {
            speex_decoder_destroy(mState);
            speex_bits_destroy(mBits);
        }
    }
}
