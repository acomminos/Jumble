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
import android.util.Log;

import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Loader;
import com.googlecode.javacpp.Pointer;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.audio.javacpp.CELT11;
import com.morlunk.jumble.audio.javacpp.CELT7;
import com.morlunk.jumble.audio.javacpp.Opus;
import com.morlunk.jumble.audio.javacpp.Speex;
import com.morlunk.jumble.net.JumbleConnectionException;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.net.PacketDataStream;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.protocol.ProtocolHandler;

import java.util.Arrays;

/**
 * Created by andrew on 23/08/13.
 */
public class AudioInput extends ProtocolHandler implements Runnable {

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

    private AudioInputListener mListener;
    private int mTransmitMode = Constants.TRANSMIT_PUSH_TO_TALK;
    private float mVADThreshold = 0;
    private boolean mVADLastDetected = false;
    private long mVADLastDetectedTime;
    private float mAmplitudeBoost;

    private AudioRecord mAudioRecord;
    private int mAudioSource;
    private int mMinBufferSize;
    private int mSampleRate = -1;
    private int mQuality;
    private int mFrameSize = Audio.FRAME_SIZE;
    private int mMicFrameSize = Audio.FRAME_SIZE;
    private int mFramesPerPacket;

    // Temporary encoder state
    private final short[] mOpusBuffer;

    // CELT encoded frame buffer
    private final byte[][] mCELTBuffer;

    private final byte[] mEncodedBuffer = new byte[OPUS_MAX_BYTES];
    private final byte[] mPacketBuffer = new byte[1024];
    private final PacketDataStream mPacketDataStream = new PacketDataStream(mPacketBuffer, 1024);
    private int mBufferedFrames = 0;
    private int mFrameCounter;

    private JumbleUDPMessageType mCodec = null;

    private final Object mRecordLock = new Object(); // Make sure we don't get calls to start and stop recording more than once at a time.
    private Thread mRecordThread;
    private boolean mRecording;

    /**
     * Creates a new audio input manager configured for the specified codec.
     * @param listener
     */
    public AudioInput(JumbleService service, JumbleUDPMessageType codec, int audioSource, int sampleRate, int quality, int transmitMode, float voiceThreshold, float amplitudeBoost, int framesPerPacket, AudioInputListener listener) throws NativeAudioException {
        super(service);
        mListener = listener;
        mAudioSource = audioSource;
        mTransmitMode = transmitMode;
        mVADThreshold = voiceThreshold;
        mAmplitudeBoost = amplitudeBoost;
        mFramesPerPacket = framesPerPacket;
        mQuality = quality;
        mSampleRate = getSupportedSampleRate(sampleRate);
        switchCodec(codec);
        configurePreprocessState();

        mCELTBuffer = new byte[mFramesPerPacket][Audio.SAMPLE_RATE/800];
        mOpusBuffer = new short[mFrameSize*mFramesPerPacket];

        if(mSampleRate != -1) {
            if(mSampleRate != Audio.SAMPLE_RATE) {
                mResampler = new Speex.SpeexResampler(1, mSampleRate, Audio.SAMPLE_RATE, SPEEX_RESAMPLE_QUALITY);
                mMicFrameSize = (mFrameSize * mSampleRate)/Audio.SAMPLE_RATE;
            }
        } else {
            throw new RuntimeException("Device does not support any compatible input sampling rates!");
        }

        int reportedMinBufferSize = AudioRecord.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mMinBufferSize = Math.max(reportedMinBufferSize, mFrameSize);

        Log.i(Constants.TAG, "AudioInput: " + mQuality + "bps, " + mFramesPerPacket + " frames/packet, " + mSampleRate + "hz");
    }

    /**
     * Checks if the preferred sample rate is supported, and use it if so. Otherwise, automatically find a supported rate.
     * @param preferredSampleRate The preferred sample rate, or -1 if there is no preference.
     * @return The preferred sample rate if supported, otherwise a default one.
     *         If no rates are supported, return -1.
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
                if(preferredSampleRate != -1) getService().logWarning("Failed to use desired sample rate, falling back to "+rate+"Hz.");
                return rate;
            }
        }

        return -1;
    }

    public void setVADThreshold(float threshold) {
        mVADThreshold = threshold;
    }

    public void setAmplitudeBoost(float boost) {
        mAmplitudeBoost = boost;
    }

    public void setTransmitMode(int transmitMode) {
        mTransmitMode = transmitMode;
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

    /**
     * Switches to the specified codec, and deallocates old native encoders.
     * @param codec The codec to switch to.
     */
    public void switchCodec(JumbleUDPMessageType codec) throws NativeAudioException {
        if(codec == mCodec) return;
        mCodec = codec;

        Log.v(Constants.TAG, "Using codec "+codec.toString()+" for input");

        if(mEncoder != null) mEncoder.destroy();

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
            case UDPVoiceSpeex:
                // TODO
//                break;
                return;
        }
        mEncoder.setBitrate(mQuality);
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

            if(mAudioRecord == null) {
                mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mMinBufferSize);

                // If we're still uninitialized, we have a problem.
                if(mAudioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
                    getService().onConnectionError(new JumbleConnectionException("Failed to initialize audio input!", false));
                    getService().disconnect();
                    return;
                }
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
        if(!mRecording) return;

        synchronized (mRecordLock) {
            mRecording = false;
            mRecordThread = null;
        }
    }

    /**
     * Stops the record loop and waits on it to finish.
     * @throws InterruptedException
     */
    public void stopRecordingAndWait() {
        if(!mRecording) return;

        synchronized (mRecordLock) {
            mRecording = false;
            try {
                mRecordLock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mRecordThread = null;
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

        mBufferedFrames = 0;
        mFrameCounter = 0;
        mVADLastDetected = false;

        mAudioRecord.startRecording();

        if(mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED)
            return;

        final short[] audioData = new short[mFrameSize];
        final short[] resampleBuffer = new short[mMicFrameSize];

        if(mTransmitMode == Constants.TRANSMIT_CONTINUOUS || mTransmitMode == Constants.TRANSMIT_PUSH_TO_TALK)
            mListener.onTalkStateChanged(true);

        // We loop when the 'recording' instance var is true instead of checking audio record state because we want to always cleanly shutdown.
        while(mRecording || mBufferedFrames > 0) { // Make sure we clear all buffered frames before stopping. FIXME- second 'or' condition is experimental, untested.
            int shortsRead = mAudioRecord.read(mResampler != null ? resampleBuffer : audioData, 0, mResampler != null ? mMicFrameSize : mFrameSize);
            if(shortsRead > 0) {
                int len;
                boolean encoded = false;
                mFrameCounter++;

                // Resample if necessary
                if(mResampler != null)
                    mResampler.resample(resampleBuffer, audioData);

                // Run preprocessor on audio data. TODO echo!
                mPreprocessState.preprocess(audioData);

                // Boost/reduce amplitude based on user preference
                if(mAmplitudeBoost != 1.0f) {
                    for(int i = 0; i < mFrameSize; i++) {
                        audioData[i] *= mAmplitudeBoost;
                        if(audioData[i] > Short.MAX_VALUE) audioData[i] = Short.MAX_VALUE;
                        else if(audioData[i] < Short.MIN_VALUE) audioData[i] = Short.MIN_VALUE;
                    }
                }

                boolean talking = true;

                if(mTransmitMode == Constants.TRANSMIT_VOICE_ACTIVITY) {
                    // Use a logarithmic energy-based scale for VAD.
                    float sum = 1.0f;
                    for (int i = 0; i < mFrameSize; i++) {
                        sum += audioData[i] * audioData[i];
                    }
                    float micLevel = (float) Math.sqrt(sum / (float)mFrameSize);
                    float peakSignal = (float) (20.0f*Math.log10(micLevel / 32768.0f))/96.0f;
                    talking = (peakSignal+1) >= mVADThreshold;

                    /* Record the last time where VAD was detected in order to prevent speech dropping. */
                    if(talking) mVADLastDetectedTime = System.nanoTime();

//                    Log.v(Constants.TAG, String.format("Signal: %2f, Threshold: %2f", peakSignal+1, mVADThreshold));
                    talking |= (System.nanoTime() - mVADLastDetectedTime) < SPEECH_DETECT_THRESHOLD;

                    if(talking ^ mVADLastDetected) // Update the service with the new talking state if we detected voice.
                        mListener.onTalkStateChanged(talking);
                    mVADLastDetected = talking;
                }

                if(!talking) {
                    // If talking has terminated before we have filled the buffer, send anyway.
                    if(mBufferedFrames > 0) sendFrame(true);
                    continue;
                }

                // TODO integrate this switch's behaviour into IEncoder implementations
                switch (mCodec) {
                    case UDPVoiceOpus:
                        System.arraycopy(audioData, 0, mOpusBuffer, mFrameSize * mBufferedFrames, mFrameSize);
                        mBufferedFrames++;

                        if(!mRecording || mBufferedFrames >= mFramesPerPacket) {
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
                            mEncoder.encode(audioData, mFrameSize, mCELTBuffer[mBufferedFrames], Audio.SAMPLE_RATE/800);
                            mBufferedFrames++;
                            encoded = mBufferedFrames >= mFramesPerPacket || !talking;
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

        if(mBufferedFrames > 0) sendFrame(!mRecording);

        mAudioRecord.stop();

        mListener.onTalkStateChanged(false);

        synchronized (mRecordLock) {
            mRecordLock.notify();
        }
    }

    /**
     * Sends the encoded frame to the server.
     * Volatile; depends on class state and must not be called concurrently.
     */
    private void sendFrame(boolean terminator) {
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
            byte[] frame = mEncodedBuffer;
            long size = frame.length;
            if(terminator)
                size |= 1 << 13;
            mPacketDataStream.writeLong(size);
            mPacketDataStream.append(frame, frame.length);
        } else {
            for (int x=0;x<frames;x++) {
                byte[] frame = mCELTBuffer[x];
                int head = frame.length;
                if(x < frames-1)
                   head |= 0x80;
                mPacketDataStream.append(head);
                mPacketDataStream.append(frame, frame.length);
            }
        }

        mListener.onFrameEncoded(mPacketBuffer, mPacketDataStream.size(), mCodec);
    }

    /**
     * Deallocates native assets. Must be called on disconnect.
     */
    public void destroy() {
        if(mEncoder != null) mEncoder.destroy();
        if(mPreprocessState != null)
            mPreprocessState.destroy();
        if(mResampler != null)
            mResampler.destroy();
        if(mAudioRecord != null) mAudioRecord.release();
    }

    @Override
    public void messageCodecVersion(Mumble.CodecVersion msg) {
        // FIXME: CELT 0.11.0 (beta) does not work.
        try {
            if(msg.hasOpus() && msg.getOpus())
                switchCodec(JumbleUDPMessageType.UDPVoiceOpus);
            else if(msg.hasBeta() && msg.getBeta() == Constants.CELT_11_VERSION && !(msg.hasPreferAlpha() && msg.getPreferAlpha()))
                switchCodec(JumbleUDPMessageType.UDPVoiceCELTBeta);
            else if(msg.hasAlpha() && msg.getAlpha() == Constants.CELT_7_VERSION)
                switchCodec(JumbleUDPMessageType.UDPVoiceCELTAlpha);
        } catch (NativeAudioException e) {
            e.printStackTrace();
            // TODO handle native codec failure
        }

    }
}
