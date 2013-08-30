/*
 * Copyright (C) 2013 Andrew Comminos
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

import com.morlunk.jumble.audio.opus.SWIGTYPE_p_OpusEncoder;
import com.morlunk.jumble.net.JumbleUDPMessageType;

/**
 * Created by andrew on 23/08/13.
 */
public class AudioInput {
    private SWIGTYPE_p_OpusEncoder mOpusEncoder;
    private com.morlunk.jumble.audio.celt11.SWIGTYPE_p_CELTEncoder mCelt11Encoder;
    private com.morlunk.jumble.audio.celt7.SWIGTYPE_p_CELTEncoder mCelt7Encoder;

    private JumbleUDPMessageType mCodec;

    public AudioInput(JumbleUDPMessageType codec) {
        mCodec = codec;
        switch (codec) {
            case UDPVoiceOpus:
                break;
            case UDPVoiceCELTBeta:
                break;
            case UDPVoiceCELTAlpha:
                break;
            case UDPVoiceSpeex:
                break;
        }
    }

    public void startRecording() {

    }

    public void stopRecording() {
        
    }
}
