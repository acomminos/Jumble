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

package com.morlunk.jumble.audio;

/**
 * Created by andrew on 05/04/15.
 */
public class AudioUser {
    private static final float AVERAGE_DECAY = 0.99f;

    private int mSession;
    /** The average amount of packets in the jitter buffer. */
    private float mAverageAvailable;

    public AudioUser(int session) {
        mSession = session;
        mAverageAvailable = 0.f;
    }

    public int getSession() {
        return mSession;
    }

    /**
     * Use the given packet data to update the average available packets.
     * If the number of packets present is greater than the accumulated average, update our average
     * to it- otherwise, decay our average by a factor of {@link #AVERAGE_DECAY}.
     * @param numPackets The number of packets present in this cycle of the jitter buffer.
     */
    public void updateAveragePackets(int numPackets) {
        mAverageAvailable = Math.max(numPackets, mAverageAvailable * AVERAGE_DECAY);
    }

    public int getAveragePackets() {
        return (int) Math.ceil(mAverageAvailable);
    }
}
