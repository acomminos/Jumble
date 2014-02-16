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
import android.util.SparseArray;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.IJumbleObserver;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.net.PacketDataStream;
import com.morlunk.jumble.protocol.JumbleTCPMessageListener;
import com.morlunk.jumble.protocol.JumbleUDPMessageListener;
import com.morlunk.jumble.protocol.ProtocolHandler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by andrew on 16/07/13.
 */
public class AudioOutput extends ProtocolHandler implements Runnable, AudioOutputSpeech.TalkStateListener, JumbleUDPMessageListener {

    /** Number of nanoseconds until sleeping audio output thread. */
    private static final long SLEEP_THRESHOLD = 3000000000L;

    private SparseArray<AudioOutputSpeech> mAudioOutputs = new SparseArray<AudioOutputSpeech>();
    private AudioTrack mAudioTrack;
    private int mAudioStream;
    private int mBufferSize;
    private Thread mThread;
    private Object mInactiveLock = new Object(); // Lock that the audio thread waits on when there's no audio to play. Wake when we get a frame.
    private boolean mRunning = false;
    private long mLastPacket; // Time that the last packet was received, in nanoseconds
    private List<AudioOutputSpeech> mMixBuffer = new ArrayList<AudioOutputSpeech>();
    private List<AudioOutputSpeech> mDelBuffer = new ArrayList<AudioOutputSpeech>();
    private Handler mMainHandler;

    public AudioOutput(JumbleService service, int audioStream) {
        super(service);
        mMainHandler = new Handler(Looper.getMainLooper());
        mAudioStream = audioStream;
    }

    public void startPlaying(boolean scoEnabled) {
        if(mRunning)
            return;

        int minBufferSize = AudioTrack.getMinBufferSize(Audio.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mBufferSize = minBufferSize;
//        mBufferSize = Math.max(minBufferSize, Audio.FRAME_SIZE * 12); // Make the buffer size a multiple of the largest possible frame.
        Log.v(Constants.TAG, "Using buffer size "+mBufferSize+", system's min buffer size: "+minBufferSize);

        // Force STREAM_VOICE_CALL for Bluetooth, as it's all that will work.
        mAudioTrack = new AudioTrack(scoEnabled ? AudioManager.STREAM_VOICE_CALL : mAudioStream,
                Audio.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                mBufferSize,
                AudioTrack.MODE_STREAM);

        mThread = new Thread(this);
        mThread.start();
    }

    public void stopPlaying() {
        if(!mRunning)
            return;

        mRunning = false;
        synchronized (mInactiveLock) {
            mInactiveLock.notify(); // Wake inactive lock if active
        }
        try {
            mThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mThread = null;
        for(int i = 0; i < mAudioOutputs.size(); i++) {
            mAudioOutputs.valueAt(i).destroy();
        }

        mAudioOutputs.clear();
        mAudioTrack.release();
        mAudioTrack = null;
    }

    public boolean isPlaying() {
        return mRunning;
    }

    @Override
    public void run() {
        Log.v(Constants.TAG, "Started audio output thread.");
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        mRunning = true;
        mAudioTrack.play();

        final short[] mix = new short[Audio.FRAME_SIZE];

        while(mRunning) {
            Arrays.fill(mix, (short)0);
            boolean play = mix(mix, mix.length);
            if(play) {
                mAudioTrack.write(mix, 0, mix.length);
            }
            else if(System.nanoTime()-mLastPacket > SLEEP_THRESHOLD) {
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
        mMixBuffer.clear();
        mDelBuffer.clear();
        // TODO add priority speaker support

        for(int i = 0; i < mAudioOutputs.size(); i++) {
            AudioOutputSpeech speech = mAudioOutputs.valueAt(i);
            if(!speech.needSamples(bufferSize))
                mDelBuffer.add(speech);
            else
                mMixBuffer.add(speech);
        }

        if(!mMixBuffer.isEmpty()) {
            for(AudioOutputSpeech speech : mMixBuffer) {
                float[] buffer = speech.getBuffer();
                for(int i = 0; i < bufferSize; i++) {
                    short pcm = (short) (buffer[i]*Short.MAX_VALUE); // Convert float to short
                    pcm = pcm <= Short.MAX_VALUE ? (pcm >= Short.MIN_VALUE ? pcm : Short.MIN_VALUE) : Short.MIN_VALUE; // Clip audio
                    outBuffer[i] += pcm;
                }
            }
        }

        for(AudioOutputSpeech speech : mDelBuffer) {
            Log.v(Constants.TAG, "Deleted audio user "+speech.getUser().getName());
            mAudioOutputs.remove(speech.getSession());
            speech.destroy();
        }

        return !mMixBuffer.isEmpty();
    }

    @Override
    public void messageVoiceData(byte[] data) {
        if(!mRunning)
            return;

        JumbleUDPMessageType dataType = JumbleUDPMessageType.values()[data[0] >> 5 & 0x7];
        int msgFlags = data[0] & 0x1f;
        PacketDataStream pds = new PacketDataStream(data, data.length);
        pds.skip(1);
        int session = (int) pds.readLong();
        User user = getService().getUserHandler().getUser(session);
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
                aop = new AudioOutputSpeech(user, dataType, this);
                Log.v(Constants.TAG, "Created audio user "+user.getName());
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
    public void messageUDPPing(byte[] data) {
        // nothing
    }

    @Override
    public void onTalkStateUpdated(int session, User.TalkState state) {
        final User user = getService().getUserHandler().getUser(session);
        if(user != null && user.getTalkState() != state) {
            user.setTalkState(state);
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    getService().notifyObservers(new JumbleService.ObserverRunnable() {
                        @Override
                        public void run(IJumbleObserver observer) throws RemoteException {
                            observer.onUserTalkStateUpdated(user);
                        }
                    });
                }
            });
        }
    }
}
