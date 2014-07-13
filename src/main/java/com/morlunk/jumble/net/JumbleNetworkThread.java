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

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base class for TCP/UDP protocol implementations.
 * Provides a common threading model (single threaded queue for write)
 * Created by andrew on 25/03/14.
 */
public abstract class JumbleNetworkThread implements Runnable {

    private final ExecutorService mExecutor;
    private final Executor mSendExecutor;
    private final Handler mMainHandler;

    public JumbleNetworkThread() {
        mExecutor = Executors.newSingleThreadExecutor();
        mSendExecutor = Executors.newSingleThreadExecutor();
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    protected void startThread() {
        mExecutor.execute(this);
    }

    protected void executeOnSendThread(Runnable r) {
        mSendExecutor.execute(r);
    }

    protected void executeOnMainThread(Runnable r) {
        mMainHandler.post(r);
    }

    protected void stopReadThread() {
        mExecutor.shutdownNow();
    }

    protected Handler getMainHandler() {
        return mMainHandler;
    }
}
