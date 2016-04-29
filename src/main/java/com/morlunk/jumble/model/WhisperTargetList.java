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

package com.morlunk.jumble.model;

import java.util.List;

/**
 * A simple implementation of a fixed-size whisper target list using a bit vector.
 * Created by andrew on 29/04/16.
 */
public class WhisperTargetList {
    public static final byte TARGET_MIN = 1;
    public static final byte TARGET_MAX = 30;

    private final WhisperTarget[] mActiveTargets;
    // Mumble stores voice targets using a 5-bit identifier.
    // Use a bit vector to represent this 32-element range.
    private int mTakenIds;

    public WhisperTargetList() {
        mActiveTargets = new WhisperTarget[TARGET_MAX - TARGET_MIN + 1];
        clear();
    }

    /**
     * Assigns the target to a slot.
     * @param target The whisper target to assign.
     * @return The slot number in range [1, 30].
     */
    public byte append(WhisperTarget target) {
        byte freeId = -1;
        for (byte i = TARGET_MIN; i < TARGET_MAX; i++) {
            if ((mTakenIds & (1 << i)) == 0) {
                freeId = i;
                break;
            }
        }
        if (freeId != -1) {
            mActiveTargets[freeId - TARGET_MIN] = target;
        }

        return freeId;
    }

    public WhisperTarget get(byte id) {
        if ((mTakenIds & (1 << id)) > 0)
            return null;
        return mActiveTargets[id - TARGET_MIN];
    }

    public void free(byte slot) {
        if (slot < TARGET_MIN || slot > TARGET_MAX)
            throw new IllegalArgumentException();

        mTakenIds &= ~(1 << slot);
    }

    public int spaceRemaining() {
        int counter = 0;
        for (byte i = TARGET_MIN; i < TARGET_MAX; i++) {
            if ((mTakenIds & (1 << i)) == 0) {
                counter++;
            }
        }
        return counter;
    }

    public void clear() {
        // Slots 0 and 31 are non-whisper targets.
        mTakenIds = 1 | (1 << 31);
    }
}
