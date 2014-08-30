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

import com.googlecode.javacpp.BytePointer;
import com.googlecode.javacpp.IntPointer;
import com.morlunk.jumble.audio.javacpp.CELT11;
import com.morlunk.jumble.audio.javacpp.CELT7;
import com.morlunk.jumble.audio.javacpp.Opus;
import com.morlunk.jumble.audio.javacpp.Speex;
import com.morlunk.jumble.exception.NativeAudioException;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.net.PacketBuffer;
import com.morlunk.jumble.protocol.AudioHandler;

import java.nio.BufferOverflowException;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by andrew on 16/07/13.
 */
public class AudioOutputSpeech {
    interface TalkStateListener {
        public void onTalkStateUpdated(int session, User.TalkState state);
    }

    private IDecoder mDecoder;
    private Speex.JitterBuffer mJitterBuffer;
    private final Object mJitterLock = new Object();

    private User mUser;
    private JumbleUDPMessageType mCodec;
    private int mAudioBufferSize = AudioHandler.FRAME_SIZE;

    // State-specific
    private float[] mBuffer;
    private float[] mOut;
    private float[] mFadeOut;
    private float[] mFadeIn;
    private Queue<byte[]> mFrames = new ConcurrentLinkedQueue<byte[]>();
    private int mMissCount = 0;
    private boolean mHasTerminator = false;
    private boolean mLastAlive = true;
    private int mBufferFilled, mLastConsume = 0;
    private int ucFlags;
    private IntPointer avail = new IntPointer(1);

    private TalkStateListener mTalkStateListener;

    public AudioOutputSpeech(User user, JumbleUDPMessageType codec, TalkStateListener listener) throws NativeAudioException {
        // TODO: consider implementing resampling if some Android devices not support 48kHz?
        mUser = user;
        mCodec = codec;
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

    public void addFrameToBuffer(byte[] data, int seq) {
        if(data.length < 2)
            return;

        synchronized (mJitterLock) {
            try {
                PacketBuffer pds = new PacketBuffer(data, data.length);
                pds.next(); // skip flags

                int samples = 0;
                if (mCodec == JumbleUDPMessageType.UDPVoiceOpus) {
                    long header = pds.readLong();
                    int size = (int) (header & ((1 << 13) - 1));

                    if (size > 0) {
                        byte[] packet = pds.dataBlock(size);
                        if (packet.length != size) return;

                        BytePointer packetPointer = new BytePointer(packet);
                        int frames = Opus.opus_packet_get_nb_frames(packetPointer, size);
                        samples = frames * Opus.opus_packet_get_samples_per_frame(packetPointer, AudioHandler.SAMPLE_RATE);
                        packetPointer.deallocate();
                    } else {
                        return;
                    }
                } else {
                    int header;
                    do {
                        header = pds.next();
                        samples += AudioHandler.FRAME_SIZE;
                        pds.skip(header & 0x7f);
                    } while ((header & 0x80) > 0);
                }

                Speex.JitterBufferPacket packet = new Speex.JitterBufferPacket(data, data.length, AudioHandler.FRAME_SIZE * seq, samples, 0);
                synchronized (mJitterLock) {
                    mJitterBuffer.put(packet);
                }
            } catch (BufferOverflowException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean needSamples(int num) {
        for(int i = mLastConsume; i < mBufferFilled; ++i)
            mBuffer[i-mLastConsume] = mBuffer[i];
        mBufferFilled -= mLastConsume;

        mLastConsume = num;

        if(mBufferFilled >= num)
            return mLastAlive;

        boolean nextAlive = mLastAlive;

        while(mBufferFilled < num) {
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
                    Speex.JitterBufferPacket jbp = new Speex.JitterBufferPacket(null, 4096, 0, 0, 0);
                    int result;

                    synchronized (mJitterLock) {
                        result = mJitterBuffer.get(jbp, null);
                    }

                    if(result == Speex.JitterBuffer.JITTER_BUFFER_OK) {
                        byte[] data = new byte[jbp.getLength()];
                        jbp.getData(data, 0, jbp.getLength());
                        PacketBuffer pds = new PacketBuffer(data, jbp.getLength());

                        mMissCount = 0;
                        ucFlags = pds.next();

                        mHasTerminator = false;
                        try {
                            if (mCodec == JumbleUDPMessageType.UDPVoiceOpus) {
                                long header = pds.readLong();
                                int size = (int) (header & ((1 << 13) - 1));
                                mHasTerminator = (header & (1 << 13)) > 0;

                                byte[] audioData = pds.dataBlock(size);
                                mFrames.add(audioData);
                            } else {
                                int header;
                                do {
                                    header = pds.next() & 0xFF;
                                    if (header > 0)
                                        mFrames.add(pds.dataBlock(header & 0x7f));
                                    else
                                        mHasTerminator = true;
                                } while ((header & 0x80) > 0);
                            }
                        } catch (BufferOverflowException e) {
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
                        byte[] data = mFrames.poll();

                        decodedSamples = mDecoder.decodeFloat(data, data.length, mOut, mAudioBufferSize);

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

        User.TalkState talkState;
        switch (ucFlags) {
            case 0:
                talkState = User.TalkState.TALKING;
                break;
            case 1:
                talkState = User.TalkState.SHOUTING;
                break;
            case 0xFF:
                talkState = User.TalkState.PASSIVE;
                break;
            default:
                talkState = User.TalkState.WHISPERING;
                break;
        }

        mTalkStateListener.onTalkStateUpdated(mUser.getSession(), talkState);

        boolean tmp = mLastAlive;
        mLastAlive = nextAlive;

        return tmp;
    }

    public void resizeBuffer(int newSize) {
        if(newSize > mBuffer.length) {
            float[] newBuffer = Arrays.copyOf(mBuffer, newSize);
            mBuffer = newBuffer;
        }
    }

    public float[] getBuffer() {
        return mBuffer;
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
}
