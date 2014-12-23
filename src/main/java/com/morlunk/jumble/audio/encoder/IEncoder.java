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

import com.morlunk.jumble.exception.NativeAudioException;
import com.morlunk.jumble.net.PacketBuffer;

import java.nio.BufferUnderflowException;

/**
 * IEncoder provides an interface for native audio encoders to buffer and serve encoded audio
 * data.
 * Created by andrew on 07/03/14.
 */
public interface IEncoder {
    /**
     * Encodes the provided input and returns the number of bytes encoded.
     * @param input The short PCM data to encode.
     * @param inputSize The number of samples to encode.
     * @return The number of bytes encoded.
     * @throws NativeAudioException if there was an error encoding.
     */
    public int encode(short[] input, int inputSize) throws NativeAudioException;

    /**
     * @return the number of audio frames buffered.
     */
    public int getBufferedFrames();

    /**
     * @return true if enough buffered audio has been encoded to send to the server.
     */
    public boolean isReady();

    /**
     * Writes the currently encoded audio data into the provided {@link PacketBuffer}.
     * Use {@link #isReady()} to determine whether or not this should be called.
     * @throws BufferUnderflowException if insufficient audio data has been buffered.
     */
    public void getEncodedData(PacketBuffer packetBuffer) throws BufferUnderflowException;

    /**
     * Informs the encoder that there are no more audio packets to be queued. Often, this will
     * trigger an encode operation, changing the result of {@link #isReady()}.
     */
    public void terminate() throws NativeAudioException;

    /**
     * Destroys the encoder, cleaning up natively allocated resources.
     */
    public void destroy();
}
