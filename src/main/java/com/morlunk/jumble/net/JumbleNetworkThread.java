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

package com.morlunk.jumble.net;

import android.os.Handler;
import android.os.Looper;

import java.net.InetAddress;
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

    protected Handler getMainHandler() {
        return mMainHandler;
    }
}
