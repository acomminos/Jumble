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

import android.os.Parcel;
import android.os.Parcelable;

import com.morlunk.jumble.protobuf.Mumble;

import java.util.List;

/**
 * An abstraction around a channel whisper target.
 * Created by andrew on 28/04/16.
 */
public class WhisperTargetChannel implements WhisperTarget {
    private final IChannel mChannel;
    private final boolean mIncludeLinked;
    private final boolean mIncludeSubchannels;
    private final String mGroupRestriction;

    public WhisperTargetChannel(final IChannel channel, boolean includeLinked,
                                boolean includeSubchannels, String groupRestriction) {
        mChannel = channel;
        mIncludeLinked = includeLinked;
        mIncludeSubchannels = includeSubchannels;
        mGroupRestriction = groupRestriction;
    }

    @Override
    public Mumble.VoiceTarget.Target createTarget() {
        Mumble.VoiceTarget.Target.Builder vtb = Mumble.VoiceTarget.Target.newBuilder();
        vtb.setLinks(mIncludeLinked);
        vtb.setChildren(mIncludeSubchannels);
        if (mGroupRestriction != null)
            vtb.setGroup(mGroupRestriction);
        vtb.setChannelId(mChannel.getId());
        return vtb.build();
    }

    @Override
    public String getName() {
        return mChannel.getName();
    }
}
