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

package com.morlunk.jumble.net;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base class for TCP/UDP protocol implementations.
 * Provides a common threading model (single threaded queue for write)
 * Created by andrew on 25/03/14.
 * @deprecated This shouldn't be needed. Redundant inheritance with limited shared code.
 */
public abstract class JumbleNetworkThread implements Runnable {

    private ExecutorService mExecutor;
    private ExecutorService mSendExecutor;
    private ExecutorService mReceiveExecutor;
    private Handler mMainHandler;
    private boolean mInitialized;

    public JumbleNetworkThread() {
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    protected void startThreads() {
        if (mInitialized) {
            throw new IllegalArgumentException("Threads already initialized.");
        }
        mExecutor = Executors.newSingleThreadExecutor();
        mSendExecutor = Executors.newSingleThreadExecutor();
        mReceiveExecutor = Executors.newSingleThreadExecutor();
        mExecutor.execute(this);
        mInitialized = true;
    }

    protected void stopThreads() {
        if (!mInitialized) {
            throw new IllegalArgumentException("Threads already shutdown.");
        }
        mSendExecutor.shutdown();
        mReceiveExecutor.shutdownNow();
        mExecutor.shutdownNow();
        mSendExecutor = null;
        mReceiveExecutor = null;
        mExecutor = null;
        mInitialized = false;
    }

    protected void executeOnSendThread(Runnable r) {
        mSendExecutor.execute(r);
    }

    protected void executeOnReceiveThread(Runnable r) {
        mSendExecutor.execute(r);
    }

    protected void executeOnMainThread(Runnable r) {
        mMainHandler.post(r);
    }

    protected Handler getMainHandler() {
        return mMainHandler;
    }
}
