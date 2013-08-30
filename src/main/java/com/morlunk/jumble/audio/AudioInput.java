/*
 * Copyright (C) 2013 Andrew Comminos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
