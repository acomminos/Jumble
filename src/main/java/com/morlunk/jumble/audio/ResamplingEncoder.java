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

package com.morlunk.jumble.audio;

import com.morlunk.jumble.audio.javacpp.Speex;
import com.morlunk.jumble.exception.NativeAudioException;

/**
 * Wraps around another encoder, resampling up/down all input using the Speex resampler.
 * Created by andrew on 16/04/14.
 */
public class ResamplingEncoder implements IEncoder {

    private static final int SPEEX_RESAMPLE_QUALITY = 3;

    private IEncoder mEncoder;
    private Speex.SpeexResampler mResampler;

    public ResamplingEncoder(IEncoder encoder, int channels, int inputSampleRate, int targetSampleRate) {
        mEncoder = encoder;
        mResampler = new Speex.SpeexResampler(channels, inputSampleRate, targetSampleRate, SPEEX_RESAMPLE_QUALITY);
    }

    @Override
    public int encode(short[] input, int inputSize, byte[] output, int outputSize) throws NativeAudioException {
        mResampler.resample(input, input);
        return mEncoder.encode(input, inputSize, output, outputSize);
    }

    @Override
    public void setBitrate(int bitrate) {
        mEncoder.setBitrate(bitrate);
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
