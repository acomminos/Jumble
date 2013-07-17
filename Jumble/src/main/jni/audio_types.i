/* File: audio_types.i */

/* Boilerplate type management for audio libs */

/* Use this to resolve types to primitives */
#define __EMX__ 1

/* use Java byte[] instead of String for char* */
%import various.i
%apply char *BYTE { char * }
%apply char *BYTE { char [] }
%apply char *BYTE { unsigned char * }
%apply char *BYTE { unsigned char [] }

/* Wrap pointers to java arrays */
%include "typemaps.i"
%apply int *INOUT { int * }
%apply int *INOUT { unsigned int * }
%apply short *INOUT { short * }
%apply float *INOUT { float * }
%apply int *INOUT { void * }