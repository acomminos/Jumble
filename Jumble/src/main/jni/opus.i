/* File : opus.i */

%module Opus

/* JNI load library, inserted in module java class */
%pragma(java) jniclasscode=%{
  static {
      System.loadLibrary("opus");
  }
%}

%import audio_types.i

/* Support opus_encoder_ctl use of variable length arguments */
%varargs(int* value) opus_encoder_ctl;

%{
#include <opus_defines.h>
#include <opus_types.h>
#include <opus.h>
%}

%include "opus/include/opus_defines.h"
%include "opus/include/opus_types.h"
%include "opus/include/opus.h"