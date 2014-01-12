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

import android.util.Log;

import com.googlecode.javacpp.BytePointer;
import com.googlecode.javacpp.FloatPointer;
import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Loader;
import com.googlecode.javacpp.Pointer;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.audio.javacpp.CELT11;
import com.morlunk.jumble.audio.javacpp.CELT7;
import com.morlunk.jumble.audio.javacpp.Opus;
import com.morlunk.jumble.audio.javacpp.Speex;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.net.PacketDataStream;

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

    private Pointer mOpusDecoder;
    private Pointer mCELTBetaDecoder;
    private Pointer mCELTAlphaMode;
    private Pointer mCELTAlphaDecoder;
    private Speex.SpeexBits mSpeexBits;
    private Pointer mSpeexDecoder;
    private Speex.JitterBuffer mJitterBuffer;

    private int mSession;
    private JumbleUDPMessageType mCodec;
    private int mAudioBufferSize = Audio.FRAME_SIZE;

    // State-specific
    private float[] mBuffer;
    private float[] mOut;
    private float[] mFadeOut;
    private float[] mFadeIn;
    private Queue<byte[]> mFrames = new ConcurrentLinkedQueue<byte[]>();
    private int mMissCount = 0;
    private float mAverageAvailable = 0;
    private boolean mHasTerminator = false;
    private boolean mLastAlive = true;
    private int mBufferFilled, mLastConsume = 0;
    private int ucFlags;

    private TalkStateListener mTalkStateListener;

    public AudioOutputSpeech(int session, JumbleUDPMessageType codec, TalkStateListener listener) {
        // TODO: consider implementing resampling if some Android devices not support 48kHz?
        mSession = session;
        mCodec = codec;
        mTalkStateListener = listener;
        switch (codec) {
            case UDPVoiceOpus:
                mAudioBufferSize *= 12;
                mOpusDecoder = Opus.opus_decoder_create(Audio.SAMPLE_RATE, 1, null);
                break;
            case UDPVoiceCELTBeta:
                mCELTBetaDecoder = CELT11.celt_decoder_create(Audio.SAMPLE_RATE, 1, null);
                break;
            case UDPVoiceCELTAlpha:
                mCELTAlphaMode = CELT7.celt_mode_create(Audio.SAMPLE_RATE, Audio.FRAME_SIZE, null);
                mCELTAlphaDecoder = CELT7.celt_decoder_create(mCELTAlphaMode, 1, null);
                break;
            case UDPVoiceSpeex:
                mSpeexBits = new Speex.SpeexBits();
                mSpeexDecoder = Speex.speex_decoder_init(Speex.speex_lib_get_mode(Speex.SPEEX_MODEID_UWB));
                IntPointer enh = new IntPointer();
                enh.put(1);
                Speex.speex_decoder_ctl(mSpeexDecoder, Speex.SPEEX_SET_ENH, enh);
                break;
        }

        mBuffer = new float[mAudioBufferSize*2]; // Make initial buffer size larger so we can save performance by not resizing at runtime.
        mOut = new float[mAudioBufferSize];
        mFadeIn = new float[Audio.FRAME_SIZE];
        mFadeOut = new float[Audio.FRAME_SIZE];

        // Sine function to represent fade in/out. Period is FRAME_SIZE.
        float mul = (float)(Math.PI / (2.0 * (float)Audio.FRAME_SIZE));
        for (int i = 0; i < Audio.FRAME_SIZE; i++)
            mFadeIn[i] = mFadeOut[Audio.FRAME_SIZE-i-1] = (float) Math.sin((float) i * mul);

        mJitterBuffer = new Speex.JitterBuffer(Audio.FRAME_SIZE);
        IntPointer margin = new IntPointer(1);
        margin.put(10 * Audio.FRAME_SIZE);
        mJitterBuffer.control(Speex.JitterBuffer.JITTER_BUFFER_SET_MARGIN, margin);
    }

    public void addFrameToBuffer(byte[] data, int seq) {
        if(data.length < 2)
            return;

        PacketDataStream pds = new PacketDataStream(data, data.length);
        pds.next(); // skip flags

        int samples = 0;
        if(mCodec == JumbleUDPMessageType.UDPVoiceOpus) {
            long header = pds.readLong();
            int size = (int) (header & ((1 << 13) - 1));

            if(size > 0) {
                byte[] packet = pds.dataBlock(size);
                BytePointer packetPointer = new BytePointer(packet);
                int frames = Opus.opus_packet_get_nb_frames(packetPointer, size);
                samples = frames * Opus.opus_packet_get_samples_per_frame(packetPointer, Audio.SAMPLE_RATE);
                packetPointer.deallocate();
            }
        } else {
            int header;
            do {
                header = pds.next();
                samples += Audio.FRAME_SIZE;
                pds.skip(header & 0x7f);
            } while ((header & 0x80) > 0 && pds.isValid());
        }

        if(pds.isValid()) {
            Speex.JitterBufferPacket packet = new Speex.JitterBufferPacket(data, data.length, Audio.FRAME_SIZE * seq, samples, 0);
            synchronized (mJitterBuffer) {
                mJitterBuffer.put(packet);
            }
            packet.deallocate();
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
            int decodedSamples = Audio.FRAME_SIZE;
            resizeBuffer(mBufferFilled + mAudioBufferSize);

            if(!mLastAlive)
                Arrays.fill(mOut, 0);
            else {
                IntPointer avail = new IntPointer(1);
                avail.put(0);

                int ts;
                synchronized (mJitterBuffer) {
                    ts = mJitterBuffer.getPointerTimestamp();
                    mJitterBuffer.control(Speex.JitterBuffer.JITTER_BUFFER_GET_AVAILABLE_COUNT, avail);
                }

//                if(ts != 0) {
//                    int want = (int) mAverageAvailable;
//                    if (avail.get() < want) {
//                        mMissCount++;
//                        if(mMissCount < 20) {
//                            Arrays.fill(mOut, 0);
//                            System.arraycopy(mOut, 0, mBuffer, mBufferFilled, decodedSamples);
//                            mBufferFilled += decodedSamples;
//                            continue;
//                        }
//                    }
//                }

                if(mFrames.isEmpty()) {
                    Speex.JitterBufferPacket jbp = new Speex.JitterBufferPacket(null, 4096, 0, 0, 0);
                    IntPointer startofs = new IntPointer(1);
                    int result;

                    synchronized (mJitterBuffer) {
                        result = mJitterBuffer.get(jbp, startofs);
                    }

                    if(result == Speex.JitterBuffer.JITTER_BUFFER_OK) {
                        byte[] data = new byte[jbp.getLength()];
                        jbp.getData().get(data);
                        PacketDataStream pds = new PacketDataStream(data, jbp.getLength());

                        mMissCount = 0;
                        ucFlags = pds.next();

                        mHasTerminator = false;
                        if(mCodec == JumbleUDPMessageType.UDPVoiceOpus) {
                            long header = pds.readLong();
                            int size = (int) (header & ((1 << 13) - 1));
                            mHasTerminator = (header & (1 << 13)) > 0;

                            byte[] audioData = pds.dataBlock(size);
                            mFrames.add(audioData);
                        } else {
                            int header;
                            do {
                                header = pds.next() & 0xFF;
                                if(header > 0)
                                    mFrames.add(pds.dataBlock(header & 0x7f));
                                else
                                    mHasTerminator = true;
                            } while ((header & 0x80) > 0 && pds.isValid());
                        }

                        float a = (float) avail.get();
                        if(a >= mAverageAvailable)
                            mAverageAvailable = a;
                        else
                            mAverageAvailable *= 0.99f;

                    } else {
                        synchronized (mJitterBuffer) {
                            mJitterBuffer.updateDelay(jbp, null);
                        }

                        mMissCount++;
                        if(mMissCount > 10)
                            nextAlive = false;
                    }
                }

                if(!mFrames.isEmpty()) {
                    byte[] data = mFrames.poll();

                    if(mCodec == JumbleUDPMessageType.UDPVoiceCELTAlpha) {
                        CELT7.celt_decode_float(mCELTAlphaDecoder,
                                data,
                                data.length,
                                mOut);
                    } else if(mCodec == JumbleUDPMessageType.UDPVoiceCELTBeta) {
                        CELT11.celt_decode_float(mCELTBetaDecoder,
                                data,
                                data.length,
                                mOut,
                                Audio.FRAME_SIZE);
                    } else if(mCodec == JumbleUDPMessageType.UDPVoiceOpus) {
                        decodedSamples = Opus.opus_decode_float(mOpusDecoder,
                                data,
                                data.length,
                                mOut,
                                mAudioBufferSize,
                                0);
                    } else {
                        mSpeexBits.read(data, data.length);
                        Speex.speex_decode(mSpeexDecoder, mSpeexBits, mOut);
                        for(int i = 0; i < Audio.FRAME_SIZE; i++)
                            mOut[i] *= (1.0f / 32767.f);
                    }

                    if(mFrames.isEmpty())
                        synchronized (mJitterBuffer) {
                            mJitterBuffer.updateDelay(null, new IntPointer(1));
                        }

                    if(mFrames.isEmpty() && mHasTerminator)
                        nextAlive = false;
                } else {
                    if(mCodec == JumbleUDPMessageType.UDPVoiceCELTAlpha)
                        CELT7.celt_decode_float(mCELTAlphaDecoder, null, 0, mOut);
                    else if(mCodec == JumbleUDPMessageType.UDPVoiceCELTBeta)
                        CELT11.celt_decode_float(mCELTBetaDecoder, null, 0, mOut, Audio.FRAME_SIZE);
                    else if(mCodec == JumbleUDPMessageType.UDPVoiceOpus)
                        decodedSamples = Opus.opus_decode_float(mOpusDecoder, null, 0, mOut, Audio.FRAME_SIZE, 0);
                    else {
                        Speex.speex_decode(mSpeexDecoder, null, mOut);
                        for(int i = 0; i < Audio.FRAME_SIZE; i++)
                            mOut[i] *= (1.0f / 32767.f);
                    }
                }

                if (!nextAlive) {
                    for (int i = 0; i < Audio.FRAME_SIZE; i++) {
                        mOut[i] *= mFadeOut[i];
                    }
                } else if (ts == 0) {
                    for (int i = 0; i < Audio.FRAME_SIZE; i++) {
                        mOut[i] *= mFadeIn[i];
                    }
                }

                synchronized (mJitterBuffer) {
                    for(int i = decodedSamples / Audio.FRAME_SIZE; i > 0; i--)
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

        mTalkStateListener.onTalkStateUpdated(mSession, talkState);

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

    public int getSession() {
        return mSession;
    }

    /**
     * Cleans up all JNI refs linked to this instance.
     * This MUST be called eventually, otherwise we get memory leaks!
     */
    public void destroy() {
        if(mOpusDecoder != null)
            Opus.opus_decoder_destroy(mOpusDecoder);
        if(mCELTBetaDecoder != null)
            CELT11.celt_decoder_destroy(mCELTBetaDecoder);
        if(mCELTAlphaMode != null)
            CELT7.celt_mode_destroy(mCELTAlphaMode);
        if(mCELTAlphaDecoder != null)
            CELT7.celt_decoder_destroy(mCELTAlphaDecoder);
        if(mSpeexBits != null)
            mSpeexBits.destroy();
        if(mSpeexDecoder != null)
            Speex.speex_decoder_destroy(mSpeexDecoder);
        mJitterBuffer.destroy();
    }
}
