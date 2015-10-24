/*
 * Copyright (C) 2015 Andrew Comminos <andrew@comminos.com>
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

import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.User;

import junit.framework.TestCase;

/**
 * Tests the Channel-User tree model.
 * Created by andrew on 24/10/15.
 */
public class ModelTest extends TestCase {

    public void testUserAddRemove() {
        Channel root = new Channel(0, false);
        User user = new User(0, "Test user");
        user.setChannel(root);
        assertEquals("Channel user list count is sane", 1, root.getUsers().size());
        assertEquals("Channel subchannel user count is sane", 1, root.getSubchannelUserCount());

        Channel sub = new Channel(1, false);
        root.addSubchannel(sub);
        User subuser = new User(1, "Test user in subchannel");
        subuser.setChannel(sub);
        assertEquals("Adding a user to a subchannel doesn't affect the number of direct children of the root", 1, root.getUsers().size());
        assertEquals("Adding a user to a subchannel updates the recursive user count", 2, root.getSubchannelUserCount());

        user.setChannel(sub);
        assertEquals("Moving a user to a subchannel updates the number of children of the root", 0, root.getUsers().size());
        assertEquals("Moving a user to a subchannel does not change the recursive user count of the root", 2, root.getSubchannelUserCount());
        assertEquals("Subchannel user count is sane", 2, sub.getUsers().size());
    }
}
