/* File : speex.i */

%module Speex

/* JNI load library, inserted in module java class */
%pragma(java) jniclasscode=%{
  static {
      System.loadLibrary("speex");
  }
%}

%import audio_types.i

%{
#include <speex/speex_types.h>
#include <speex/speex_jitter.h>
#include <speex/speex_echo.h>
#include <speex/speex_resampler.h>
%}

/* Remove underscore */
%rename(JitterBufferPacket) _JitterBufferPacket;

%include "speex/include/speex/speex_types.h"
%include "speex/include/speex/speex_jitter.h"
%include "speex/include/speex/speex_echo.h"
%include "speex/include/speex/speex_resampler.h"