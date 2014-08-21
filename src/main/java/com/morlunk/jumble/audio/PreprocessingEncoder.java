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
    public int encode(short[] input, int inputSize, byte[] output, int outputSize) throws NativeAudioException {
        mPreprocessor.preprocess(input);
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
        mPreprocessor.destroy();
        mEncoder.destroy();
        mPreprocessor = null;
        mEncoder = null;
    }
}
