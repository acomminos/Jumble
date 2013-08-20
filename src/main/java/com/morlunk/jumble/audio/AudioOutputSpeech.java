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

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.audio.celt11.CELT11;
import com.morlunk.jumble.audio.celt7.CELT7;
import com.morlunk.jumble.audio.opus.Opus;
import com.morlunk.jumble.audio.opus.SWIGTYPE_p_OpusDecoder;
import com.morlunk.jumble.audio.speex.JitterBufferPacket;
import com.morlunk.jumble.audio.speex.SWIGTYPE_p_JitterBuffer_;
import com.morlunk.jumble.audio.speex.SWIGTYPE_p_void;
import com.morlunk.jumble.audio.speex.Speex;
import com.morlunk.jumble.audio.speex.SpeexBits;
import com.morlunk.jumble.audio.speex.SpeexConstants;
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

    // Native audio pointers
    private SWIGTYPE_p_OpusDecoder mOpusDecoder;
    private com.morlunk.jumble.audio.celt7.SWIGTYPE_p_CELTDecoder mCELTAlphaDecoder;
    private com.morlunk.jumble.audio.celt7.SWIGTYPE_p_CELTMode mCELTAlphaMode;
    private com.morlunk.jumble.audio.celt11.SWIGTYPE_p_CELTDecoder mCELTBetaDecoder;
    private SWIGTYPE_p_void mSpeexDecoder;
    private SpeexBits mSpeexBits;
    private SWIGTYPE_p_JitterBuffer_ mJitterBuffer;

    private int mSession;
    private JumbleUDPMessageType mCodec;
    private int mAudioBufferSize = Audio.FRAME_SIZE;

    // State-specific
    private float[] mBuffer;
    private Queue<byte[]> mFrames = new ConcurrentLinkedQueue<byte[]>();
    private int mMissCount = 0;
    private int mMissedFrames = 0;
    private float mAverageAvailable = 0;
    private boolean mHasTerminator = false;
    private boolean mLastAlive = true;
    private int mBufferOffset, mBufferFilled, mLastConsume = 0;
    private int ucFlags;

    private TalkStateListener mTalkStateListener;

    public AudioOutputSpeech(int session, JumbleUDPMessageType codec, TalkStateListener listener) {
        // TODO: consider implementing resampling if some Android devices not support 48kHz?
        mSession = session;
        mCodec = codec;
        mTalkStateListener = listener;
        int[] error = new int[1];
        switch (codec) {
            case UDPVoiceOpus:
                mAudioBufferSize *= 12;
                mOpusDecoder = Opus.opus_decoder_create(Audio.SAMPLE_RATE, 1, error);
                break;
            case UDPVoiceSpeex:
                mSpeexBits = new SpeexBits();
                Speex.speex_bits_init(mSpeexBits);
                mSpeexDecoder = Speex.speex_decoder_init(Speex.getSpeex_uwb_mode());
                Speex.speex_decoder_ctl(mSpeexDecoder, SpeexConstants.SPEEX_SET_ENH, new int[] { 1 });
                break;
            case UDPVoiceCELTBeta:
                mCELTBetaDecoder = CELT11.celt_decoder_create(Audio.SAMPLE_RATE, 1, error);
                break;
            case UDPVoiceCELTAlpha:
                mCELTAlphaMode = CELT7.celt_mode_create(Audio.SAMPLE_RATE, Audio.FRAME_SIZE, error);
                mCELTAlphaDecoder = CELT7.celt_decoder_create(mCELTAlphaMode, 1, error);
                break;
        }

        mBuffer = new float[mAudioBufferSize];
        mJitterBuffer = Speex.jitter_buffer_init(Audio.FRAME_SIZE);
        Speex.jitter_buffer_ctl(mJitterBuffer, SpeexConstants.JITTER_BUFFER_SET_MARGIN, new int[] { 10 * Audio.FRAME_SIZE });
    }

    public void addFrameToBuffer(byte[] data, int seq) {
        if(data.length < 2)
            return;

        PacketDataStream pds = new PacketDataStream(data, data.length);

        // skip flags
        pds.next();

        int samples = 0;
        if(mCodec == JumbleUDPMessageType.UDPVoiceOpus) {
            int size = pds.next();
            size &= 0x1fff;

            byte[] packet = pds.dataBlock(size);
            int frames = Opus.opus_packet_get_nb_frames(packet, size);
            samples = Opus.opus_packet_get_samples_per_frame(packet, Audio.FRAME_SIZE);

            if(samples % Audio.FRAME_SIZE == 0)
                return; // We can't handle frames which are not a multiple of 10ms.
        } else {
            int header;
            do {
                header = pds.next();
                samples += Audio.FRAME_SIZE;
                pds.skip(header & 0x7f);
            } while ((header & 0x80) > 0 && pds.isValid());
        }

        if(pds.isValid()) {
            JitterBufferPacket jbp = new JitterBufferPacket();
            jbp.setData(data);
            jbp.setLen(data.length);
            jbp.setSpan(samples);
            jbp.setTimestamp(Audio.FRAME_SIZE * seq);
            synchronized(mJitterBuffer) {
                Speex.jitter_buffer_put(mJitterBuffer, jbp);
            }
        }
    }

    public boolean needSamples(int num) {
        for(int i = mLastConsume; i < mBufferFilled; i++)
            mBuffer[i-mLastConsume] = mBuffer[i];
        mBufferFilled -= mLastConsume;

        mLastConsume = num;

        if(mBufferFilled >= num)
            return mLastAlive;

        float[] out = new float[mAudioBufferSize];
        boolean nextAlive = mLastAlive;

        while(mBufferFilled < num) {
            int decodedSamples = Audio.FRAME_SIZE;
            resizeBuffer(mBufferFilled + mAudioBufferSize);

            if(!mLastAlive)
                Arrays.fill(out, 0);
            else {
                int[] avail = new int[1];
                int ts = Speex.jitter_buffer_get_pointer_timestamp(mJitterBuffer);
                Speex.jitter_buffer_ctl(mJitterBuffer, SpeexConstants.JITTER_BUFFER_GET_AVAILABLE_COUNT, avail);

                if(ts != 0) {
                    int want = (int) mAverageAvailable;
                    if (avail[0] < want) {
                        mMissCount++;
                        if(mMissCount < 20) {
                            Arrays.fill(out, 0);
                            mBufferFilled += mAudioBufferSize;
                            continue;
                        }
                    }
                }

                if(mFrames.isEmpty()) {
                    byte[] data = new byte[4096];
                    JitterBufferPacket jbp = new JitterBufferPacket();
                    jbp.setData(data);
                    jbp.setLen(4096);

                    int[] startofs = new int[1];
                    int result = 0;

                    synchronized (mJitterBuffer) {
                        result = Speex.jitter_buffer_get(mJitterBuffer, jbp, Audio.FRAME_SIZE, startofs);
                    }

                    if(result == SpeexConstants.JITTER_BUFFER_OK) {
                        PacketDataStream pds = new PacketDataStream(jbp.getData(), (int)jbp.getLen());

                        mMissCount = 0;
                        ucFlags = pds.next();

                        mHasTerminator = false;
                        if(mCodec == JumbleUDPMessageType.UDPVoiceOpus) {
                            int size = pds.next();

                            mHasTerminator = (size & 0x2000) > 0;
                            mFrames.add(pds.dataBlock(size & 0x1fff));
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
                    } else {
                        synchronized (mJitterBuffer) {
                            Speex.jitter_buffer_update_delay(mJitterBuffer, jbp, new int[] { 0 });
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
                                mFrames.isEmpty() ? null : data,
                                data.length,
                                out);
                    } else if(mCodec == JumbleUDPMessageType.UDPVoiceCELTBeta) {
                        CELT11.celt_decode_float(mCELTBetaDecoder,
                                mFrames.isEmpty() ? null : data,
                                data.length,
                                out,
                                Audio.FRAME_SIZE);
                    } else if(mCodec == JumbleUDPMessageType.UDPVoiceOpus) {
                        decodedSamples = Opus.opus_decode_float(mOpusDecoder,
                                mFrames.isEmpty() ? null : data,
                                data.length,
                                out,
                                mAudioBufferSize,
                                0);
                    } else { // Speex
                        if(mFrames.isEmpty())
                            Speex.speex_decode(mSpeexDecoder, null, out);
                        else {
                            Speex.speex_bits_read_from(mSpeexBits, data, data.length);
                            Speex.speex_decode(mSpeexDecoder, mSpeexBits, out);
                        }
                        for(int i = 0; i < Audio.FRAME_SIZE; i++)
                            out[i] *= (1.0f / 32767.f);
                    }


                    if(mFrames.isEmpty())
                        synchronized (mJitterBuffer) {
                            Speex.jitter_buffer_update_delay(mJitterBuffer, null, null);
                        }

                    if(mFrames.isEmpty() && mHasTerminator)
                        nextAlive = false;
                } else {
                    if(mCodec == JumbleUDPMessageType.UDPVoiceCELTAlpha)
                        CELT7.celt_decode_float(mCELTAlphaDecoder, null, 0, out);
                    else if(mCodec == JumbleUDPMessageType.UDPVoiceCELTBeta)
                        CELT11.celt_decode_float(mCELTBetaDecoder, null, 0, out, Audio.FRAME_SIZE);
                    else if(mCodec == JumbleUDPMessageType.UDPVoiceOpus)
                        decodedSamples = Opus.opus_decode_float(mOpusDecoder, null, 0, out, Audio.FRAME_SIZE, 0);
                    else {
                        Speex.speex_decode(mSpeexDecoder, null, out);
                        for(int i = 0; i < Audio.FRAME_SIZE; i++)
                            out[i] *= (1.0f / 32767.f);
                    }
                }

                for(int i = decodedSamples / Audio.FRAME_SIZE; i > 0; i--)
                    synchronized (mJitterBuffer) {
                        Speex.jitter_buffer_tick(mJitterBuffer);
                    }
            }

            System.arraycopy(out, 0, mBuffer, mBufferFilled, mAudioBufferSize);
            mBufferFilled += mAudioBufferSize;
        }

        if(!nextAlive)
            ucFlags = 0xFF;

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
        if(newSize > mAudioBufferSize) {
            float[] n = new float[newSize];
            if(mBuffer != null)
                System.arraycopy(mBuffer, 0, n, 0, mAudioBufferSize);
            mBuffer = n;
            mAudioBufferSize = newSize;
        }
    }

    public float[] getBuffer() {
        return mBuffer;
    }

    public JumbleUDPMessageType getCodec() {
        return mCodec;
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
            Speex.speex_bits_destroy(mSpeexBits);
        if(mSpeexDecoder != null)
            Speex.speex_decoder_destroy(mSpeexDecoder);
        if(mJitterBuffer != null)
            Speex.jitter_buffer_destroy(mJitterBuffer);
    }
}
