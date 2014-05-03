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

package com.morlunk.jumble.protocol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.widget.Toast;

import com.morlunk.jumble.Constants;
import com.morlunk.jumble.R;
import com.morlunk.jumble.audio.AudioInput;
import com.morlunk.jumble.audio.AudioOutput;
import com.morlunk.jumble.exception.AudioException;
import com.morlunk.jumble.exception.AudioInitializationException;
import com.morlunk.jumble.exception.NativeAudioException;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.jumble.protobuf.Mumble;
import com.morlunk.jumble.util.JumbleNetworkListener;

/**
 * Bridges the protocol's audio messages to our input and output threads.
 * A useful intermediate for reducing code coupling.
 * Changes to input/output instance vars after the audio threads have been initialized will recreate
 * them in most cases (they're immutable for the purpose of avoiding threading issues).
 * Calling shutdown() will cleanup both input and output threads. It is safe to restart after.
 * Created by andrew on 23/04/14.
 */
public class AudioHandler extends JumbleNetworkListener {
    private Context mContext;
    private AudioInput mInput;
    private AudioOutput mOutput;
    private AudioInput.AudioInputListener mInputListener;
    private AudioOutput.AudioOutputListener mOutputListener;

    private JumbleUDPMessageType mCodec = JumbleUDPMessageType.UDPVoiceOpus;
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

    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int audioState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR);
            switch (audioState) {
                case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                    Toast.makeText(mContext, R.string.bluetooth_connected, Toast.LENGTH_LONG).show();
                    mOutput.stopPlaying();
                    mBluetoothOn = true;
                    mOutput.startPlaying(true);
                    break;
                case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                case AudioManager.SCO_AUDIO_STATE_ERROR:
                    if(mOutput.isPlaying() && mBluetoothOn)
                        Toast.makeText(mContext, R.string.bluetooth_disconnected, Toast.LENGTH_LONG).show();
                    mOutput.stopPlaying();
                    mOutput.startPlaying(false);
                    mBluetoothOn = false;
                    break;
            }
        }
    };

    public AudioHandler(Context context, AudioInput.AudioInputListener inputListener, AudioOutput.AudioOutputListener outputListener) {
        mContext = context;
        mInputListener = inputListener;
        mOutputListener = outputListener;
    }

    /**
     * Configures a new audio input thread with the handler's settings.
     * Automatically starts recording if mTransitMode is continuous or voice activity.
     * Will cleanup old input thread if applicable.
     * @throws AudioException if the AudioInput instance failed to initialize.
     */
    private void createAudioInput() throws AudioException {
        if(mInput != null) mInput.shutdown();

        mInput = new AudioInput(mInputListener, mCodec, mAudioSource, mSampleRate, mBitrate, mFramesPerPacket, mTransmitMode, mVADThreshold, mAmplitudeBoost);
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
    public void initialize() throws AudioException {
        if(mInitialized) return;
        if(mOutput == null) createAudioOutput();
        if(mInput == null && mCodec != null) createAudioInput();
        // This sticky broadcast will initialize the audio output.
        mContext.registerReceiver(mBluetoothReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));
        mInitialized = true;
    }

    public void startRecording() throws AudioException {
        if(mInput == null) createAudioInput();
        mInput.startRecording();
    }

    public void stopRecording() {
        if(mInput == null) return;
        mInput.stopRecording();
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
     * Sets the bitrate of the audio input thread.
     * Does not require input thread recreation.
     * @param bitrate The desired bitrate.
     */
    public void setBitrate(int bitrate) {
        this.mBitrate = bitrate;
        if(mInput != null) mInput.setBitrate(bitrate);
    }

    public int getFramesPerPacket() {
        return mFramesPerPacket;
    }

    /**
     * Sets the number of frames per packet to be encoded before sending to the server.
     * The input thread will be automatically respawned if currently recording.
     * @param framesPerPacket The number of frames per audio packet.
     */
    public void setFramesPerPacket(int framesPerPacket) throws AudioException {
        this.mFramesPerPacket = framesPerPacket;
        if(mInput != null) createAudioInput();
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
     * Shuts down the audio handler, halting input and output.
     * The handler may still be reinitialized with {@link AudioHandler#initialize()} after.
     */
    public void shutdown() {
        if(mInput != null) {
            mInput.shutdown();
            mInput = null;
        }
        if(mOutput != null) {
            mOutput.stopPlaying();
            mContext.unregisterReceiver(mBluetoothReceiver);
            mOutput = null;
        }
        mInitialized = false;
        mBluetoothOn = false;
        // Restore audio manager mode
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        audioManager.stopBluetoothSco();
    }

    @Override
    public void messageCodecVersion(Mumble.CodecVersion msg) {
        if (msg.hasOpus() && msg.getOpus()) {
            mCodec = JumbleUDPMessageType.UDPVoiceOpus;
        } else if (msg.hasBeta() && !msg.getPreferAlpha()) {
            mCodec = JumbleUDPMessageType.UDPVoiceCELTBeta;
        } else {
            mCodec = JumbleUDPMessageType.UDPVoiceCELTAlpha;
        }
        if(mInitialized) {
            try {
                createAudioInput();
            } catch (AudioException e) {
                e.printStackTrace();
                // TODO handle gracefully
            }
        }
    }

    @Override
    public void messageVoiceData(byte[] data, JumbleUDPMessageType messageType) {
        mOutput.queueVoiceData(data, messageType);
    }
}
