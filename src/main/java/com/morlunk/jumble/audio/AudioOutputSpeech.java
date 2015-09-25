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

import com.googlecode.javacpp.IntPointer;
import com.morlunk.jumble.audio.javacpp.CELT11;
import com.morlunk.jumble.audio.javacpp.CELT7;
import com.morlunk.jumble.audio.javacpp.Opus;
import com.morlunk.jumble.audio.javacpp.Speex;
import com.morlunk.jumble.exception.NativeAudioException;
import com.morlunk.jumble.model.TalkState;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.net.PacketBuffer;
import com.morlunk.jumble.protocol.AudioHandler;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by andrew on 16/07/13.
 */
public class AudioOutputSpeech implements Callable<AudioOutputSpeech.Result> {

    interface TalkStateListener {
        public void onTalkStateUpdated(int session, TalkState state);
    }

    private IDecoder mDecoder;
    private Speex.JitterBuffer mJitterBuffer;
    private final Object mJitterLock = new Object();

    private User mUser;
    private JumbleUDPMessageType mCodec;
    private int mAudioBufferSize = AudioHandler.FRAME_SIZE;
    private int mRequestedSamples; // Number of samples requested

    // State-specific
    private float[] mBuffer;
    private float[] mOut;
    private float[] mFadeOut;
    private float[] mFadeIn;
    private Queue<ByteBuffer> mFrames = new ConcurrentLinkedQueue<ByteBuffer>();
    private int mMissCount = 0;
    private boolean mHasTerminator = false;
    private boolean mLastAlive = true;
    private int mBufferFilled, mLastConsume = 0;
    private int ucFlags;
    private IntPointer avail = new IntPointer(1);

    private TalkStateListener mTalkStateListener;

    public AudioOutputSpeech(User user, JumbleUDPMessageType codec, int requestedSamples, TalkStateListener listener) throws NativeAudioException {
        // TODO: consider implementing resampling if some Android devices not support 48kHz?
        mUser = user;
        mCodec = codec;
        mRequestedSamples = requestedSamples;
        mTalkStateListener = listener;
        switch (codec) {
            case UDPVoiceOpus:
                mAudioBufferSize *= 12;
                mDecoder = new Opus.OpusDecoder(AudioHandler.SAMPLE_RATE, 1);
                break;
            case UDPVoiceCELTBeta:
                mDecoder = new CELT11.CELT11Decoder(AudioHandler.SAMPLE_RATE, 1);
                break;
            case UDPVoiceCELTAlpha:
                mDecoder = new CELT7.CELT7Decoder(AudioHandler.SAMPLE_RATE, AudioHandler.FRAME_SIZE, 1);
                break;
            case UDPVoiceSpeex:
                mDecoder = new Speex.SpeexDecoder();
                break;
        }

        mBuffer = new float[mAudioBufferSize*2]; // Make initial buffer size larger so we can save performance by not resizing at runtime.
        mOut = new float[mAudioBufferSize];
        mFadeIn = new float[AudioHandler.FRAME_SIZE];
        mFadeOut = new float[AudioHandler.FRAME_SIZE];

        // Sine function to represent fade in/out. Period is FRAME_SIZE.
        float mul = (float)(Math.PI / (2.0 * (float) AudioHandler.FRAME_SIZE));
        for (int i = 0; i < AudioHandler.FRAME_SIZE; i++)
            mFadeIn[i] = mFadeOut[AudioHandler.FRAME_SIZE-i-1] = (float) Math.sin((float) i * mul);

        mJitterBuffer = new Speex.JitterBuffer(AudioHandler.FRAME_SIZE);
        IntPointer margin = new IntPointer(1);
        margin.put(10 * AudioHandler.FRAME_SIZE);
        mJitterBuffer.control(Speex.JitterBuffer.JITTER_BUFFER_SET_MARGIN, margin);
    }

    public void addFrameToBuffer(PacketBuffer pb, byte flags, int seq) {
        if(pb.capacity() < 2)
            return;

        synchronized (mJitterLock) {
            try {
                int samples = 0;
                if (mCodec == JumbleUDPMessageType.UDPVoiceOpus) {
                    long header = pb.readLong();
                    int size = (int) (header & ((1 << 13) - 1));

                    if (size > 0) {
                        byte[] data = pb.dataBlock(size);
                        if (data.length != size) return;

                        int frames = Opus.opus_packet_get_nb_frames(data, size);
                        samples = frames * Opus.opus_packet_get_samples_per_frame(data, AudioHandler.SAMPLE_RATE);
                    } else {
                        return;
                    }
                } else {
                    try {
                        int header;
                        do {
                            header = pb.next();
                            samples += AudioHandler.FRAME_SIZE;
                            pb.skip(header & 0x7f);
                        } while ((header & 0x80) > 0);
                    } catch (BufferUnderflowException e) {
                        // reached end of buffer
                    }
                }
                pb.rewind();

                int size = pb.left();
                byte[] data = pb.dataBlock(size);
                Speex.JitterBufferPacket packet = new Speex.JitterBufferPacket(data, size, AudioHandler.FRAME_SIZE * seq, samples, 0, flags);
                synchronized (mJitterLock) {
                    mJitterBuffer.put(packet);
                }
            } catch (BufferOverflowException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Result call() throws Exception {
        if (mBufferFilled - mLastConsume > 0) {
            // Shift over the remaining unconsumed data in the buffer.
            System.arraycopy(mBuffer, mLastConsume, mBuffer, 0, mBufferFilled - mLastConsume);
        }
        mBufferFilled -= mLastConsume;

        mLastConsume = mRequestedSamples;

        if(mBufferFilled >= mRequestedSamples)
            return new Result(this, mLastAlive, mBuffer, mBufferFilled);

        boolean nextAlive = mLastAlive;

        while(mBufferFilled < mRequestedSamples) {
            int decodedSamples = AudioHandler.FRAME_SIZE;
            resizeBuffer(mBufferFilled + mAudioBufferSize);

            if(!mLastAlive)
                Arrays.fill(mOut, 0);
            else {
                avail.put(0);

                int ts;
                synchronized (mJitterLock) {
                    ts = mJitterBuffer.getPointerTimestamp();
                    mJitterBuffer.control(Speex.JitterBuffer.JITTER_BUFFER_GET_AVAILABLE_COUNT, avail);
                }
                float availPackets = (float) avail.get();

                // This bit of code here will make sure that we have enough packets in the jitter
                // buffer before we even begin decoding, based on the average # of packets available.
                // It's useful in preventing a metallic 'twang' when the user starts talking,
                // caused by buffer underrun. The official Mumble project uses the same technique.
                if(ts == 0) {
                    int want = (int) Math.ceil(mUser.getAverageAvailable());
                    if (availPackets < want) {
                        mMissCount++;
                        if(mMissCount < 20) {
                            Arrays.fill(mOut, 0);
                            System.arraycopy(mOut, 0, mBuffer, mBufferFilled, decodedSamples);
                            mBufferFilled += decodedSamples;
                            continue;
                        }
                    }
                }

                if(mFrames.isEmpty()) {
                    ByteBuffer packet = ByteBuffer.allocateDirect(4096);
                    Speex.JitterBufferPacket jbp = new Speex.JitterBufferPacket(packet, 4096, 0, 0, 0, 0);
                    int result;

                    synchronized (mJitterLock) {
                        result = mJitterBuffer.get(jbp, null);
                    }

                    if(result == Speex.JitterBuffer.JITTER_BUFFER_OK) {
                        packet.limit(jbp.getLength());
                        PacketBuffer pb = new PacketBuffer(packet);

                        mMissCount = 0;
                        ucFlags = jbp.getUserData();

                        mHasTerminator = false;
                        try {
                            if (mCodec == JumbleUDPMessageType.UDPVoiceOpus) {
                                long header = pb.readLong();
                                int size = (int) (header & ((1 << 13) - 1));
                                mHasTerminator = (header & (1 << 13)) > 0;

                                ByteBuffer audioData = pb.bufferBlock(size);
                                mFrames.add(audioData);
                            } else {
                                int header;
                                do {
                                    header = pb.next();
                                    int size = header & 0x7f;
                                    if (header > 0) {
                                        mFrames.add(pb.bufferBlock(size));
                                    } else {
                                        mHasTerminator = true;
                                    }
                                } while ((header & 0x80) > 0);
                            }
                        } catch (BufferOverflowException e) {
                            e.printStackTrace();
                        } catch (BufferUnderflowException e) {
                            e.printStackTrace();
                        }

                        if(availPackets >= mUser.getAverageAvailable())
                            mUser.setAverageAvailable(availPackets);
                        else
                            mUser.setAverageAvailable(mUser.getAverageAvailable() * 0.99f);

                    } else {
                        synchronized (mJitterLock) {
                            mJitterBuffer.updateDelay(jbp, null);
                        }

                        mMissCount++;
                        if(mMissCount > 10)
                            nextAlive = false;
                    }
                }

                try {
                    if(!mFrames.isEmpty()) {
                        ByteBuffer data = mFrames.poll();

                        decodedSamples = mDecoder.decodeFloat(data, data.limit(), mOut, mAudioBufferSize);

                        if(mFrames.isEmpty())
                            synchronized (mJitterLock) {
                                mJitterBuffer.updateDelay(null, new IntPointer(1));
                            }

                        if(mFrames.isEmpty() && mHasTerminator)
                            nextAlive = false;
                    } else {
                        decodedSamples = mDecoder.decodeFloat(null, 0, mOut, AudioHandler.FRAME_SIZE);
                    }
                } catch (NativeAudioException e) {
                    e.printStackTrace();
                    decodedSamples = AudioHandler.FRAME_SIZE;
                }

                if (!nextAlive) {
                    for (int i = 0; i < AudioHandler.FRAME_SIZE; i++) {
                        mOut[i] *= mFadeOut[i];
                    }
                } else if (ts == 0) {
                    for (int i = 0; i < AudioHandler.FRAME_SIZE; i++) {
                        mOut[i] *= mFadeIn[i];
                    }
                }

                synchronized (mJitterLock) {
                    for(int i = decodedSamples / AudioHandler.FRAME_SIZE; i > 0; i--)
                        mJitterBuffer.tick();
                }
            }

            System.arraycopy(mOut, 0, mBuffer, mBufferFilled, decodedSamples);
            mBufferFilled += decodedSamples;
        }

        if(!nextAlive) ucFlags = 0xFF;

        TalkState talkState;
        switch (ucFlags) {
            case 0:
                talkState = TalkState.TALKING;
                break;
            case 1:
                talkState = TalkState.SHOUTING;
                break;
            case 0xFF:
                talkState = TalkState.PASSIVE;
                break;
            default:
                talkState = TalkState.WHISPERING;
                break;
        }

        mTalkStateListener.onTalkStateUpdated(mUser.getSession(), talkState);

        boolean tmp = mLastAlive;
        mLastAlive = nextAlive;

        return new Result(this, tmp, mBuffer, mRequestedSamples);
    }

    private void resizeBuffer(int newSize) {
        if(newSize > mBuffer.length) {
            float[] newBuffer = Arrays.copyOf(mBuffer, newSize);
            mBuffer = newBuffer;
        }
    }

    /**
     * Sets the preferred number of samples to return when the callable is executed.
     * @param samples The number of floating point samples to retrieve.
     */
    public void setRequestedSamples(int samples) {
        mRequestedSamples = samples;
    }

    public JumbleUDPMessageType getCodec() {
        return mCodec;
    }

    public User getUser() {
        return mUser;
    }

    public int getSession() {
        return mUser.getSession();
    }

    /**
     * Cleans up all JNI refs linked to this instance.
     * This MUST be called eventually, otherwise we get memory leaks!
     */
    public void destroy() {
        if(mDecoder != null) mDecoder.destroy();
        mJitterBuffer.destroy();
    }

    /**
     * The outcome of a decoding pass.
     */
    protected static class Result implements IAudioMixerSource<float[]> {
        private AudioOutputSpeech mSpeechOutput;
        private boolean mAlive;
        private float[] mSamples;
        private int mNumSamples;

        private Result(AudioOutputSpeech speechOutput,
                      boolean alive,
                      float[] samples,
                      int numSamples) {
            mSpeechOutput = speechOutput;
            mAlive = alive;
            mSamples = samples;
            mNumSamples = numSamples;
        }

        public AudioOutputSpeech getSpeechOutput() {
            return mSpeechOutput;
        }

        public boolean isAlive() {
            return mAlive;
        }

        public float[] getSamples() {
            return mSamples;
        }

        public int getNumSamples() {
            return mNumSamples;
        }
    }
}
