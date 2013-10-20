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

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.morlunk.jumble.net.JumbleUDPMessageType;

/**
 * Created by andrew on 23/08/13.
 */
public class AudioInput implements Runnable {
    private interface JumbleVoiceListener {
        public void onFrameEncoded(byte[] data, int frameSize);
    }

//    private SWIGTYPE_p_OpusEncoder mOpusEncoder;
//    private com.morlunk.jumble.audio.celt11.SWIGTYPE_p_CELTEncoder mCELTBetaEncoder;
//    private com.morlunk.jumble.audio.celt7.SWIGTYPE_p_CELTMode mCELTAlphaMode;
//    private com.morlunk.jumble.audio.celt7.SWIGTYPE_p_CELTEncoder mCELTAlphaEncoder;

    private JumbleVoiceListener mListener;

    private AudioRecord mAudioRecord;
    private int mMinBufferSize;
    private int mInputSampleRate = 44100;
    private int mFrameSize = Audio.FRAME_SIZE;

    private JumbleUDPMessageType mCodec;

    public AudioInput(JumbleUDPMessageType codec, JumbleVoiceListener listener) {
        mCodec = codec;
        mListener = listener;
        int[] error = new int[1];
//        switch (codec) {
//            case UDPVoiceOpus:
//                mOpusEncoder = Opus.opus_encoder_create(Audio.SAMPLE_RATE, 1, OpusConstants.OPUS_APPLICATION_VOIP, error);
//                break;
//            case UDPVoiceCELTBeta:
//                mCELTBetaEncoder = CELT11.celt_encoder_create(Audio.SAMPLE_RATE, 1, error);
//                break;
//            case UDPVoiceCELTAlpha:
//                mCELTAlphaMode = CELT7.celt_mode_create(Audio.SAMPLE_RATE, Audio.FRAME_SIZE, error);
//                mCELTAlphaEncoder = CELT7.celt_encoder_create(mCELTAlphaMode, 1, error);
//                break;
//            case UDPVoiceSpeex:
//                // TODO
//                break;
//        }

        mMinBufferSize = AudioRecord.getMinBufferSize(mInputSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, mInputSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mMinBufferSize);
    }

    public void startRecording() {
        mAudioRecord.startRecording();
    }

    public void stopRecording() {
        mAudioRecord.stop();
    }

    @Override
    public void run() {
        while(mAudioRecord.getState() == AudioRecord.RECORDSTATE_RECORDING) {
            short[] audioData = new short[mFrameSize];
            byte[] encodedData = new byte[512];
            if(mAudioRecord.read(audioData, 0, mFrameSize) == AudioRecord.SUCCESS) {
                switch (mCodec) {
                    case UDPVoiceOpus:
//                        Opus.opus_encode(mOpusEncoder, audioData, mFrameSize, encodedData, 512);
                        break;
                    case UDPVoiceCELTBeta:
                        break;
                    case UDPVoiceCELTAlpha:
                        break;
                    case UDPVoiceSpeex:
                        break;
                }
            }
        }
    }
}
