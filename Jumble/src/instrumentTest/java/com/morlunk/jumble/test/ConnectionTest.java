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

package com.morlunk.jumble.test;

import android.content.Intent;
import android.test.ActivityTestCase;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.JumbleParams;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.net.JumbleConnection;
import junit.framework.Test;

/**
 * Created by andrew on 09/07/13.
 */
public class ConnectionTest extends ServiceTestCase {
    public ConnectionTest() {
        super(JumbleService.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        JumbleParams params = new JumbleParams();

        Server server = new Server("Test Server", "morlunk.com", 64738, "Jumble_Client", null);
        params.server = server;

        Intent intent = new Intent(JumbleService.ACTION_CONNECT);
        intent.putExtra(JumbleService.EXTRA_PARAMS, params);

        startService(intent);
    }

    @LargeTest
    public void testConnection() throws JumbleConnection.JumbleConnectionException, InterruptedException {
        JumbleService service = (JumbleService) getService();
        service.connect();
        while(!service.isConnected()) {
            Log.v(Constants.TAG, "Not connected");
            Thread.sleep(10000);
        }
    }
}
