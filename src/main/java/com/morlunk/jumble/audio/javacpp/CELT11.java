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

package com.morlunk.jumble.audio.javacpp;

import com.googlecode.javacpp.IntPointer;
import com.googlecode.javacpp.Pointer;
import com.googlecode.javacpp.annotation.Platform;

/**
 * Created by andrew on 20/10/13.
 */
@Platform(library="celt11", cinclude={"<celt.h>","<celt_types.h>"})
public class CELT11 {

    public static class Decoder {

        private Pointer mNativeDecoder;
        private int mError;

        public Decoder(int sampleRate, int channels) {
            IntPointer error = new IntPointer(1);
            mNativeDecoder = celt_decoder_init(sampleRate, channels, error);
            mError = error.get();
        }

        public int getError() {
            return mError;
        }
    }

    private static native Pointer celt_decoder_init(int sampleRate, int channels, IntPointer error);
}
