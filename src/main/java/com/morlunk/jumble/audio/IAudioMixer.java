package com.morlunk.jumble.audio;

import java.util.Collection;

/**
 * A mixer for {@link IAudioMixerSource}s, where {@link T} is the source buffer type and {@link U}
 * is the destination buffer type.
 */
public interface IAudioMixer<T,U> {
    void mix(Collection<IAudioMixerSource<T>> sources, U buffer, int bufferOffset,
             int bufferLength);
}
