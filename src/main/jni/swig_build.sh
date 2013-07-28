#!/bin/sh

pushd ${0%/*}

# Speex
rm -r ../java/com/morlunk/jumble/audio/speex/*
swig -java -package com.morlunk.jumble.audio.speex -outdir ../java/com/morlunk/jumble/audio/speex speex-build/speex.i

# CELT11
rm -r ../java/com/morlunk/jumble/audio/celt11/*
swig -java -package com.morlunk.jumble.audio.celt11 -outdir ../java/com/morlunk/jumble/audio/celt11 celt-0.11.0-build/celt.i

# CELT7
rm -r ../java/com/morlunk/jumble/audio/celt7/*
swig -java -package com.morlunk.jumble.audio.celt7 -outdir ../java/com/morlunk/jumble/audio/celt7 celt-0.7.0-build/celt.i


# Opus
rm -r ../java/com/morlunk/jumble/audio/opus/*
swig -java -package com.morlunk.jumble.audio.opus -outdir ../java/com/morlunk/jumble/audio/opus opus-build/opus.i

popd
