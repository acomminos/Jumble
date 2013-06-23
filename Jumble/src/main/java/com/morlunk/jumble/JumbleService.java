/*
 * Copyright (C) 2013 Andrew Comminos
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

package com.morlunk.jumble;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.ChannelManager;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.protobuf.Mumble;

import java.security.Security;
import java.util.List;

public class JumbleService extends Service {

    static {
        // Use Spongy Castle for crypto implementation so we can create and manage PKCS #12 (.p12) certificates.
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    private JumbleParams mParams;
    private ChannelManager mChannelManager;

    private IJumbleService.Stub mBinder = new IJumbleService.Stub() {

        @Override
        public void connect(JumbleParams params) throws RemoteException {
            mParams = params;
        }

        @Override
        public User getUserWithId(int id) throws RemoteException {
            return null;
        }

        @Override
        public Channel getChannelWithId(int id) throws RemoteException {
            return null;
        }
    };

    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
