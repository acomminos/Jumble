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
import com.googlecode.javacpp.annotation.ByPtr;
import com.googlecode.javacpp.annotation.ByRef;
import com.googlecode.javacpp.annotation.Cast;
import com.googlecode.javacpp.annotation.MemberGetter;
import com.googlecode.javacpp.annotation.MemberSetter;
import com.googlecode.javacpp.annotation.Name;
import com.googlecode.javacpp.annotation.Platform;

/**
 * JavaCPP interface for Speex JNI.
 * Created by andrew on 18/10/13.
 */
@Platform(library="speex", link="speex", cinclude={"<speex/speex.h>","<speex/speex_types.h>","<speex/speex_jitter.h>", "<speex/speex_preprocess.h>", "<speex/speex_resampler.h>"})
public class Speex {

    static {
        Loader.load();
    }

    @Name("_JitterBufferPacket")
    public static class JitterBufferPacket extends Pointer {

        public JitterBufferPacket(byte[] data, int length, int timestamp, int span, int sequence) {
            allocate();
            if(data != null)
                setData(new BytePointer(data));
            else
                setData(new BytePointer(4096));
            setLength(length);
            setTimestamp(timestamp);
            setSpan(span);
            setSequence(sequence);
        }

        private native void allocate();

        @MemberGetter @Name("data") public native @Cast("char *") BytePointer getData();
        @MemberSetter @Name("data") public native void setData(@Cast("char *") BytePointer pointer);
        @MemberGetter @Name("len") public native int getLength();
        @MemberSetter @Name("len") public native void setLength(int length);
        @MemberGetter @Name("timestamp") public native int getTimestamp();
        @MemberSetter @Name("timestamp") public native void setTimestamp(int timestamp);
        @MemberGetter @Name("span") public native int getSpan();
        @MemberSetter @Name("span") public native void setSpan(int span);
        @MemberGetter @Name("sequence") public native int sequence();
        @MemberSetter @Name("sequence") public native void setSequence(int sequence);
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

}
