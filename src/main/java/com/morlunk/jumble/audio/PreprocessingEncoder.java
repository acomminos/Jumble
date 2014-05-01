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

import com.googlecode.javacpp.IntPointer;
import com.morlunk.jumble.audio.javacpp.Speex;
import com.morlunk.jumble.exception.NativeAudioException;

/**
 * Wrapper performing preprocessing options on the nested encoder.
 * Uses Speex preprocessor.
 * Created by andrew on 17/04/14.
 */
public class PreprocessingEncoder implements IEncoder {
    private IEncoder mEncoder;
    private Speex.SpeexPreprocessState mPreprocessor;

    public PreprocessingEncoder(IEncoder encoder, int frameSize, int sampleRate) {
        mEncoder = encoder;
        mPreprocessor = new Speex.SpeexPreprocessState(frameSize, sampleRate);

        IntPointer arg = new IntPointer(1);
        arg.put(0);
        mPreprocessor.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_VAD, arg);
        arg.put(1);
        mPreprocessor.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_AGC, arg);
        mPreprocessor.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_DENOISE, arg);
        mPreprocessor.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_DEREVERB, arg);
        arg.put(30000);
        mPreprocessor.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_AGC_TARGET, arg);
        // TODO AGC max gain, decrement, noise suppress, echo
    }

    @Override
    public void encode(short[] input, int inputSize, byte[] output, int outputSize) throws NativeAudioException {
        mPreprocessor.preprocess(input);
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
        mPreprocessor.destroy();
        mEncoder.destroy();
        mPreprocessor = null;
        mEncoder = null;
    }
}
