/* File : celt.i */

%module CELT7


/* JNI load library, inserted in module java class */
%pragma(java) jniclasscode=%{
  static {
      System.loadLibrary("celt7");
  }
%}

%import ../audio_types.i

/* Support celt_encoder_ctl use of variable length arguments */
%varargs(int* value) celt_encoder_ctl;

/* Rename some CELT types to differentiate them by version */
/* DOES NOT WORK TODO FIX */
%rename(CELT7Decoder) CELTDecoder;
%rename(CELT7Encoder) CELTEncoder;
%rename(CELT7Mode) CELTMode;

%{
#include <celt.h>
#include <celt_types.h>
%}

%include "../celt-0.7.0-src/libcelt/celt.h"
%include "../celt-0.7.0-src/libcelt/celt_types.h"
