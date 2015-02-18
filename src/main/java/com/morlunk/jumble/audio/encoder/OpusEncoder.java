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

package com.morlunk.jumble.audio.encoder;

import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Pointer;
import com.morlunk.jumble.audio.javacpp.Opus;
import com.morlunk.jumble.exception.NativeAudioException;
import com.morlunk.jumble.net.PacketBuffer;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.Arrays;

/**
* Created by andrew on 08/12/14.
*/
public class OpusEncoder implements IEncoder {
    private final byte[] mBuffer;
    private final short[] mAudioBuffer;
    private final int mFramesPerPacket;
    private final int mFrameSize;

    // Stateful
    private int mBufferedFrames;
    private int mEncodedLength;
    private boolean mTerminated;

    private Pointer mState;

    public OpusEncoder(int sampleRate, int channels, int frameSize, int framesPerPacket,
                       int bitrate, int maxBufferSize) throws NativeAudioException {
        mBuffer = new byte[maxBufferSize];
        mAudioBuffer = new short[framesPerPacket * frameSize];
        mFramesPerPacket = framesPerPacket;
        mFrameSize = frameSize;
        mBufferedFrames = 0;
        mEncodedLength = 0;
        mTerminated = false;

        IntPointer error = new IntPointer(1);
        error.put(0);
        mState = Opus.opus_encoder_create(sampleRate, channels, Opus.OPUS_APPLICATION_VOIP, error);
        if(error.get() < 0) throw new NativeAudioException("Opus encoder initialization failed with error: "+error.get());
        Opus.opus_encoder_ctl(mState, Opus.OPUS_SET_VBR_REQUEST, 0);
        Opus.opus_encoder_ctl(mState, Opus.OPUS_SET_BITRATE_REQUEST, bitrate);
    }

    @Override
    public int encode(short[] input, int inputSize) throws NativeAudioException {
        if (mBufferedFrames >= mFramesPerPacket) {
            throw new BufferOverflowException();
        }

        if (inputSize != mFrameSize) {
            throw new IllegalArgumentException("This Opus encoder implementation requires a " +
                                                       "constant frame size.");
        }

        mTerminated = false;
        System.arraycopy(input, 0, mAudioBuffer, mFrameSize * mBufferedFrames, mFrameSize);
        mBufferedFrames++;

        if (mBufferedFrames == mFramesPerPacket) {
            return encode();
        }
        return 0;
    }

    private int encode() throws NativeAudioException {
        if (mBufferedFrames < mFramesPerPacket) {
            // If encoding is done before enough frames are buffered, fill rest of packet.
            Arrays.fill(mAudioBuffer, mFrameSize * mBufferedFrames, mAudioBuffer.length, (short)0);
            mBufferedFrames = mFramesPerPacket;
        }
        int result = Opus.opus_encode(mState, mAudioBuffer, mFrameSize * mBufferedFrames,
                                             mBuffer, mBuffer.length);
        if(result < 0) throw new NativeAudioException("Opus encoding failed with error: "
                                                              + result);
        mEncodedLength = result;
        return result;
    }

    @Override
    public int getBufferedFrames() {
        return mBufferedFrames;
    }

    @Override
    public boolean isReady() {
        return mEncodedLength > 0;
    }

    @Override
    public void getEncodedData(PacketBuffer packetBuffer) throws BufferUnderflowException {
        if (!isReady()) {
            throw new BufferUnderflowException();
        }

        int size = mEncodedLength;
        if(mTerminated)
            size |= 1 << 13;
        packetBuffer.writeLong(size);
        packetBuffer.append(mBuffer, mEncodedLength);

        mBufferedFrames = 0;
        mEncodedLength = 0;
        mTerminated = false;
    }

    @Override
    public void terminate() throws NativeAudioException {
        mTerminated = true;
        if (mBufferedFrames > 0 && !isReady()) {
            // Perform encode operation on remaining audio if available.
            encode();
        }
    }

    public int getBitrate() {
        IntPointer ptr = new IntPointer(1);
        Opus.opus_encoder_ctl(mState, Opus.OPUS_GET_BITRATE_REQUEST, ptr);
        return ptr.get();
    }

    @Override
    public void destroy() {
        Opus.opus_encoder_destroy(mState);
    }
}
