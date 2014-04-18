/*
 * Copyright (C) 2014 Andrew Comminos
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

package com.morlunk.jumble.audio;

import com.morlunk.jumble.audio.javacpp.Speex;

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
    public void encode(short[] input, int inputSize, byte[] output, int outputSize) throws NativeAudioException {
        mResampler.resample(input, input);
        mEncoder.encode(input, inputSize, output, outputSize);
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
