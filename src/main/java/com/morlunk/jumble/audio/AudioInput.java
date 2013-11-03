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
import android.util.Log;

import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Loader;
import com.googlecode.javacpp.Pointer;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.audio.javacpp.Opus;
import com.morlunk.jumble.audio.javacpp.Speex;
import com.morlunk.jumble.net.JumbleMessageHandler;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.net.PacketDataStream;
import com.morlunk.jumble.protobuf.Mumble;

import java.util.Arrays;

/**
 * Created by andrew on 23/08/13.
 */
public class AudioInput extends JumbleMessageHandler.Stub implements Runnable {

    static {
        Loader.load(Opus.class); // Do this so we can reference IntPointer and the like earlier.
    }

    public interface AudioInputListener {
        public void onFrameEncoded(byte[] data, int length, JumbleUDPMessageType messageType);
    }

    public static final int OPUS_MAX_BYTES = 512; // Opus specifies 4000 bytes as a recommended value for encoding, but the official mumble project uses 512.

    private Pointer mOpusEncoder;;
//    private com.morlunk.jumble.audio.celt11.SWIGTYPE_p_CELTEncoder mCELTBetaEncoder;
//    private com.morlunk.jumble.audio.celt7.SWIGTYPE_p_CELTMode mCELTAlphaMode;
//    private com.morlunk.jumble.audio.celt7.SWIGTYPE_p_CELTEncoder mCELTAlphaEncoder;
    private Speex.SpeexPreprocessState mPreprocessState;

    private AudioInputListener mListener;
    private int mTransmitMode = Constants.TRANSMIT_PUSH_TO_TALK;

    private AudioRecord mAudioRecord;
    private int mMinBufferSize;
    private int mInputSampleRate = 48000; //44100; FIXME
    private int mFrameSize = Audio.FRAME_SIZE;
    private int mFramesPerPacket = 2;

    // Temporary encoder state
    private final short[] mOpusBuffer = new short[mFrameSize*mFramesPerPacket];
    private final byte[] mEncodedBuffer = new byte[OPUS_MAX_BYTES];
    private final byte[] mPacketBuffer = new byte[1024];
    private final PacketDataStream mPacketDataStream = new PacketDataStream(mPacketBuffer, 1024);
    private int mBufferedFrames = 0;
    private int mFrameCounter;

    private JumbleUDPMessageType mCodec = null;

    private Object mRecordLock = new Object(); // Make sure we don't get calls to start and stop recording more than once at a time.
    private Thread mRecordThread;
    private boolean mRecording;

    /**
     * Creates a new audio input manager configured for the specified codec.
     * @param listener
     */
    public AudioInput(JumbleUDPMessageType codec, int transmitMode, AudioInputListener listener) {
        mListener = listener;
        mTransmitMode = transmitMode;
        switchCodec(codec);
        configurePreprocessState();

        // TODO support input resampling. We can't expect 48000hz on all android devices.
        int reportedMinBufferSize = AudioRecord.getMinBufferSize(Audio.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mMinBufferSize = Math.max(reportedMinBufferSize, mFrameSize);
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, mInputSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mMinBufferSize);
    }

    /**
     * Initializes and configures the Speex preprocessor.
     * Based off of Mumble project's AudioInput method resetAudioProcessor().
     */
    private void configurePreprocessState() {
        if(mPreprocessState != null)
            mPreprocessState.destroy();

        mPreprocessState = new Speex.SpeexPreprocessState(mFrameSize, Audio.SAMPLE_RATE);

        IntPointer arg = new IntPointer(1);

        arg.put(1);
        mPreprocessState.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_VAD, arg);
        mPreprocessState.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_AGC, arg);
        mPreprocessState.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_DENOISE, arg);
        mPreprocessState.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_DEREVERB, arg);

        arg.put(30000);
        mPreprocessState.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_AGC_TARGET, arg);

        // TODO AGC max gain, decrement, noise suppress, echo
    }

    /**
     * Switches to the specified codec, and deallocates old native encoders.
     * @param codec The codec to switch to.
     */
    public void switchCodec(JumbleUDPMessageType codec) {
        if(codec == mCodec)
            return;
        mCodec = codec;

        destroy(); // Deallocate old native objects

        IntPointer error = new IntPointer(1);
        switch (codec) {
            case UDPVoiceOpus:
                mOpusEncoder = Opus.opus_encoder_create(Audio.SAMPLE_RATE, 1, Opus.OPUS_APPLICATION_VOIP, error);

                IntPointer vbr = new IntPointer(1);
                vbr.put(0);
                Opus.opus_encoder_ctl(mOpusEncoder, Opus.OPUS_SET_VBR_REQUEST, vbr);
                break;
            case UDPVoiceCELTBeta:
//                mCELTBetaEncoder = CELT11.celt_encoder_create(Audio.SAMPLE_RATE, 1, error);
                break;
            case UDPVoiceCELTAlpha:
//                mCELTAlphaMode = CELT7.celt_mode_create(Audio.SAMPLE_RATE, Audio.FRAME_SIZE, error);
//                mCELTAlphaEncoder = CELT7.celt_encoder_create(mCELTAlphaMode, 1, error);
                break;
            case UDPVoiceSpeex:
                // TODO
                break;
        }
    }

    /**
     * Starts the recording thread.
     */
    public void startRecording() {
        synchronized (mRecordLock) {
            if(mRecording) {
                Log.w(Constants.TAG, "Attempted to start recording while already recording!");
                return;
            }

            mRecordThread = new Thread(this);
            mRecordThread.start();
        }
    }

    /**
     * Stops the record loop after the current iteration.
     */
    public void stopRecording() {
        synchronized (mRecordLock) {
            mRecording = false;
            mRecordThread = null;
        }
    }

    /**
     * Stops the record loop and waits on it to finish.
     * @throws InterruptedException
     */
    public void stopRecordingAndWait() throws InterruptedException {
        stopRecording();
        synchronized (mRecordLock) {
            mRecordLock.wait();
        }
    }

    public boolean isRecording() {
        synchronized (mRecordLock) {
            return mRecording;
        }
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        mRecording = true;
        mBufferedFrames = 0;
        mFrameCounter = 0;

        mAudioRecord.startRecording();

        if(mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED)
            return;
        final short[] audioData = new short[mFrameSize];

        // We loop when the 'recording' instance var is true instead of checking audio record state because we want to always cleanly shutdown.
        while(mRecording) {
            int shortsRead = mAudioRecord.read(audioData, 0, mFrameSize);
            if(shortsRead > 0) {
                int len;
                boolean encoded = false;
                mFrameCounter++;

                boolean talking = true;

                if(mTransmitMode == Constants.TRANSMIT_VOICE_ACTIVITY) {
                    // Check if audio input registered as probable speech.
                    IntPointer prob = new IntPointer(1);
                    mPreprocessState.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_GET_PROB, prob);
                    float speechProbablilty = (float)prob.get() / 100.0f;
                    // TODO use determined probability
                    // talking = ...?
                }

                if(!talking)
                    continue;

                // Run preprocessor on audio data. TODO echo!
                mPreprocessState.preprocess(audioData);

                switch (mCodec) {
                    case UDPVoiceOpus:
                        System.arraycopy(audioData, 0, mOpusBuffer, mFrameSize * mBufferedFrames, mFrameSize);
                        mBufferedFrames++;

                        if(!mRecording || mBufferedFrames >= mFramesPerPacket) {
                            if(mBufferedFrames < mFramesPerPacket)
                                mBufferedFrames = mFramesPerPacket; // If recording was stopped early, encode remaining empty frames too.

                            len = Opus.opus_encode(mOpusEncoder, mOpusBuffer, mFrameSize * mBufferedFrames, mEncodedBuffer, OPUS_MAX_BYTES);

                            if(len <= 0) {
                                mBufferedFrames = 0;
                                continue;
                            }

                            encoded = true;
                        }
                        break;
                    case UDPVoiceCELTBeta:
                        break;
                    case UDPVoiceCELTAlpha:
                        break;
                    case UDPVoiceSpeex:
                        break;
                }

                if(encoded)
                    sendFrame(mEncodedBuffer, !talking || !mRecording);
            } else {
                Log.e(Constants.TAG, "Error fetching audio! AudioRecord error "+shortsRead);
                mBufferedFrames = 0;
            }
        }

        mAudioRecord.stop();

        synchronized (mRecordLock) {
            mRecordLock.notify();
        }
    }

    /**
     * Sends the encoded frame to the server.
     * Volatile; depends on class state and must not be called concurrently.
     */
    private void sendFrame(byte[] frame, boolean terminator) {
        int frames = mBufferedFrames;
        mBufferedFrames = 0;

        Arrays.fill(mPacketBuffer, (byte) 0);

        int flags = 0;
        flags |= mCodec.ordinal() << 5;
        mPacketBuffer[0] = (byte) (flags & 0xFF);
        mPacketDataStream.rewind();
        mPacketDataStream.skip(1);
        mPacketDataStream.writeLong(mFrameCounter - frames);

        if(mCodec == JumbleUDPMessageType.UDPVoiceOpus) {
            long size = frame.length;
            if(terminator)
                size |= 1 << 13;
            mPacketDataStream.writeLong(size);
            mPacketDataStream.append(frame, frame.length);
        }

        mListener.onFrameEncoded(mPacketBuffer, mPacketDataStream.size(), mCodec);
    }

    /**
     * Deallocates native assets. Must be called on disconnect.
     */
    public void destroy() {
        if(mOpusEncoder != null)
            Opus.opus_encoder_destroy(mOpusEncoder);
        if(mPreprocessState != null)
            mPreprocessState.destroy();
    }

    @Override
    public void messageCodecVersion(Mumble.CodecVersion msg) {
//        if(msg.getOpus())
//            switchCodec(JumbleUDPMessageType.UDPVoiceOpus);
//        else if(msg.getPreferAlpha())
//            switchCodec(JumbleUDPMessageType.UDPVoiceCELTAlpha);
//        else
//            switchCodec(JumbleUDPMessageType.UDPVoiceCELTBeta);

    }
}
