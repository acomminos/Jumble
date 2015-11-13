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

package com.morlunk.jumble;

/**
 * @deprecated Constant values should be associated with the class in which they are used.
 */
public class Constants {
    public static final int PROTOCOL_MAJOR = 1;
    public static final int PROTOCOL_MINOR = 2;
    public static final int PROTOCOL_PATCH = 5;

    public static final int TRANSMIT_VOICE_ACTIVITY = 0;
    public static final int TRANSMIT_PUSH_TO_TALK = 1;
    public static final int TRANSMIT_CONTINUOUS = 2;

    public static final int PROTOCOL_VERSION = (PROTOCOL_MAJOR << 16) | (PROTOCOL_MINOR << 8) | PROTOCOL_PATCH;
    public static final String PROTOCOL_STRING = PROTOCOL_MAJOR+ "." +PROTOCOL_MINOR+"."+PROTOCOL_PATCH;
    public static final int DEFAULT_PORT = 64738;

    public static final String TAG = "Jumble";
}
