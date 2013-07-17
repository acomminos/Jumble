#!/bin/sh

pushd ${0%/*}

# Speex
rm -r ../java/com/morlunk/jumble/audio/speex/*
swig -java -package com.morlunk.jumble.audio.speex -outdir ../java/com/morlunk/jumble/audio/speex speex.i

# CELT
rm -r ../java/com/morlunk/jumble/audio/celt/*
swig -java -package com.morlunk.jumble.audio.celt -outdir ../java/com/morlunk/jumble/audio/celt celt.i

# Opus
rm -r ../java/com/morlunk/jumble/audio/opus/*
swig -java -package com.morlunk.jumble.audio.opus -outdir ../java/com/morlunk/jumble/audio/opus opus.i

popd
