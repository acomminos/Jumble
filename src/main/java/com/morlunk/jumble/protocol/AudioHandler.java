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

package com.morlunk.jumble.protocol;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.R;
import com.morlunk.jumble.audio.AudioInput;
import com.morlunk.jumble.audio.AudioOutput;
import com.morlunk.jumble.audio.encoder.CELT11Encoder;
import com.morlunk.jumble.audio.encoder.CELT7Encoder;
import com.morlunk.jumble.audio.encoder.IEncoder;
import com.morlunk.jumble.audio.encoder.OpusEncoder;
import com.morlunk.jumble.audio.encoder.PreprocessingEncoder;
import com.morlunk.jumble.audio.encoder.ResamplingEncoder;
import com.morlunk.jumble.audio.inputmode.IInputMode;
import com.morlunk.jumble.exception.AudioException;
import com.morlunk.jumble.exception.AudioInitializationException;
import com.morlunk.jumble.exception.NativeAudioException;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.net.JumbleConnection;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.net.PacketBuffer;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.util.JumbleLogger;
import com.morlunk.jumble.util.JumbleNetworkListener;

/**
 * Bridges the protocol's audio messages to our input and output threads.
 * A useful intermediate for reducing code coupling.
 * Audio playback and recording is exclusively controlled by the protocol.
 * Changes to input/output instance vars after the audio threads have been initialized will recreate
 * them in most cases (they're immutable for the purpose of avoiding threading issues).
 * Calling shutdown() will cleanup both input and output threads. It is safe to restart after.
 * Created by andrew on 23/04/14.
 */
public class AudioHandler extends JumbleNetworkListener implements AudioInput.AudioInputListener {
    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SIZE = SAMPLE_RATE/100;
    public static final int MAX_BUFFER_SIZE = 960;

    private final Context mContext;
    private final JumbleLogger mLogger;
    private final AudioManager mAudioManager;
    private final AudioInput mInput;
    private final AudioOutput mOutput;
    private AudioOutput.AudioOutputListener mOutputListener;
    private AudioEncodeListener mEncodeListener;

    private int mSession;
    private JumbleUDPMessageType mCodec;
    private IEncoder mEncoder;
    private int mFrameCounter;

    private final int mAudioStream;
    private final int mAudioSource;
    private int mSampleRate;
    private int mBitrate;
    private int mFramesPerPacket;
    private final IInputMode mInputMode;
    private final float mAmplitudeBoost;

    private boolean mInitialized;
    /** True if the user is muted on the server. */
    private boolean mMuted;
    private boolean mBluetoothOn;
    private boolean mHalfDuplex;
    private boolean mPreprocessorEnabled;
    /** The last observed talking state. False if muted, or the input mode is not active. */
    private boolean mTalking;

    private final Object mEncoderLock;
    private byte mTargetId;

    public AudioHandler(Context context, JumbleLogger logger, int audioStream, int audioSource,
                        int sampleRate, int targetBitrate, int targetFramesPerPacket,
                        IInputMode inputMode, byte targetId, float amplitudeBoost,
                        boolean bluetoothEnabled, boolean halfDuplexEnabled,
                        boolean preprocessorEnabled, AudioEncodeListener encodeListener,
                        AudioOutput.AudioOutputListener outputListener) throws AudioInitializationException, NativeAudioException {
        mContext = context;
        mLogger = logger;
        mAudioStream = audioStream;
        mAudioSource = audioSource;
        mSampleRate = sampleRate;
        mBitrate = targetBitrate;
        mFramesPerPacket = targetFramesPerPacket;
        mInputMode = inputMode;
        mAmplitudeBoost = amplitudeBoost;
        mBluetoothOn = bluetoothEnabled;
        mHalfDuplex = halfDuplexEnabled;
        mPreprocessorEnabled = preprocessorEnabled;
        mEncodeListener = encodeListener;
        mOutputListener = outputListener;
        mTalking = false;
        mTargetId = targetId;

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mEncoderLock = new Object();

        mInput = new AudioInput(this, mAudioSource, mSampleRate);
        mOutput = new AudioOutput(mOutputListener);
    }

    /**
     * Starts the audio output and input threads.
     * Will create both the input and output modules if they haven't been created yet.
     */
    public synchronized void initialize(User self, int maxBandwidth, JumbleUDPMessageType codec) throws AudioException {
        if(mInitialized) return;
        mSession = self.getSession();

        setMaxBandwidth(maxBandwidth);
        setCodec(codec);
        setServerMuted(self.isMuted() || self.isLocalMuted() || self.isSuppressed());
        startRecording();
        // Ensure that if a bluetooth SCO connection is active, we use the VOICE_CALL stream.
        // This is required by Android for compatibility with SCO.
        mOutput.startPlaying(mBluetoothOn ? AudioManager.STREAM_VOICE_CALL : mAudioStream);

        mInitialized = true;
    }

    /**
     * Starts a recording AudioInput thread.
     * @throws AudioException if the input thread failed to initialize, or if a thread was already
     *                        recording.
     */
    private void startRecording() throws AudioException {
        synchronized (mInput) {
            if (!mInput.isRecording()) {
                mInput.startRecording();
            } else {
                throw new AudioException("Attempted to start recording while recording!");
            }
        }
    }

    /**
     * Stops the recording AudioInput thread.
     * @throws AudioException if there was no thread recording.
     */
    private void stopRecording() throws AudioException {
        synchronized (mInput) {
            if (mInput.isRecording()) {
                mInput.stopRecording();
            } else {
                throw new AudioException("Attempted to stop recording while not recording!");
            }
        }
    }

    /**
     * Sets whether or not the server wants the client muted.
     * @param muted Whether the user is muted on the server.
     */
    private void setServerMuted(boolean muted) throws AudioException {
        mMuted = muted;
    }

    /**
     * Returns whether or not the handler has been initialized.
     * @return true if the handler is ready to play and record audio.
     */
    public boolean isInitialized() {
        return mInitialized;
    }

    public boolean isPlaying() {
        synchronized (mOutput) {
            return mOutput.isPlaying();
        }
    }

    public JumbleUDPMessageType getCodec() {
        return mCodec;
    }

    public void recreateEncoder() throws NativeAudioException {
        setCodec(mCodec);
    }

    public void setCodec(JumbleUDPMessageType codec) throws NativeAudioException {
        mCodec = codec;

        if (mEncoder != null) {
            mEncoder.destroy();
            mEncoder = null;
        }

        IEncoder encoder;
        switch (codec) {
            case UDPVoiceCELTAlpha:
                encoder = new CELT7Encoder(SAMPLE_RATE, AudioHandler.FRAME_SIZE, 1,
                        mFramesPerPacket, mBitrate, MAX_BUFFER_SIZE);
                break;
            case UDPVoiceCELTBeta:
                encoder = new CELT11Encoder(SAMPLE_RATE, 1, mFramesPerPacket);
                break;
            case UDPVoiceOpus:
                encoder = new OpusEncoder(SAMPLE_RATE, 1, FRAME_SIZE, mFramesPerPacket, mBitrate,
                        MAX_BUFFER_SIZE);
                break;
            default:
                Log.w(Constants.TAG, "Unsupported codec, input disabled.");
                return;
        }

        if (mPreprocessorEnabled) {
            encoder = new PreprocessingEncoder(encoder, FRAME_SIZE, SAMPLE_RATE);
        }

        if (mInput.getSampleRate() != SAMPLE_RATE) {
            encoder = new ResamplingEncoder(encoder, 1, mInput.getSampleRate(), FRAME_SIZE, SAMPLE_RATE);
        }

        mEncoder = encoder;
    }

    public int getAudioStream() {
        return mAudioStream;
    }

    public int getAudioSource() {
        return mAudioSource;
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getBitrate() {
        return mBitrate;
    }

    /**
     * Sets the maximum bandwidth available for audio input as obtained from the server.
     * Adjusts the bitrate and frames per packet accordingly to meet the server's requirement.
     * @param maxBandwidth The server-reported maximum bandwidth, in bps.
     */
    private void setMaxBandwidth(int maxBandwidth) throws AudioException {
        if (maxBandwidth == -1) {
            return;
        }
        int bitrate = mBitrate;
        int framesPerPacket = mFramesPerPacket;
        // Logic as per desktop Mumble's AudioInput::adjustBandwidth for consistency.
        if (JumbleConnection.calculateAudioBandwidth(bitrate, framesPerPacket) > maxBandwidth) {
            if (framesPerPacket <= 4 && maxBandwidth <= 32000) {
                framesPerPacket = 4;
            } else if (framesPerPacket == 1 && maxBandwidth <= 64000) {
                framesPerPacket = 2;
            } else if (framesPerPacket == 2 && maxBandwidth <= 48000) {
                framesPerPacket = 4;
            }
            while (JumbleConnection.calculateAudioBandwidth(bitrate, framesPerPacket)
                    > maxBandwidth && bitrate > 8000) {
                bitrate -= 1000;
            }
        }
        bitrate = Math.max(8000, bitrate);

        if (bitrate != mBitrate ||
                framesPerPacket != mFramesPerPacket) {
            mBitrate = bitrate;
            mFramesPerPacket = framesPerPacket;

            mLogger.logInfo(mContext.getString(R.string.audio_max_bandwidth,
                    maxBandwidth/1000, maxBandwidth/1000, framesPerPacket * 10));
        }
    }

    public int getFramesPerPacket() {
        return mFramesPerPacket;
    }

    public float getAmplitudeBoost() {
        return mAmplitudeBoost;
    }

    /**
     * Returns whether or not the audio handler is operating in half duplex mode, muting outgoing
     * audio when incoming audio is received.
     * @return true if the handler is in half duplex mode.
     */
    public boolean isHalfDuplex() {
        return mHalfDuplex;
    }

    public int getCurrentBandwidth() {
        return JumbleConnection.calculateAudioBandwidth(mBitrate, mFramesPerPacket);
    }

    /**
     * Shuts down the audio handler, halting input and output.
     */
    public synchronized void shutdown() {
        synchronized (mInput) {
            mInput.shutdown();
        }
        synchronized (mOutput) {
            mOutput.stopPlaying();
        }
        synchronized (mEncoderLock) {
            if (mEncoder != null) {
                mEncoder.destroy();
                mEncoder = null;
            }
        }
        mInitialized = false;
        mBluetoothOn = false;

        mEncodeListener.onTalkingStateChanged(false);
    }


    @Override
    public void messageCodecVersion(Mumble.CodecVersion msg) {
        if (!mInitialized)
            return; // Only listen to change events in this handler.

        JumbleUDPMessageType codec;
        if (msg.hasOpus() && msg.getOpus()) {
            codec = JumbleUDPMessageType.UDPVoiceOpus;
        } else if (msg.hasBeta() && !msg.getPreferAlpha()) {
            codec = JumbleUDPMessageType.UDPVoiceCELTBeta;
        } else {
            codec = JumbleUDPMessageType.UDPVoiceCELTAlpha;
        }

        if (codec != mCodec) {
            try {
                synchronized (mEncoderLock) {
                    setCodec(codec);
                }
            } catch (NativeAudioException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void messageServerSync(Mumble.ServerSync msg) {
        try {
            setMaxBandwidth(msg.hasMaxBandwidth() ? msg.getMaxBandwidth() : -1);
        } catch (AudioException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void messageUserState(Mumble.UserState msg) {
        if (!mInitialized)
            return; // We shouldn't initialize on UserState- wait for ServerSync.

        // Stop audio input if the user is muted, and resume if the user has set talking enabled.
        if (msg.hasSession() && msg.getSession() == mSession &&
                (msg.hasMute() || msg.hasSelfMute() || msg.hasSuppress())) {
            try {
                setServerMuted(msg.getMute() || msg.getSelfMute() || msg.getSuppress());
            } catch (AudioException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void messageVoiceData(byte[] data, JumbleUDPMessageType messageType) {
        synchronized (mOutput) {
            mOutput.queueVoiceData(data, messageType);
        }
    }

    @Override
    public void onAudioInputReceived(short[] frame, int frameSize) {
        boolean talking = mInputMode.shouldTransmit(frame, frameSize);
        talking &= !mMuted;

        if (mTalking ^ talking) {
            mEncodeListener.onTalkingStateChanged(talking);
            if (mHalfDuplex) {
                mAudioManager.setStreamMute(getAudioStream(), talking);
            }

            synchronized (mEncoderLock) {
                // Terminate encoding when talking stops.
                if (!talking && mEncoder != null) {
                    try {
                        mEncoder.terminate();
                    } catch (NativeAudioException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (talking) {
            // Boost/reduce amplitude based on user preference
            // TODO: perhaps amplify to the largest value that does not result in clipping.
            if (mAmplitudeBoost != 1.0f) {
                for (int i = 0; i < frameSize; i++) {
                    // Java only guarantees the bounded preservation of sign in a narrowing
                    // primitive conversion from float -> int, not float -> int -> short.
                    float val = frame[i] * mAmplitudeBoost;
                    if (val > Short.MAX_VALUE) {
                        val = Short.MAX_VALUE;
                    } else if (val < Short.MIN_VALUE) {
                        val = Short.MIN_VALUE;
                    }
                    frame[i] = (short) val;
                }
            }

            synchronized (mEncoderLock) {
                if (mEncoder != null) {
                    try {
                        mEncoder.encode(frame, frameSize);
                        mFrameCounter++;
                    } catch (NativeAudioException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        synchronized (mEncoderLock) {
            if (mEncoder != null && mEncoder.isReady()) {
                sendEncodedAudio();
            }
        }

        mTalking = talking;
        if (!talking) {
            mInputMode.waitForInput();
        }
    }

    public void setVoiceTargetId(byte id) {
        mTargetId = id;
    }

    public void clearVoiceTarget() {
        // A target ID of 0 indicates normal talking.
        mTargetId = 0;
    }

    /**
     * Fetches the buffered audio from the current encoder and sends it to the server.
     */
    private void sendEncodedAudio() {
        int frames = mEncoder.getBufferedFrames();

        int flags = 0;
        flags |= mCodec.ordinal() << 5;
        flags |= mTargetId & 0x1F;

        final byte[] packetBuffer = new byte[1024];
        packetBuffer[0] = (byte) (flags & 0xFF);

        PacketBuffer ds = new PacketBuffer(packetBuffer, 1024);
        ds.skip(1);
        ds.writeLong(mFrameCounter - frames);
        mEncoder.getEncodedData(ds);
        int length = ds.size();
        ds.rewind();

        byte[] packet = ds.dataBlock(length);
        mEncodeListener.onAudioEncoded(packet, length);
    }

    public interface AudioEncodeListener {
        void onAudioEncoded(byte[] data, int length);
        void onTalkingStateChanged(boolean talking);
    }

    /**
     * A builder to configure and instantiate the audio protocol handler.
     */
    public static class Builder {
        private Context mContext;
        private JumbleLogger mLogger;
        private int mAudioStream;
        private int mAudioSource;
        private int mTargetBitrate;
        private int mTargetFramesPerPacket;
        private int mInputSampleRate;
        private float mAmplitudeBoost;
        private boolean mBluetoothEnabled;
        private boolean mHalfDuplexEnabled;
        private boolean mPreprocessorEnabled;
        private IInputMode mInputMode;
        private AudioEncodeListener mEncodeListener;
        private AudioOutput.AudioOutputListener mTalkingListener;

        public Builder setContext(Context context) {
            mContext = context;
            return this;
        }

        public Builder setLogger(JumbleLogger logger) {
            mLogger = logger;
            return this;
        }

        public Builder setAudioStream(int audioStream) {
            mAudioStream = audioStream;
            return this;
        }

        public Builder setAudioSource(int audioSource) {
            mAudioSource = audioSource;
            return this;
        }

        public Builder setTargetBitrate(int targetBitrate) {
            mTargetBitrate = targetBitrate;
            return this;
        }

        public Builder setTargetFramesPerPacket(int targetFramesPerPacket) {
            mTargetFramesPerPacket = targetFramesPerPacket;
            return this;
        }

        public Builder setInputSampleRate(int inputSampleRate) {
            mInputSampleRate = inputSampleRate;
            return this;
        }

        public Builder setAmplitudeBoost(float amplitudeBoost) {
            mAmplitudeBoost = amplitudeBoost;
            return this;
        }

        public Builder setBluetoothEnabled(boolean bluetoothEnabled) {
            mBluetoothEnabled = bluetoothEnabled;
            return this;
        }

        public Builder setHalfDuplexEnabled(boolean halfDuplexEnabled) {
            mHalfDuplexEnabled = halfDuplexEnabled;
            return this;
        }

        public Builder setPreprocessorEnabled(boolean preprocessorEnabled) {
            mPreprocessorEnabled = preprocessorEnabled;
            return this;
        }

        public Builder setEncodeListener(AudioEncodeListener encodeListener) {
            mEncodeListener = encodeListener;
            return this;
        }

        public Builder setTalkingListener(AudioOutput.AudioOutputListener talkingListener) {
            mTalkingListener = talkingListener; // TODO: remove user dependency from AudioOutput
            return this;
        }

        public Builder setInputMode(IInputMode inputMode) {
            mInputMode = inputMode;
            return this;
        }

        /**
         * Creates a new AudioHandler for the given session and begins managing input/output.
         * @return An initialized audio handler.
         */
        public AudioHandler initialize(User self, int maxBandwidth, JumbleUDPMessageType codec, byte targetId) throws AudioException {
            AudioHandler handler = new AudioHandler(mContext, mLogger, mAudioStream, mAudioSource,
                    mInputSampleRate, mTargetBitrate, mTargetFramesPerPacket, mInputMode, targetId,
                    mAmplitudeBoost, mBluetoothEnabled, mHalfDuplexEnabled,
                    mPreprocessorEnabled, mEncodeListener, mTalkingListener);
            handler.initialize(self, maxBandwidth, codec);
            return handler;
        }
    }
}
