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

/**
 * Created by andrew on 15/10/15.
 */
public interface IUser {
    int getSession();

    Channel getChannel();

    int getUserId();

    String getName();

    String getComment();

    byte[] getCommentHash();

    byte[] getTexture();

    byte[] getTextureHash();

    String getHash();

    boolean isMuted();

    boolean isDeafened();

    boolean isSuppressed();

    boolean isSelfMuted();

    boolean isSelfDeafened();

    boolean isPrioritySpeaker();

    boolean isRecording();

    boolean isLocalMuted();

    boolean isLocalIgnored();

    void setLocalMuted(boolean muted);

    void setLocalIgnored(boolean ignored);

    TalkState getTalkState();
}
