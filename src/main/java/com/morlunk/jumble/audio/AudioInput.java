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

import com.googlecode.javacpp.BytePointer;
import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Loader;
import com.googlecode.javacpp.Pointer;
import com.googlecode.javacpp.ShortPointer;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.audio.javacpp.Opus;
import com.morlunk.jumble.net.JumbleMessageHandler;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.net.PacketDataStream;
import com.morlunk.jumble.protobuf.Mumble;

import java.nio.ShortBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private volatile Pointer mOpusEncoder;;
//    private SWIGTYPE_p_OpusEncoder mOpusEncoder;
//    private com.morlunk.jumble.audio.celt11.SWIGTYPE_p_CELTEncoder mCELTBetaEncoder;
//    private com.morlunk.jumble.audio.celt7.SWIGTYPE_p_CELTMode mCELTAlphaMode;
//    private com.morlunk.jumble.audio.celt7.SWIGTYPE_p_CELTEncoder mCELTAlphaEncoder;

    private AudioInputListener mListener;

    private AudioRecord mAudioRecord;
    private int mMinBufferSize;
    private int mInputSampleRate = 48000; //44100; FIXME
    private int mFrameSize = Audio.FRAME_SIZE;
    private int mFramesPerPacket = 2;

    // Temporary encoder state
    private final short[] mOpusBuffer = new short[mFrameSize*mFramesPerPacket];
    private final byte[] mEncodedBuffer = new byte[OPUS_MAX_BYTES];
    private int mBufferedFrames = 0;
    private int mFrameCounter;

    private JumbleUDPMessageType mCodec = null;

    private Object mRecordLock = new Object(); // Make sure we don't get calls to start and stop recording more than once at a time.
    private Thread mRecordThread;
    private boolean mRecording;

    private ExecutorService mEncodingThread = Executors.newSingleThreadExecutor();

    /**
     * Creates a new audio input manager configured for the specified codec.
     * @param listener
     */
    public AudioInput(JumbleUDPMessageType codec, AudioInputListener listener) {
        mListener = listener;
        switchCodec(codec);

        // TODO support input resampling. We can't expect 48000hz on all android devices.
        int reportedMinBufferSize = AudioRecord.getMinBufferSize(Audio.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mMinBufferSize = Math.max(reportedMinBufferSize, mFrameSize);
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, mInputSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mMinBufferSize);
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

                switch (mCodec) {
                    case UDPVoiceOpus:
                        System.arraycopy(audioData, 0, mOpusBuffer, mFrameSize * mBufferedFrames, mFrameSize);
                        mBufferedFrames++;

                        if(!mRecording || mBufferedFrames >= mFramesPerPacket) {
                            if(mBufferedFrames < mFramesPerPacket)
                                mBufferedFrames = mFramesPerPacket; // If recording was stopped early, encode remaining empty frames too.IntPointer sampleRate = new IntPointer(1);

                            len = Opus.opus_encode(mOpusEncoder, mOpusBuffer, mFrameSize*mBufferedFrames, mEncodedBuffer, OPUS_MAX_BYTES);

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
                    sendFrame(mEncodedBuffer);
            } else {
                Log.e(Constants.TAG, "Error fetching audio! AudioRecord error "+shortsRead);
            }
        }

        mAudioRecord.stop();

        synchronized (mRecordLock) {
            mRecordLock.notify();
        }
    }

    /**
     * Sends the encoded frame to the server.
     */
    private void sendFrame(byte[] frame) {

        int frames = mBufferedFrames;
        mBufferedFrames = 0;

        byte[] packet = new byte[1024];
        int flags = 0;
        flags |= mCodec.ordinal() << 5;
        packet[0] = (byte) (flags & 0xFF);

        PacketDataStream pds = new PacketDataStream(packet, packet.length);
        pds.skip(1);
        pds.writeLong(mFrameCounter - frames);

        if(mCodec == JumbleUDPMessageType.UDPVoiceOpus) {
            long size = frame.length;
            pds.writeLong(size);
            pds.append(frame, frame.length);
        }

        mListener.onFrameEncoded(packet, pds.size(), mCodec);
    }

    /**
     * Deallocates native assets. Must be called on disconnect.
     */
    public void destroy() {
        if(mOpusEncoder != null)
            Opus.opus_encoder_destroy(mOpusEncoder);
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
