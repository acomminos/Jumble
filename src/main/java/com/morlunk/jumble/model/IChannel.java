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

package com.morlunk.jumble.model;

import java.util.List;

public interface IChannel {
    List<? extends IUser> getUsers();

    int getId();

    int getPosition();

    boolean isTemporary();

    IChannel getParent();

    String getName();

    String getDescription();

    byte[] getDescriptionHash();

    List<? extends IChannel> getSubchannels();

    int getSubchannelUserCount();

    List<? extends IChannel> getLinks();

    int getPermissions();
}
