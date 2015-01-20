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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.Log;
import android.widget.Toast;

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
import com.morlunk.jumble.exception.AudioException;
import com.morlunk.jumble.exception.AudioInitializationException;
import com.morlunk.jumble.exception.NativeAudioException;
import com.morlunk.jumble.model.Message;
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
 * Changes to input/output instance vars after the audio threads have been initialized will recreate
 * them in most cases (they're immutable for the purpose of avoiding threading issues).
 * Calling shutdown() will cleanup both input and output threads. It is safe to restart after.
 * Created by andrew on 23/04/14.
 */
public class AudioHandler extends JumbleNetworkListener implements AudioInput.AudioInputListener {
    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SIZE = SAMPLE_RATE/100;

    private Context mContext;
    private JumbleLogger mLogger;
    private AudioManager mAudioManager;
    private AudioInput mInput;
    private AudioOutput mOutput;
    private AudioOutput.AudioOutputListener mOutputListener;
    private AudioEncodeListener mEncodeListener;

    private JumbleUDPMessageType mCodec;
    private IEncoder mEncoder;
    private int mFrameCounter;

    private int mAudioStream;
    private int mAudioSource;
    private int mSampleRate;
    private int mBitrate;
    private int mFramesPerPacket;
    private int mTransmitMode;
    private float mVADThreshold;
    private float mAmplitudeBoost = 1.0f;

    private boolean mInitialized;
    private boolean mBluetoothOn;
    private boolean mHalfDuplex;
    private boolean mPreprocessorEnabled;

    private final Object mEncoderLock;

    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int audioState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR);
            switch (audioState) {
                case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                    Toast.makeText(mContext, R.string.bluetooth_connected, Toast.LENGTH_LONG).show();
                    mOutput.stopPlaying();
                    mBluetoothOn = true;
                    try {
                        mOutput.startPlaying(true);
                    } catch (AudioInitializationException e) {
                        e.printStackTrace();
                        mLogger.log(Message.Type.WARNING, e.getLocalizedMessage());
                    }
                    break;
                case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                case AudioManager.SCO_AUDIO_STATE_ERROR:
                    if(mOutput.isPlaying() && mBluetoothOn)
                        Toast.makeText(mContext, R.string.bluetooth_disconnected, Toast.LENGTH_LONG).show();
                    mOutput.stopPlaying();
                    try {
                        mOutput.startPlaying(false);
                    } catch (AudioInitializationException e) {
                        e.printStackTrace();
                        mLogger.log(Message.Type.WARNING, e.getLocalizedMessage());
                    }
                    mBluetoothOn = false;
                    break;
            }
        }
    };

    public AudioHandler(Context context, JumbleLogger logger, AudioEncodeListener encodeListener,
                        AudioOutput.AudioOutputListener outputListener) {
        mContext = context;
        mLogger = logger;
        mEncodeListener = encodeListener;
        mOutputListener = outputListener;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mEncoderLock = new Object();
    }

    /**
     * Configures a new audio input thread with the handler's settings.
     * Automatically starts recording if mTransitMode is continuous or voice activity.
     * Will cleanup old input thread if applicable.
     * @throws AudioException if the AudioInput instance failed to initialize.
     */
    private void createAudioInput() throws AudioException {
        if(mInput != null) mInput.shutdown();

        mInput = new AudioInput(this, mAudioSource, mSampleRate, mTransmitMode,
                                       mVADThreshold, mAmplitudeBoost);
        if(mTransmitMode == Constants.TRANSMIT_VOICE_ACTIVITY || mTransmitMode == Constants.TRANSMIT_CONTINUOUS) {
            mInput.startRecording();
        }
    }

    /**
     * Configures a new audio output thread with the handler's settings.
     * Will cleanup old output thread if applicable.
     */
    private void createAudioOutput() {
        if(mInitialized) mOutput.stopPlaying();
        mOutput = new AudioOutput(mOutputListener, mAudioStream);
    }

    /**
     * Starts the audio output thread. Will create both the input and output modules if they
     * haven't been created yet. If the codec information has not yet been received from the server,
     * we'll initialize input once we receive that.
     */
    public synchronized void initialize() throws AudioException {
        if(mInitialized) return;
        if(mOutput == null) createAudioOutput();
        if(mInput == null) createAudioInput();
        // This sticky broadcast will initialize the audio output.
        mContext.registerReceiver(mBluetoothReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));
        mInitialized = true;
    }

    /**
     * Starts a recording AudioInput thread.
     * @throws AudioException if the input thread failed to initialize, or if a thread was already
     *                        recording.
     */
    public synchronized void startRecording() throws AudioException {
        if(mInput == null) createAudioInput();

        if (!mInput.isRecording()) {
            mInput.startRecording();
            if (mHalfDuplex && mTransmitMode == Constants.TRANSMIT_PUSH_TO_TALK) {
                mAudioManager.setStreamMute(getAudioStream(), true);
            }
        } else {
            throw new AudioException("Attempted to start recording while recording!");
        }
    }

    /**
     * Stops the recording AudioInput thread.
     * @throws AudioException if there was no thread recording.
     */
    public synchronized void stopRecording() throws AudioException {
        if(mInput == null) return;

        if (mInput.isRecording()) {
            mInput.stopRecording();
            if (mHalfDuplex && mTransmitMode == Constants.TRANSMIT_PUSH_TO_TALK) {
                mAudioManager.setStreamMute(getAudioStream(), false);
            }
        } else {
            throw new AudioException("Attempted to stop recording while not recording!");
        }
    }

    /**
     * Returns whether or not the handler has been initialized.
     * @return true if the handler is ready to play and record audio.
     */
    public boolean isInitialized() {
        return mInitialized;
    }

    public boolean isRecording() {
        return mInput != null && mInput.isRecording();
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
                                                  mFramesPerPacket, mBitrate);
                break;
            case UDPVoiceCELTBeta:
                encoder = new CELT11Encoder(SAMPLE_RATE, 1, mFramesPerPacket);
                break;
            case UDPVoiceOpus:
                encoder = new OpusEncoder(SAMPLE_RATE, 1, FRAME_SIZE, mFramesPerPacket, mBitrate);
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

    /**
     * Sets the output stream for audio, such as {@link android.media.AudioManager#STREAM_VOICE_CALL}.
     * The output thread will be automatically recreated if currently playing.
     * @param audioStream A constant representing an Android stream. Found in {@link android.media.AudioManager}.
     */
    public void setAudioStream(int audioStream) {
        this.mAudioStream = audioStream;
        if(mInitialized) createAudioOutput();
    }

    public int getAudioSource() {
        return mAudioSource;
    }

    /**
     * Sets the input source for audio, i.e. back microphone, front microphone.
     * The input thread will be automatically respawned if currently recording.
     * @param audioSource A constant representing an Android audio source. Found in {@link android.media.MediaRecorder.AudioSource}.
     */
    public void setAudioSource(int audioSource) throws AudioException {
        this.mAudioSource = audioSource;
        if(mInitialized) createAudioInput();
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    /**
     * Attempts to set the sample rate of the audio input thread.
     * If the desired sample rate is not supported, the next best one will be chosen.
     * The input thread will be automatically respawned if currently recording.
     * @param sampleRate The desired sample rate.
     */
    public void setSampleRate(int sampleRate) throws AudioException {
        this.mSampleRate = sampleRate;
        if(mInitialized) createAudioInput();
    }

    public int getBitrate() {
        return mBitrate;
    }

    /**
     * Sets the bitrate of the encoder.
     * Triggers an encoder recreation if initialized.
     * @param bitrate The desired bitrate.
     */
    public void setBitrate(int bitrate) throws NativeAudioException {
        this.mBitrate = bitrate;
        synchronized (mEncoderLock) {
            if (mCodec != null && mEncoder != null) {
                recreateEncoder();
            }
        }
    }

    /**
     * Sets the maximum bandwidth available for audio input as obtained from the server.
     * Adjusts the bitrate and frames per packet accordingly to meet the server's requirement.
     * @param maxBandwidth The server-reported maximum bandwidth, in bps.
     */
    public void setMaxBandwidth(int maxBandwidth) throws AudioException {
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
            setBitrate(bitrate);
            setFramesPerPacket(framesPerPacket);

            mLogger.log(Message.Type.INFO, mContext.getString(R.string.audio_max_bandwidth,
                    maxBandwidth/1000, maxBandwidth/1000, framesPerPacket * 10));
        }
    }

    public int getFramesPerPacket() {
        return mFramesPerPacket;
    }

    /**
     * Sets the number of frames per packet to be encoded before sending to the server.
     * Recreates encoder, if created.
     * @param framesPerPacket The number of frames per audio packet.
     */
    public void setFramesPerPacket(int framesPerPacket) throws AudioException {
        this.mFramesPerPacket = framesPerPacket;
        synchronized (mEncoderLock) {
            if (mCodec != null && mEncoder != null) {
                recreateEncoder();
            }
        }
    }

    public int getTransmitMode() {
        return mTransmitMode;
    }

    /**
     * Sets the transmit mode for the audio input thread.
     * The input thread will be automatically respawned if currently recording.
     * @param transmitMode A transmission mode constant found in {@link Constants}.
     */
    public void setTransmitMode(int transmitMode) throws AudioException {
        this.mTransmitMode = transmitMode;
        if(mInitialized) createAudioInput();
    }

    public float getVADThreshold() {
        return mVADThreshold;
    }

    /**
     * Sets the threshold for voice activation transmission.
     * Does not require input thread recreation.
     * @param threshold An arbitrary floating point value to use in determining speech.
     */
    public void setVADThreshold(float threshold) {
        this.mVADThreshold = threshold;
        if(mInitialized) mInput.setVADThreshold(threshold);
    }

    public float getAmplitudeBoost() {
        return mAmplitudeBoost;
    }

    /**
     * Sets an amplitude boost multiplier for audio input.
     * Does not require input thread recreation.
     * @param boost An floating point value to multiply raw PCM data by in the range [0, {@link Float#MAX_VALUE}].
     */
    public void setAmplitudeBoost(float boost) {
        this.mAmplitudeBoost = boost;
        if(mInitialized) mInput.setAmplitudeBoost(boost);
    }

    /**
     * Returns whether or not the audio handler is operating in half duplex mode, muting outgoing
     * audio when incoming audio is received.
     * @return true if the handler is in half duplex mode.
     */
    public boolean isHalfDuplex() {
        return mHalfDuplex;
    }

    /**
     * Sets whether or not the audio handler should operate in half duplex mode, muting outgoing
     * audio when incoming audio is received.
     * Does not require input thread recreation.
     * @param halfDuplex Whether to enable half duplex mode.
     */
    public void setHalfDuplex(boolean halfDuplex) {
        mHalfDuplex = halfDuplex;
    }

    /**
     * Sets whether to enable the Speex preprocessor.
     * Does not require input thread recreation.
     * @param preprocessorEnabled Whether to enable the Speex preprocessor.
     */
    public void setPreprocessorEnabled(boolean preprocessorEnabled) {
        mPreprocessorEnabled = preprocessorEnabled;
        // FIXME
    }

    public int getCurrentBandwidth() {
        return JumbleConnection.calculateAudioBandwidth(mBitrate, mFramesPerPacket);
    }

    /**
     * Shuts down the audio handler, halting input and output.
     * The handler may still be reinitialized with {@link AudioHandler#initialize()} after.
     */
    public synchronized void shutdown() {
        if(mInput != null) {
            mInput.shutdown();
            mInput = null;
        }
        if(mOutput != null) {
            mOutput.stopPlaying();
            mOutput = null;
        }
        if (mEncoder != null) {
            mEncoder.destroy();
            mEncoder = null;
        }
        if(mInitialized) {
            try {
                mContext.unregisterReceiver(mBluetoothReceiver);
            } catch (IllegalArgumentException e) {
                e.printStackTrace(); // Called if the registration failed, and we try and unregister nothing.
            }
        }
        mInitialized = false;
        mBluetoothOn = false;
        // Restore audio manager mode
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        audioManager.stopBluetoothSco();
    }

    @Override
    public void messageCodecVersion(Mumble.CodecVersion msg) {
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
    public void messageVoiceData(byte[] data, JumbleUDPMessageType messageType) {
        mOutput.queueVoiceData(data, messageType);
    }

    @Override
    public void onTalkStateChange(User.TalkState state) {
        synchronized (mEncoderLock) {
            if (mEncoder != null && state == User.TalkState.PASSIVE) {
                try {
                    mEncoder.terminate();
                    if (mEncoder.isReady()) {
                        sendEncodedAudio();
                    }
                } catch (NativeAudioException e) {
                    e.printStackTrace();
                }
            }
        }
        mEncodeListener.onTalkStateChange(state);
    }

    @Override
    public void onAudioInputReceived(short[] frame, int frameSize) {
        synchronized (mEncoderLock) {
            if (mEncoder != null) {
                try {
                    mEncoder.encode(frame, frameSize);
                    mFrameCounter++;
                } catch (NativeAudioException e) {
                    e.printStackTrace();
                    return;
                }

                if (mEncoder.isReady()) {
                    sendEncodedAudio();
                }
            }
        }
    }

    /**
     * Fetches the buffered audio from the current encoder and sends it to the server.
     */
    private void sendEncodedAudio() {
        int frames = mEncoder.getBufferedFrames();

        int flags = 0;
        flags |= mCodec.ordinal() << 5;

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
        public void onAudioEncoded(byte[] data, int length);
        public void onTalkStateChange(User.TalkState state);
    }
}
