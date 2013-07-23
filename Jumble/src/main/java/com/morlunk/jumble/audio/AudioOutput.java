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
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.*;
import android.os.Process;
import android.util.SparseArray;

import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleMessageHandler;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.net.PacketDataStream;

import java.nio.ByteBuffer;

/**
 * Created by andrew on 16/07/13.
 */
public class AudioOutput extends JumbleMessageHandler.Stub implements Runnable {

    private JumbleService mService;
    private SparseArray<AudioOutputSpeech> mAudioOutputs = new SparseArray<AudioOutputSpeech>();
    private AudioTrack mAudioTrack;

    private Thread mThread;
    private boolean mRunning = false;

    public AudioOutput(JumbleService service) {
        mService = service;
        int bufferSize = AudioTrack.getMinBufferSize(Audio.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2;

        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                Audio.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM);
    }

    public void startPlaying() {
        if(mRunning)
            return;

        mThread = new Thread(this);
        mThread.start();
    }

    public void stopPlaying() {
        mRunning = false;
        mThread = null;
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        mRunning = true;
        while(mRunning) {

        }
    }

    @Override
    public void messageVoiceData(byte[] data) {
        if(!mRunning)
            return;

        JumbleUDPMessageType dataType = JumbleUDPMessageType.values()[data[0] >> 5 & 0x7];
        int msgFlags = data[0] & 0x1f;
        byte[] voiceData = new byte[data.length-5];
        System.arraycopy(data, 1, voiceData, 0, voiceData.length);

        PacketDataStream pds = new PacketDataStream(voiceData, voiceData.length);
        int session = pds.next();
        User user = mService.getUserManager().getUser(session);
        if(user != null && !user.isLocalMuted()) {
            // TODO check for whispers here
            int seq = pds.next();
            ByteBuffer packet = ByteBuffer.allocateDirect(pds.left() + 1);
            packet.putInt(msgFlags);
            packet.put(pds.dataBlock(pds.left()));

            synchronized (mAudioOutputs) {
                AudioOutputSpeech aop = mAudioOutputs.get(session);
                if(aop == null || aop.getCodec() != dataType) {
                    if(aop.getCodec() != dataType)
                        aop.destroy();
                    aop = new AudioOutputSpeech(session, dataType);
                    mAudioOutputs.put(session, aop);
                }

                aop.addFrameToBuffer(packet.array(), seq);
            }
        }

    }
}
