/*
 * Copyright (C) 2015 Andrew Comminos
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

package com.morlunk.jumble.test;

import android.content.Intent;
import android.os.RemoteException;
import android.test.ServiceTestCase;

import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Server;

/**
 * Tests to ensure the integrity of {@link JumbleService}'s state.
 * Created by andrew on 02/02/15.
 */
public class JumbleServiceTest extends ServiceTestCase<JumbleService> {
    private static final Server DUMMY_SERVER = new Server(-1, "dummy","example.com", 64738,
            "dummy_user", "dummy_pass");

    public JumbleServiceTest() {
        super(JumbleService.class);
    }

    /**
     * Tests the state of a JumbleService prior to connection.
     */
    public void testPreconnection() throws RemoteException {
        Intent intent = new Intent(getContext(), JumbleService.class);
        intent.putExtra(JumbleService.EXTRAS_SERVER, DUMMY_SERVER);
        startService(intent);
        IJumbleService service = getService();
        assertFalse(service.isReconnecting());
        assertNull(service.getConnectionError());
        assertEquals(JumbleService.ConnectionState.DISCONNECTED, service.getConnectionState());
        assertEquals(DUMMY_SERVER, service.getTargetServer());
    }

}
