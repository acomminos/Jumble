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
import com.morlunk.jumble.audio.javacpp.CELT7;
import com.morlunk.jumble.exception.NativeAudioException;
import com.morlunk.jumble.net.PacketBuffer;
import com.morlunk.jumble.protocol.AudioHandler;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;

/**
* Created by andrew on 08/12/14.
*/
public class CELT7Encoder implements IEncoder {
    private final byte[][] mBuffer;
    private final int[] mPacketLengths;
    private final int mBufferSize;
    private final int mFramesPerPacket;
    private int mBufferedFrames;
    private boolean mReady;

    private Pointer mMode;
    private Pointer mState;

    public CELT7Encoder(int sampleRate, int frameSize, int channels,
                        int framesPerPacket, int bitrate, int maxBufferSize)
            throws NativeAudioException {
        mFramesPerPacket = framesPerPacket;
        mBufferSize = Math.min(maxBufferSize, bitrate / 800);
        mBuffer = new byte[framesPerPacket][mBufferSize];
        mPacketLengths = new int[framesPerPacket];
        mBufferedFrames = 0;

        IntPointer error = new IntPointer(1);
        error.put(0);
        mMode = CELT7.celt_mode_create(sampleRate, frameSize, error);
        if(error.get() < 0) throw new NativeAudioException("CELT 0.7.0 encoder initialization failed with error: "+error.get());
        mState = CELT7.celt_encoder_create(mMode, channels, error);
        if(error.get() < 0) throw new NativeAudioException("CELT 0.7.0 encoder initialization failed with error: "+error.get());
        CELT7.celt_encoder_ctl(mState, CELT7.CELT_SET_PREDICTION_REQUEST, 0);
        CELT7.celt_encoder_ctl(mState, CELT7.CELT_SET_VBR_RATE_REQUEST, bitrate);
    }

    @Override
    public int encode(short[] input, int inputSize) throws NativeAudioException {
        if (mBufferedFrames >= mFramesPerPacket) {
            throw new BufferOverflowException();
        }

        int result = CELT7.celt_encode(mState, input, null, mBuffer[mBufferedFrames], mBufferSize);
        if(result < 0) throw new NativeAudioException("CELT 0.7.0 encoding failed with error: "
                                                              + result);
        mPacketLengths[mBufferedFrames] = result;
        mBufferedFrames++;

        if (mBufferedFrames >= mFramesPerPacket)
            mReady = true;

        return result;
    }

    @Override
    public int getBufferedFrames() {
        return mBufferedFrames;
    }

    @Override
    public boolean isReady() {
        return mReady && mBufferedFrames > 0;
    }

    @Override
    public void getEncodedData(PacketBuffer packetBuffer) throws BufferUnderflowException {
        if (!mReady)
            throw new BufferUnderflowException();

        for (int x = 0; x < mBufferedFrames; x++) {
            byte[] frame = mBuffer[x];
            int length = mPacketLengths[x];
            int head = length;
            if(x < mBufferedFrames - 1)
                head |= 0x80;
            packetBuffer.append(head);
            packetBuffer.append(frame, length);
        }

        mBufferedFrames = 0;
        mReady = false;
    }

    @Override
    public void terminate() throws NativeAudioException {
        mReady = true;
    }

    @Override
    public void destroy() {
        CELT7.celt_encoder_destroy(mState);
        CELT7.celt_mode_destroy(mMode);
    }
}
