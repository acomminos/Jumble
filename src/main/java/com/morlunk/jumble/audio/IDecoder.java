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

import com.morlunk.jumble.exception.NativeAudioException;

import java.nio.ByteBuffer;

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
    public int decodeFloat(ByteBuffer input, int inputSize, float[] output, int frameSize) throws NativeAudioException;

    /**
     * Decodes the encoded data provided into short PCM data.
     * @param input A byte array of encoded data of size inputSize.
     * @param inputSize The size of the encoded data.
     * @param output An initialized output array at least frameSize for short PCM data.
     * @param frameSize The maximum frame size possible.
     * @return The number of decoded samples.
     * @throws NativeAudioException if encoding failed.
     */
    public int decodeShort(ByteBuffer input, int inputSize, short[] output, int frameSize) throws NativeAudioException;

    /**
     * Deallocates native resources. The decoder must no longer be called after this.
     */
    public void destroy();
}
