/* File : celt.i */

%module CELT11

/* JNI load library, inserted in module java class */
%pragma(java) jniclasscode=%{
  static {
      System.loadLibrary("celt11");
  }
%}

%import ../audio_types.i

/* Support celt_encoder_ctl use of variable length arguments */
%varargs(int* value) celt_encoder_ctl;

/* Rename some CELT types to differentiate them by version */
/* DOES NOT WORK TODO FIX */
%rename(CELT11Decoder) CELTDecoder;
%rename(CELT11Encoder) CELTEncoder;
%rename(CELT11Mode) CELTMode;

%{
#include <celt_types.h>
#include <celt.h>
%}

%include "../celt-0.11.0-src/libcelt/celt_types.h"
%include "../celt-0.11.0-src/libcelt/celt.h"