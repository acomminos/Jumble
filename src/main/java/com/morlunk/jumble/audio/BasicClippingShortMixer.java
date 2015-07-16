package com.morlunk.jumble.audio;

import java.util.Collection;

/**
 * A simple mixer that downsamples source floating point PCM to shorts, clipping naively.
 */
public class BasicClippingShortMixer implements IAudioMixer<float[], short[]> {
    @Override
    public void mix(Collection<IAudioMixerSource<float[]>> sources, short[] buffer, int bufferOffset,
                    int bufferLength) {
        for (int i = 0; i < bufferLength; i++) {
            float mix = 0;
            for (IAudioMixerSource<float[]> source : sources) {
                mix += source.getSamples()[i];
            }
            // Clip to [-1,1].
            if (mix > 1)
                mix = 1;
            else if (mix < -1)
                mix = -1;
            buffer[i + bufferOffset] = (short) (mix * Short.MAX_VALUE);
        }
    }
}
