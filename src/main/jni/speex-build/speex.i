/* File : speex.i */

%module Speex

/* JNI load library, inserted in module java class */
%pragma(java) jniclasscode=%{
  static {
      System.loadLibrary("speex");
  }
%}

%import audio_types.i

/* Handle some speex-specific cases where we have to pass pointer data as java types */
%apply int *INOUT { void *ptr }

/* Remove underscore */
%rename(JitterBufferPacket) _JitterBufferPacket;

%{
#include <speex/speex.h>
#include <speex/speex_types.h>
#include <speex/speex_bits.h>
#include <speex/speex_jitter.h>
#include <speex/speex_echo.h>
#include <speex/speex_resampler.h>

/* Jitter buffer packet creation wrapper. Makes it easier to deal with char arrays. */
JitterBufferPacket wrap_create_jitter_buffer_packet(char *data, unsigned int len, unsigned int timestamp, unsigned int span) {
    JitterBufferPacket jbp;
    char *allocData = malloc(len);
    memcpy(allocData, data, len);
    jbp.data = allocData;
    jbp.len = len;
    jbp.timestamp = timestamp;
    jbp.span = span;
}

%}

%include "speex/include/speex/speex.h"
%include "speex/include/speex/speex_bits.h"
%include "speex/include/speex/speex_types.h"
%include "speex/include/speex/speex_echo.h"
%include "speex/include/speex/speex_resampler.h"

%include "speex/include/speex/speex_jitter.h"
%apply (char *STRING, int LENGTH) { (char *data, unsigned int len) };
JitterBufferPacket wrap_create_jitter_buffer_packet(char *data, unsigned int len, unsigned int timestamp, unsigned int span);

%typemap(out) struct JitterBufferPacket %{
    JitterBufferPacket packet = $1;
    data = (*env)->NewByteArray(*env, packet.len)
    (*env)->SetByteArrayRegion(*env, data, 0, packet.len, packet.data);
    len = packet.len;
    timestamp = packet.timestamp;
    span = packet.span;
%}