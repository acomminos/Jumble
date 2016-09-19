/*
 * Copyright (C) 2016 Andrew Comminos <andrew@comminos.com>
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

package com.morlunk.jumble.audio.inputmode;

import java.util.concurrent.locks.Condition;

/**
 * A talk state engine, providing information regarding when it is appropriate to send audio.
 * Created by andrew on 13/02/16.
 */
public interface IInputMode {
    /**
     * Called when new input is received from the audio recording thread.
     * @param pcm PCM data.
     * @param length The number of shorts in the PCM data.
     * @return true if the input should be transmitted.
     */
    boolean shouldTransmit(short[] pcm, int length);

    /**
     * Called before any audio processing to wait for a change in input availability.
     * For example, a push to talk implementation will block the audio input thread until the
     * button has been activated. Other implementations may do nothing.
     *
     * This function should return immediately when shouldTransmit is returning true.
     */
    void waitForInput();
}
