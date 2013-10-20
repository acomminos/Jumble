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
@Platform(library="speex", link="speex", cinclude={"<speex/speex.h>","<speex/speex_types.h>","<speex/speex_jitter.h>"})
public class Speex {

    static {
        Loader.load();
    }

    @Name("_JitterBufferPacket")
    public static class JitterBufferPacket extends Pointer {

        public JitterBufferPacket(byte[] data, int timestamp, int span, int sequence) {
            allocate();
            setData(new BytePointer(data));
            setLength(data.length);
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

    private static native Pointer jitter_buffer_init(int tick);
    private static native void jitter_buffer_reset(@Cast("JitterBuffer*") Pointer jitterBuffer);
    private static native void jitter_buffer_destroy(@Cast("JitterBuffer*") Pointer jitterBuffer);
    private static native void jitter_buffer_put(@Cast("JitterBuffer*") Pointer jitterBuffer, JitterBufferPacket packet);
    private static native int jitter_buffer_get(@Cast("JitterBuffer*") Pointer jitterBuffer, JitterBufferPacket packet, int frameSize, IntPointer startOffset);
    private static native int jitter_buffer_get_pointer_timestamp(@Cast("JitterBuffer*") Pointer jitterBuffer);
    private static native void jitter_buffer_tick(@Cast("JitterBuffer*") Pointer jitterBuffer);
    private static native int jitter_buffer_ctl(@Cast("JitterBuffer*") Pointer jitterBuffer, int request, @Cast("void *") Pointer pointer);
    private static native int jitter_buffer_update_delay(@Cast("JitterBuffer*") Pointer jitterBuffer, JitterBufferPacket packet, IntPointer startOffset);


}
