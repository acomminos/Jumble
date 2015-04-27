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

package com.morlunk.jumble.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * User talk state.
 * Created by andrew on 19/04/15.
 */
public enum TalkState implements Parcelable {
    TALKING,
    SHOUTING,
    PASSIVE,
    WHISPERING;

    public static Creator<TalkState> CREATOR = new Creator<TalkState>() {
        @Override
        public TalkState createFromParcel(Parcel source) {
            return TalkState.values()[source.readInt()];
        }

        @Override
        public TalkState[] newArray(int size) {
            return new TalkState[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(ordinal());
    }
}
