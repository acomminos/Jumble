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

import android.util.Log;

import com.morlunk.jumble.Constants;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An input mode that depends on a toggle, such as push to talk.
 * Created by andrew on 13/02/16.
 */
public class ToggleInputMode implements IInputMode {
    private boolean mInputOn;
    private final Lock mToggleLock;
    private final Condition mToggleCondition;

    public ToggleInputMode() {
        mInputOn = false;
        mToggleLock = new ReentrantLock();
        mToggleCondition = mToggleLock.newCondition();
    }

    public void toggleTalkingOn() {
        setTalkingOn(!mInputOn);
    }

    public boolean isTalkingOn() {
        return mInputOn;
    }

    public void setTalkingOn(boolean talking) {
        mToggleLock.lock();
        mInputOn = talking;
        mToggleCondition.signalAll();
        mToggleLock.unlock();
    }

    @Override
    public boolean shouldTransmit(short[] pcm, int length) {
        return mInputOn;
    }

    @Override
    public void waitForInput() {
        mToggleLock.lock();
        if (!mInputOn) {
            Log.v(Constants.TAG, "PTT: Suspending audio input.");
            long startTime = System.currentTimeMillis();
            try {
                mToggleCondition.await();
            } catch (InterruptedException e) {
                Log.w(Constants.TAG, "Blocking for PTT interrupted, likely due to input thread shutdown.");
            }
            Log.v(Constants.TAG, "PTT: Suspended audio input for " + (System.currentTimeMillis() - startTime) + "ms.");
        }
        mToggleLock.unlock();
    }
}
