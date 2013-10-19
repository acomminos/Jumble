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
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.IJumbleObserver;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleMessageHandler;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.net.PacketDataStream;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by andrew on 16/07/13.
 */
public class AudioOutput extends JumbleMessageHandler.Stub implements Runnable, AudioOutputSpeech.TalkStateListener {

    /** Number of nanoseconds until sleeping audio output thread. */
    private static final long SLEEP_THRESHOLD = 2000000000L;

    private JumbleService mService;
    private ConcurrentHashMap<Integer, AudioOutputSpeech> mAudioOutputs = new ConcurrentHashMap<Integer, AudioOutputSpeech>();
    private AudioTrack mAudioTrack;

    private Thread mThread;
    private Object mInactiveLock = new Object(); // Lock that the audio thread waits on when there's no audio to play. Wake when we get a frame.
    private boolean mRunning = false;
    private long mLastPacket; // Time that the last packet was received, in nanoseconds

    public AudioOutput(JumbleService service) {
        mService = service;
        int bufferSize = AudioTrack.getMinBufferSize(Audio.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2;

        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                Audio.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Audio.FRAME_SIZE*12,
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
        for(AudioOutputSpeech s : mAudioOutputs.values())
            s.destroy();
    }

    @Override
    public void run() {
        Log.v(Constants.TAG, "Started audio output thread.");
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        mRunning = true;
        mAudioTrack.play();

        final short[] mix = new short[Audio.FRAME_SIZE*12];

        while(mRunning) {
            Arrays.fill(mix, (short)0);
            boolean play = mix(mix, mix.length);
            if(play) {
                mAudioTrack.write(mix, 0, mix.length);
            } else if(System.nanoTime()-mLastPacket > SLEEP_THRESHOLD) {
                Log.v(Constants.TAG, "Pausing audio output thread.");
                synchronized (mInactiveLock) {
                    try {
                        mInactiveLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Log.v(Constants.TAG, "Resuming audio output thread.");
            }
        }

        mAudioTrack.flush();
        mAudioTrack.stop();
    }

    private boolean mix(short[] outBuffer, int bufferSize) {
        List<AudioOutputSpeech> mix = new ArrayList<AudioOutputSpeech>();
        List<AudioOutputSpeech> del = new ArrayList<AudioOutputSpeech>();

        // TODO add priority speaker support

        for(AudioOutputSpeech speech : mAudioOutputs.values()) {
            if(!speech.needSamples(bufferSize))
                del.add(speech);
            else
                mix.add(speech);
        }

        if(!mix.isEmpty()) {
            for(AudioOutputSpeech speech : mix) {
                float[] buffer = speech.getBuffer();
                for(int i = 0; i < bufferSize; i++) {
                    short pcm = (short) (buffer[i]*Short.MAX_VALUE); // Convert float to short
                    pcm = (short) Math.max(Math.min(pcm, Short.MAX_VALUE), Short.MIN_VALUE); // Clip audio
                    outBuffer[i] += pcm;
                }
            }
        }

        for(AudioOutputSpeech speech : del)
            mAudioOutputs.remove(speech.getSession());

        return !mix.isEmpty();
    }

    @Override
    public void messageVoiceData(byte[] data) {
        if(!mRunning)
            return;

        JumbleUDPMessageType dataType = JumbleUDPMessageType.values()[data[0] >> 5 & 0x7];
        int msgFlags = data[0] & 0x1f;
        byte[] voiceData = new byte[data.length-1];
        System.arraycopy(data, 1, voiceData, 0, voiceData.length);

        PacketDataStream pds = new PacketDataStream(voiceData, voiceData.length);
        int session = (int) pds.readLong();
        User user = mService.getUserHandler().getUser(session);
        if(user != null && !user.isLocalMuted()) {
            // TODO check for whispers here
            int seq = (int) pds.readLong();
            ByteBuffer packet = ByteBuffer.allocate(pds.left() + 1);
            packet.put((byte)msgFlags);
            packet.put(pds.dataBlock(pds.left()));

            AudioOutputSpeech aop = mAudioOutputs.get(session);
            if(aop != null && aop.getCodec() != dataType) {
                aop.destroy();
                aop = null;
            }
            if(aop == null) {
                aop = new AudioOutputSpeech(session, dataType, this);
                mAudioOutputs.put(session, aop);
            }

            aop.addFrameToBuffer(packet.array(), seq);

            mLastPacket = System.nanoTime();
            synchronized (mInactiveLock) {
                mInactiveLock.notify();
            }
        }

    }

    @Override
    public void onTalkStateUpdated(int session, User.TalkState state) {
        final User user = mService.getUserHandler().getUser(session);
        if(user.getTalkState() != state) {
            user.setTalkState(state);
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    mService.notifyObservers(new JumbleService.ObserverRunnable() {
                        @Override
                        public void run(IJumbleObserver observer) throws RemoteException {
                            observer.onUserTalkStateUpdated(user);
                        }
                    });;
                }
            });
        }
    }
}
