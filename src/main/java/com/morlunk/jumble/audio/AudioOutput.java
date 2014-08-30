/*
 * Copyright (C) 2014 Andrew Comminos
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

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.util.SparseArray;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.exception.AudioInitializationException;
import com.morlunk.jumble.exception.NativeAudioException;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.net.PacketBuffer;
import com.morlunk.jumble.protocol.AudioHandler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by andrew on 16/07/13.
 */
public class AudioOutput implements Runnable, AudioOutputSpeech.TalkStateListener {

    private SparseArray<AudioOutputSpeech> mAudioOutputs = new SparseArray<AudioOutputSpeech>();
    private AudioTrack mAudioTrack;
    private int mBufferSize;
    private Thread mThread;
    private final Object mInactiveLock = new Object(); // Lock that the audio thread waits on when there's no audio to play. Wake when we get a frame.
    private final Object mPacketLock = new Object();
    private boolean mRunning = false;
    private List<AudioOutputSpeech> mMixBuffer = new ArrayList<AudioOutputSpeech>();
    private List<AudioOutputSpeech> mDelBuffer = new ArrayList<AudioOutputSpeech>();
    private Handler mMainHandler;
    private AudioOutputListener mListener;
    private int mAudioStream;

    public AudioOutput(AudioOutputListener listener, int audioStream) {
        mListener = listener;
        mAudioStream = audioStream;
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    public void startPlaying(boolean scoEnabled) throws AudioInitializationException {
        if(mRunning)
            return;

        int minBufferSize = AudioTrack.getMinBufferSize(AudioHandler.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mBufferSize = minBufferSize;
//        mBufferSize = Math.max(minBufferSize, Audio.FRAME_SIZE * 12); // Make the buffer size a multiple of the largest possible frame.
        Log.v(Constants.TAG, "Using buffer size "+mBufferSize+", system's min buffer size: "+minBufferSize);

        // Force STREAM_VOICE_CALL for Bluetooth, as it's all that will work.
        try {
            mAudioTrack = new AudioTrack(scoEnabled ? AudioManager.STREAM_VOICE_CALL : mAudioStream,
                    AudioHandler.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    mBufferSize,
                    AudioTrack.MODE_STREAM);
        } catch (IllegalArgumentException e) {
            throw new AudioInitializationException(e);
        }

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

        final short[] mix = new short[AudioHandler.FRAME_SIZE];

        while(mRunning) {
            Arrays.fill(mix, (short)0);
            boolean play = mix(mix, mix.length);
            if(play) {
                mAudioTrack.write(mix, 0, mix.length);
            } else {
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

        synchronized (mPacketLock) {
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
        }

        return !mMixBuffer.isEmpty();
    }

    public void queueVoiceData(byte[] data, JumbleUDPMessageType messageType) {
        if(!mRunning)
            return;

        byte msgFlags = (byte) (data[0] & 0x1f);
        PacketBuffer pds = new PacketBuffer(data, data.length);
        pds.skip(1);
        int session = (int) pds.readLong();
        User user = mListener.getUser(session);
        if(user != null && !user.isLocalMuted()) {
            // TODO check for whispers here
            int seq = (int) pds.readLong();

            // Synchronize so we don't destroy an output while we add a buffer to it.
            synchronized (mPacketLock) {
                AudioOutputSpeech aop = mAudioOutputs.get(session);
                if(aop != null && aop.getCodec() != messageType) {
                    aop.destroy();
                    aop = null;
                }
                if(aop == null) {
                    try {
                        aop = new AudioOutputSpeech(user, messageType, this);
                    } catch (NativeAudioException e) {
                        Log.v(Constants.TAG, "Failed to create audio user "+user.getName());
                        e.printStackTrace();
                        return;
                    }
                    Log.v(Constants.TAG, "Created audio user "+user.getName());
                    mAudioOutputs.put(session, aop);
                }

                PacketBuffer dataBuffer = new PacketBuffer(pds.bufferBlock(pds.left()));
                aop.addFrameToBuffer(dataBuffer, msgFlags, seq);
            }

            synchronized (mInactiveLock) {
                mInactiveLock.notify();
            }
        }

    }

    @Override
    public void onTalkStateUpdated(int session, User.TalkState state) {
        final User user = mListener.getUser(session);
        if(user != null && user.getTalkState() != state) {
            user.setTalkState(state);
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onUserTalkStateUpdated(user);
                }
            });
        }
    }

    public static interface AudioOutputListener {
        /**
         * Called when a user's talking state is changed.
         * @param user The user whose talking state has been modified.
         */
        public void onUserTalkStateUpdated(User user);

        /**
         * Used to set audio-related user data.
         * @return The user for the associated session.
         */
        public User getUser(int session);
    }
}
