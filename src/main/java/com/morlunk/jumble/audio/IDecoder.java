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

import com.morlunk.jumble.exception.NativeAudioException;

/**
 * Created by andrew on 07/03/14.
 */
public interface IDecoder {
    /**
     * Decodes the encoded data provided into float PCM data.
     * @param input A byte array of encoded data of size inputSize.
     * @param inputSize The size of the encoded data.
     * @param output An initialized output array at least frameSize for float PCM data.
     * @param frameSize The maximum frame size possible.
     * @return The number of decoded samples.
     * @throws com.morlunk.jumble.exception.NativeAudioException if encoding failed.
     */
    public int decodeFloat(byte[] input, int inputSize, float[] output, int frameSize) throws NativeAudioException;

    /**
     * Decodes the encoded data provided into short PCM data.
     * @param input A byte array of encoded data of size inputSize.
     * @param inputSize The size of the encoded data.
     * @param output An initialized output array at least frameSize for short PCM data.
     * @param frameSize The maximum frame size possible.
     * @return The number of decoded samples.
     * @throws NativeAudioException if encoding failed.
     */
    public int decodeShort(byte[] input, int inputSize, short[] output, int frameSize) throws NativeAudioException;

    /**
     * Deallocates native resources. The decoder must no longer be called after this.
     */
    public void destroy();
}
