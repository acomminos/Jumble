/*
 * Copyright (C) 2015 Andrew Comminos <andrew@comminos.com>
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

package com.morlunk.jumble.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.widget.Toast;

import com.morlunk.jumble.R;
import com.morlunk.jumble.exception.AudioInitializationException;

/**
 * Manages the state of Bluetooth SCO.
 * Created by andrew on 25/09/15.
 */
public class BluetoothScoReceiver extends BroadcastReceiver {
    private final Listener mListener;
    private final AudioManager mAudioManager;
    private boolean mBluetoothScoOn;

    public BluetoothScoReceiver(Context context, Listener listener) {
        mListener = listener;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int audioState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR);
        switch (audioState) {
            case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                mBluetoothScoOn = true;
                mListener.onBluetoothScoConnected();
                break;
            case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
            case AudioManager.SCO_AUDIO_STATE_ERROR:
                am.stopBluetoothSco();
                mBluetoothScoOn = false;
                mListener.onBluetoothScoDisconnected();
                break;
        }
    }

    public void startBluetoothSco() {
        mAudioManager.startBluetoothSco();
    }

    public void stopBluetoothSco() {
        mAudioManager.stopBluetoothSco();
        mBluetoothScoOn = false;
    }

    public boolean isBluetoothScoOn() {
        return mBluetoothScoOn;
    }

    public interface Listener {
        void onBluetoothScoConnected();
        void onBluetoothScoDisconnected();
    }
}
