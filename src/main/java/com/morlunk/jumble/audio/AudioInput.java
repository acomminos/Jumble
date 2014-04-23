/*
 * Copyright (C) 2014 Andrew Comminos
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
import android.util.Log;

import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Loader;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.audio.javacpp.CELT11;
import com.morlunk.jumble.audio.javacpp.CELT7;
import com.morlunk.jumble.audio.javacpp.Opus;
import com.morlunk.jumble.audio.javacpp.Speex;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.net.PacketDataStream;

import java.util.Arrays;

/**
 * Created by andrew on 23/08/13.
 */
public class AudioInput implements Runnable {

    static {
        Loader.load(Opus.class); // Do this so we can reference IntPointer and the like earlier.
    }

    public interface AudioInputListener {
        /**
         * Called when a frame has finished processing, and is ready to go to the server.
         * @param data The encoded audio data.
         * @param length The length of the encoded audio data.
         * @param messageType The codec of the encoded data.
         */
        public void onFrameEncoded(byte[] data, int length, JumbleUDPMessageType messageType);

        public void onTalkStateChanged(boolean talking);
    }

    public static final int[] SAMPLE_RATES = { 48000, 44100, 22050, 16000, 11025, 8000 };
    private static final int SPEEX_RESAMPLE_QUALITY = 3;
    private static final int OPUS_MAX_BYTES = 512; // Opus specifies 4000 bytes as a recommended value for encoding, but the official mumble project uses 512.
    private static final int SPEECH_DETECT_THRESHOLD = (int) (0.25 * Math.pow(10, 9)); // Continue speech for 250ms to prevent dropping

    private IEncoder mEncoder;
    private Speex.SpeexPreprocessState mPreprocessState;
    private Speex.SpeexResampler mResampler;

    // AudioRecord state
    private AudioInputListener mListener;
    private AudioRecord mAudioRecord;
    private int mSampleRate = -1;
    private int mFrameSize = Audio.FRAME_SIZE;
    private int mMicFrameSize = Audio.FRAME_SIZE;

    // Preferences
    private int mAudioSource;
    private int mBitrate;
    private int mFramesPerPacket;
    private int mTransmitMode;
    private float mVADThreshold;
    private float mAmplitudeBoost = 1.0f;

    // Encoder state
    final short[] mAudioBuffer = new short[mFrameSize];
    final short[] mResampleBuffer = new short[mMicFrameSize];
    final short[] mOpusBuffer;
    final byte[][] mCELTBuffer;

    private final byte[] mEncodedBuffer = new byte[OPUS_MAX_BYTES];
    private final byte[] mPacketBuffer = new byte[1024];
    private int mBufferedFrames = 0;
    private int mFrameCounter;

    private JumbleUDPMessageType mCodec = null;

    private final Object mRecordLock = new Object(); // Make sure we don't get calls to start and stop recording more than once at a time.
    private Thread mRecordThread;
    private boolean mRecording;

    public AudioInput(AudioInputListener listener, JumbleUDPMessageType codec, int audioSource, int targetSampleRate, int bitrate, int framesPerPacket, int transmitMode, float vadThreshold, float amplitudeBoost) throws InvalidSampleRateException, NativeAudioException {
        mListener = listener;
        mAudioSource = audioSource;
        mSampleRate = getSupportedSampleRate(targetSampleRate);
        mBitrate = bitrate;
        mFramesPerPacket = framesPerPacket;
        mTransmitMode = transmitMode;
        mVADThreshold = vadThreshold;
        mAmplitudeBoost = amplitudeBoost;

        mAudioRecord = createAudioRecord();

        mOpusBuffer = new short[mFrameSize * mFramesPerPacket];
        mCELTBuffer = new byte[mFramesPerPacket][Audio.SAMPLE_RATE/800];

        configureResampler();
        configurePreprocessState();
        setCodec(codec);
    }

    /**
     * Checks if the preferred sample rate is supported, and use it if so. Otherwise, automatically find a supported rate.
     * @param preferredSampleRate The preferred sample rate.
     * @return The preferred sample rate if supported, otherwise the next best one.
     */
    private int getSupportedSampleRate(int preferredSampleRate) {
        // Attempt to use preferred rate first
        if(preferredSampleRate != -1) {
            int bufferSize = AudioRecord.getMinBufferSize(preferredSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if(bufferSize > 0) return preferredSampleRate;
        }

        // Use the highest sample rate we can get.
        for(int rate : SAMPLE_RATES) {
            if(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0) {
                Log.w(Constants.TAG, "Failed to use desired sample rate, falling back to "+rate+"Hz.");
                return rate;
            }
        }

        // If all else fails, return the android default.
        return 48000;
    }

    /**
     * Creates a Speex resampler if the input sample rate differs from the protocol's.
     */
    private void configureResampler() {
        if(mResampler != null) mResampler.destroy();

        if(mSampleRate != Audio.SAMPLE_RATE) {
            mResampler = new Speex.SpeexResampler(1, mSampleRate, Audio.SAMPLE_RATE, SPEEX_RESAMPLE_QUALITY);
        }
    }

    /**
     * Initializes and configures the Speex preprocessor.
     * Based off of Mumble project's AudioInput method resetAudioProcessor().
     */
    private void configurePreprocessState() {
        if(mPreprocessState != null) mPreprocessState.destroy();

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

        // Increase VAD difficulty
        arg.put(99);
        mPreprocessState.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_GET_PROB_START, arg);
    }

    private AudioRecord createAudioRecord() throws InvalidSampleRateException {
        int reportedMinBufferSize = AudioRecord.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(reportedMinBufferSize, mFrameSize);

        AudioRecord audioRecord;
        try {
            audioRecord = new AudioRecord(mAudioSource, mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        } catch (IllegalArgumentException e) {
            throw new InvalidSampleRateException(e);
        }

        Log.i(Constants.TAG, "AudioInput: " + mBitrate + "bps, " + mFramesPerPacket + " frames/packet, " + mSampleRate + "hz");

        return audioRecord;
    }

    private void setCodec(JumbleUDPMessageType codec) throws NativeAudioException {
        mCodec = codec;

        Log.v(Constants.TAG, "Using codec "+codec.toString()+" for input");

        switch (codec) {
            case UDPVoiceOpus:
                mEncoder = new Opus.OpusEncoder(Audio.SAMPLE_RATE, 1);
                break;
            case UDPVoiceCELTBeta:
                mEncoder = new CELT11.CELT11Encoder(Audio.SAMPLE_RATE, 1);
                break;
            case UDPVoiceCELTAlpha:
                mEncoder = new CELT7.CELT7Encoder(Audio.SAMPLE_RATE, mFrameSize, 1);
                break;
//            case UDPVoiceSpeex:
                // TODO
//                break;
            default:
                return;
        }
        mEncoder.setBitrate(mBitrate);
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

            mRecording = true;
            mRecordThread = new Thread(this);
            mRecordThread.start();
        }
    }

    /**
     * Stops the record loop after the current iteration.
     */
    public void stopRecording() {
        synchronized (mRecordLock) {
            if(!mRecording) return;
            mRecording = false;
            mRecordThread = null;
        }
    }

    /**
     * Stops the record loop and waits on it to finish.
     * Releases native audio resources.
     * NOTE: It is safe to call startRecording after.
     * @throws InterruptedException
     */
    public void shutdown() {
        synchronized (mRecordLock) {
            if(mRecording) {
                mRecording = false;
                try {
                    mRecordThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        mRecordThread = null;
        mCodec = null;

        if(mAudioRecord != null) {
            mAudioRecord.release();
            mAudioRecord = null;
        }
        if(mEncoder != null) {
            mEncoder.destroy();
            mEncoder = null;
        }
        if(mPreprocessState != null) {
            mPreprocessState.destroy();
            mPreprocessState = null;
        }
        if(mResampler != null) {
            mResampler.destroy();
            mResampler = null;
        }
        if(mAudioRecord != null) {
            mAudioRecord.release();
            mAudioRecord = null;
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

        if(mCodec == null) {
            Log.v(Constants.TAG, "Tried to start recording without a codec version!");
            return;
        }

        boolean vadLastDetected = false;
        long vadLastDetectedTime = 0;
        mBufferedFrames = 0;
        mFrameCounter = 0;

        mAudioRecord.startRecording();

        if(mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED)
            return;

        if(mTransmitMode == Constants.TRANSMIT_CONTINUOUS || mTransmitMode == Constants.TRANSMIT_PUSH_TO_TALK)
            mListener.onTalkStateChanged(true);

        // We loop when the 'recording' instance var is true instead of checking audio record state because we want to always cleanly shutdown.
        while(mRecording) {
            int shortsRead = mAudioRecord.read(mResampler != null ? mResampleBuffer : mAudioBuffer, 0, mResampler != null ? mMicFrameSize : mFrameSize);
            if(shortsRead > 0) {
                int len;
                boolean encoded = false;
                mFrameCounter++;

                // Resample if necessary
                if(mResampler != null) mResampler.resample(mResampleBuffer, mAudioBuffer);

                // Run preprocessor on audio data. TODO echo!
                mPreprocessState.preprocess(mAudioBuffer);

                // Boost/reduce amplitude based on user preference
                if(mAmplitudeBoost != 1.0f) {
                    for(int i = 0; i < mFrameSize; i++) {
                        mAudioBuffer[i] *= mAmplitudeBoost;
                        if(mAudioBuffer[i] > Short.MAX_VALUE) mAudioBuffer[i] = Short.MAX_VALUE;
                        else if(mAudioBuffer[i] < Short.MIN_VALUE) mAudioBuffer[i] = Short.MIN_VALUE;
                    }
                }

                boolean talking = true;

                if(mTransmitMode == Constants.TRANSMIT_VOICE_ACTIVITY) {
                    // Use a logarithmic energy-based scale for VAD.
                    float sum = 1.0f;
                    for (int i = 0; i < mFrameSize; i++) {
                        sum += mAudioBuffer[i] * mAudioBuffer[i];
                    }
                    float micLevel = (float) Math.sqrt(sum / (float)mFrameSize);
                    float peakSignal = (float) (20.0f*Math.log10(micLevel / 32768.0f))/96.0f;
                    talking = (peakSignal+1) >= mVADThreshold;

                    /* Record the last time where VAD was detected in order to prevent speech dropping. */
                    if(talking) vadLastDetectedTime = System.nanoTime();

//                    Log.v(Constants.TAG, String.format("Signal: %2f, Threshold: %2f", peakSignal+1, mVADThreshold));
                    talking |= (System.nanoTime() - vadLastDetectedTime) < SPEECH_DETECT_THRESHOLD;

                    if(talking ^ vadLastDetected) // Update the service with the new talking state if we detected voice.
                        mListener.onTalkStateChanged(talking);
                    vadLastDetected = talking;
                }

                if(!talking) {
                    continue;
                }

                // TODO integrate this switch's behaviour into IEncoder implementations
                switch (mCodec) {
                    case UDPVoiceOpus:
                        System.arraycopy(mAudioBuffer, 0, mOpusBuffer, mFrameSize * mBufferedFrames, mFrameSize);
                        mBufferedFrames++;

                        if((!mRecording && mBufferedFrames > 0) || mBufferedFrames >= mFramesPerPacket) {
                            if(mBufferedFrames < mFramesPerPacket)
                                mBufferedFrames = mFramesPerPacket; // If recording was stopped early, encode remaining empty frames too.

                            try {
                                mEncoder.encode(mOpusBuffer, mFrameSize * mBufferedFrames, mEncodedBuffer, OPUS_MAX_BYTES);
                                encoded = true;
                            } catch (NativeAudioException e) {
                                mBufferedFrames = 0;
                                continue;
                            }
                        }
                        break;
                    case UDPVoiceCELTBeta:
                    case UDPVoiceCELTAlpha:
                        try {
                            mEncoder.encode(mAudioBuffer, mFrameSize, mCELTBuffer[mBufferedFrames], Audio.SAMPLE_RATE/800);
                            mBufferedFrames++;
                            encoded = mBufferedFrames >= mFramesPerPacket || (!mRecording && mBufferedFrames > 0);
                        } catch (NativeAudioException e) {
                            mBufferedFrames = 0;
                            continue;
                        }
                        break;
                    case UDPVoiceSpeex:
                        break;
                }

                if(encoded) sendFrame(!mRecording);
            } else {
                Log.e(Constants.TAG, "Error fetching audio! AudioRecord error "+shortsRead);
                mBufferedFrames = 0;
            }
        }

        mAudioRecord.stop();

        mListener.onTalkStateChanged(false);
    }

    /**
     * Sends the encoded frame to the server.
     * Volatile; depends on class state and must not be called concurrently.
     */
    private void sendFrame(boolean terminator) {
        int frames = mBufferedFrames;
        mBufferedFrames = 0;

        int flags = 0;
        flags |= mCodec.ordinal() << 5;

        Arrays.fill(mPacketBuffer, (byte) 0);
        mPacketBuffer[0] = (byte) (flags & 0xFF);

        PacketDataStream ds = new PacketDataStream(mPacketBuffer, 1024);
        ds.skip(1);
        ds.writeLong(mFrameCounter - frames);

        if(mCodec == JumbleUDPMessageType.UDPVoiceOpus) {
            byte[] frame = mEncodedBuffer;
            long size = frame.length;
            if(terminator)
                size |= 1 << 13;
            ds.writeLong(size);
            ds.append(frame, frame.length);
        } else {
            for (int x=0;x<frames;x++) {
                byte[] frame = mCELTBuffer[x];
                int head = frame.length;
                if(x < frames-1)
                   head |= 0x80;
                ds.append(head);
                ds.append(frame, frame.length);
            }
        }

        mListener.onFrameEncoded(mPacketBuffer, ds.size() + 1, mCodec);
    }
}
