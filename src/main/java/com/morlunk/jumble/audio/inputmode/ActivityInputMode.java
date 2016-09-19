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

/**
 * An input mode that sends audio if the amplitude exceeds a certain threshold.
 * Created by andrew on 13/02/16.
 */
public class ActivityInputMode implements IInputMode {
    // Continue speech for 250ms to prevent dropping.
    private static final int SPEECH_DELAY = (int) (0.25 * Math.pow(10, 9));

    private float mVADThreshold;
    private long mVADLastDetected;

    public ActivityInputMode(float detectionThreshold) {
        mVADThreshold = detectionThreshold;
    }

    @Override
    public boolean shouldTransmit(short[] pcm, int length) {
        // Use a logarithmic energy-based scale for VAD.
        float sum = 1.0f;
        for (int i = 0; i < length; i++) {
            sum += pcm[i] * pcm[i];
        }
        float micLevel = (float) Math.sqrt(sum / (float)length);
        float peakSignal = (float) (20.0f * Math.log10(micLevel / 32768.0f)) / 96.0f;
        boolean talking = (peakSignal + 1) >= mVADThreshold;

        // Record the last time where VAD was detected in order to prevent speech dropping.
        if(talking) {
            mVADLastDetected = System.nanoTime();
        }

        talking |= (System.nanoTime() - mVADLastDetected) < SPEECH_DELAY;

        return talking;
    }

    @Override
    public void waitForInput() {

    }

    public void setThreshold(float threshold) {
        mVADThreshold = threshold;
    }
}
