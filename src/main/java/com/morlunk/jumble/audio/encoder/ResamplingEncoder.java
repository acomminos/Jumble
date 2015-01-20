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

import com.morlunk.jumble.audio.javacpp.Speex;
import com.morlunk.jumble.exception.NativeAudioException;
import com.morlunk.jumble.net.PacketBuffer;

import java.nio.BufferUnderflowException;

/**
 * Wraps around another encoder, resampling up/down all input using the Speex resampler.
 * Created by andrew on 16/04/14.
 */
public class ResamplingEncoder implements IEncoder {
    private static final int SPEEX_RESAMPLE_QUALITY = 3;

    private IEncoder mEncoder;
    private Speex.SpeexResampler mResampler;
    private final int mInputSampleRate;
    private final int mTargetSampleRate;
    private final int mTargetFrameSize;
    private final short[] mResampleBuffer;

    public ResamplingEncoder(IEncoder encoder, int channels, int inputSampleRate, int targetFrameSize, int targetSampleRate) {
        mEncoder = encoder;
        mInputSampleRate = inputSampleRate;
        mTargetSampleRate = targetSampleRate;
        mTargetFrameSize = targetFrameSize;
        mResampleBuffer = new short[mTargetFrameSize];
        mResampler = new Speex.SpeexResampler(channels, inputSampleRate, targetSampleRate, SPEEX_RESAMPLE_QUALITY);
    }

    @Override
    public int encode(short[] input, int inputSize) throws NativeAudioException {
        mResampler.resample(input, mResampleBuffer);
        return mEncoder.encode(mResampleBuffer, mTargetFrameSize);
    }

    @Override
    public int getBufferedFrames() {
        return mEncoder.getBufferedFrames();
    }

    @Override
    public boolean isReady() {
        return mEncoder.isReady();
    }

    @Override
    public void getEncodedData(PacketBuffer packetBuffer) throws BufferUnderflowException {
        mEncoder.getEncodedData(packetBuffer);
    }

    @Override
    public void terminate() throws NativeAudioException {
        mEncoder.terminate();
    }

    public void setEncoder(IEncoder encoder) {
        if(mEncoder != null) mEncoder.destroy();
        mEncoder = encoder;
    }

    @Override
    public void destroy() {
        mResampler.destroy();
        mEncoder.destroy();
        mResampler = null;
        mEncoder = null;
    }
}
