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
import com.morlunk.jumble.audio.InvalidSampleRateException;
import com.morlunk.jumble.audio.NativeAudioException;
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

    private boolean mPlaying;
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
     */
    private void configureAudioInput() {
        if(mInput != null) mInput.shutdown();

        try {
            mInput = new AudioInput(mInputListener, mCodec, mAudioSource, mSampleRate, mBitrate, mFramesPerPacket, mTransmitMode, mVADThreshold, mAmplitudeBoost);
            if(mTransmitMode == Constants.TRANSMIT_VOICE_ACTIVITY || mTransmitMode == Constants.TRANSMIT_CONTINUOUS) {
                mInput.startRecording();
            }
        } catch (InvalidSampleRateException e) {
            e.printStackTrace();
            // TODO error handling
//            onConnectionError(new JumbleConnectionException(e, false));
        } catch (NativeAudioException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures a new audio output thread with the handler's settings.
     * Will cleanup old output thread if applicable.
     */
    private void configureAudioOutput() {
        if(mOutput != null) mOutput.stopPlaying();
        mOutput = new AudioOutput(mOutputListener, mAudioStream);
    }

    public void startAudioOutput() {
        if(mPlaying) return;
        if(mOutput == null) configureAudioOutput();
        // This sticky broadcast will initialize the audio output.
        mContext.registerReceiver(mBluetoothReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));
        mPlaying = true;
    }

    public void startRecording() {
        if(mInput == null) configureAudioInput();
        mInput.startRecording();
    }

    public void stopRecording() {
        if(mInput == null) return;
        mInput.stopRecording();
    }


    public JumbleUDPMessageType getCodec() {
        return mCodec;
    }

    public int getAudioStream() {
        return mAudioStream;
    }

    public void setAudioStream(int mAudioStream) {
        this.mAudioStream = mAudioStream;
        if(mOutput != null) configureAudioOutput();
    }

    public int getAudioSource() {
        return mAudioSource;
    }

    public void setAudioSource(int mAudioSource) {
        this.mAudioSource = mAudioSource;
        if(mInput != null) configureAudioInput();
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public void setSampleRate(int mSampleRate) {
        this.mSampleRate = mSampleRate;
        if(mInput != null) configureAudioInput();
    }

    public int getBitrate() {
        return mBitrate;
    }

    public void setBitrate(int mBitrate) {
        this.mBitrate = mBitrate;
        if(mInput != null) configureAudioInput();
    }

    public int getFramesPerPacket() {
        return mFramesPerPacket;
    }

    public void setFramesPerPacket(int mFramesPerPacket) {
        this.mFramesPerPacket = mFramesPerPacket;
        if(mInput != null) configureAudioInput();
    }

    public int getTransmitMode() {
        return mTransmitMode;
    }

    public void setTransmitMode(int mTransmitMode) {
        this.mTransmitMode = mTransmitMode;
        if(mInput != null) configureAudioInput();
    }

    public float getVADThreshold() {
        return mVADThreshold;
    }

    public void setVADThreshold(float mVADThreshold) {
        this.mVADThreshold = mVADThreshold;
        if(mInput != null) configureAudioInput();
    }

    public float getAmplitudeBoost() {
        return mAmplitudeBoost;
    }

    public void setAmplitudeBoost(float mAmplitudeBoost) {
        this.mAmplitudeBoost = mAmplitudeBoost;
        if(mInput != null) configureAudioInput();
    }

    public boolean isRecording() {
        return mInput != null && mInput.isRecording();
    }

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
        configureAudioInput(); // Audio input is started when we first get the codec information.
    }

    @Override
    public void messageVoiceData(byte[] data, JumbleUDPMessageType messageType) {
        mOutput.queueVoiceData(data, messageType);
    }
}
