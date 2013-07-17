/* File : celt.i */

%module CELT

/* JNI load library, inserted in module java class */
%pragma(java) jniclasscode=%{
  static {
      System.loadLibrary("celt");
  }
%}

%import audio_types.i

/* Support celt_encoder_ctl use of variable length arguments */
%varargs(int* value) celt_encoder_ctl;

%{
#include <celt_types.h>
#include <celt.h>
%}

%include "celt/libcelt/celt_types.h"
%include "celt/libcelt/celt.h"