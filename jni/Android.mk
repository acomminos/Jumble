#
# Copyright (C) 2013 Andrew Comminos
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

ROOT := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PATH          := $(ROOT)/speex/libspeex
LOCAL_MODULE        := speex
LOCAL_C_INCLUDES    := $(ROOT)/speex/include/
LOCAL_SRC_FILES		:= cb_search.c		exc_10_32_table.c 	exc_8_128_table.c 	filters.c \
					   gain_table.c 	hexc_table.c 		high_lsp_tables.c 	lsp.c \
					   ltp.c			speex.c 			stereo.c 			vbr.c \
					   vq.c bits.c 		exc_10_16_table.c	exc_20_32_table.c 	exc_5_256_table.c \
					   exc_5_64_table.c	gain_table_lbr.c 	hexc_10_32_table.c	lpc.c \
					   lsp_tables_nb.c 	modes.c 			modes_wb.c 			nb_celp.c \
					   quant_lsp.c		sb_celp.c			speex_callbacks.c 	speex_header.c \
					   window.c			resample.c			jitter.c            preprocess.c \
					   mdf.c            kiss_fft.c          kiss_fftr.c         fftwrap.c \
					   filterbank.c     scal.c \
					   $(ROOT)/speex.cpp
LOCAL_CFLAGS		:= -D__EMX__ -DUSE_KISS_FFT -DFLOATING_POINT -DEXPORT=''
LOCAL_CPP_FEATURES := exceptions
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PATH			:= $(ROOT)/celt-0.11.0-src/libcelt
LOCAL_MODULE		:= libcelt11
LOCAL_SRC_FILES		:= bands.c celt.c cwrs.c entcode.c entdec.c entenc.c header.c kiss_fft.c laplace.c mathops.c mdct.c modes.c pitch.c plc.c quant_bands.c rate.c vq.c $(ROOT)/celt11.cpp
LOCAL_C_INCLUDES    := $(ROOT)/celt-0.11.0-src/libcelt/
LOCAL_CFLAGS		:= -I$(ROOT)/celt-0.11.0-build -DHAVE_CONFIG_H -fvisibility=hidden
LOCAL_CPP_FEATURES := exceptions
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PATH			:= $(ROOT)/celt-0.7.0-src/libcelt
LOCAL_MODULE		:= libcelt7
LOCAL_SRC_FILES		:= bands.c celt.c cwrs.c entcode.c entdec.c entenc.c header.c kiss_fft.c kiss_fftr.c laplace.c mdct.c modes.c pitch.c psy.c quant_bands.c rangedec.c rangeenc.c rate.c vq.c $(ROOT)/celt7.cpp
LOCAL_C_INCLUDES    := $(ROOT)/celt-0.7.0-src/libcelt/
LOCAL_CFLAGS		:= -I$(ROOT)/celt-0.7.0-build -DHAVE_CONFIG_H -fvisibility=hidden
LOCAL_CPP_FEATURES := exceptions
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PATH			:= $(ROOT)/opus
LOCAL_MODULE		:= libopus
LOCAL_C_INCLUDES	:= $(ROOT)/opus/include $(ROOT)/opus/celt $(ROOT)/opus/silk $(ROOT)/opus/silk/float
LOCAL_SRC_FILES		:= src/opus.c \
src/opus_decoder.c \
src/opus_encoder.c \
src/opus_multistream.c \
src/opus_multistream_encoder.c \
src/opus_multistream_decoder.c \
src/analysis.c \
src/mlp.c \
src/mlp_data.c \
celt/bands.c \
celt/celt.c \
celt/celt_encoder.c \
celt/celt_decoder.c \
celt/cwrs.c \
celt/entcode.c \
celt/entdec.c \
celt/entenc.c \
celt/kiss_fft.c \
celt/laplace.c \
celt/mathops.c \
celt/mdct.c \
celt/modes.c \
celt/pitch.c \
celt/celt_lpc.c \
celt/quant_bands.c \
celt/rate.c \
celt/vq.c \
silk/CNG.c \
silk/code_signs.c \
silk/init_decoder.c \
silk/decode_core.c \
silk/decode_frame.c \
silk/decode_parameters.c \
silk/decode_indices.c \
silk/decode_pulses.c \
silk/decoder_set_fs.c \
silk/dec_API.c \
silk/enc_API.c \
silk/encode_indices.c \
silk/encode_pulses.c \
silk/gain_quant.c \
silk/interpolate.c \
silk/LP_variable_cutoff.c \
silk/NLSF_decode.c \
silk/NSQ.c \
silk/NSQ_del_dec.c \
silk/PLC.c \
silk/shell_coder.c \
silk/tables_gain.c \
silk/tables_LTP.c \
silk/tables_NLSF_CB_NB_MB.c \
silk/tables_NLSF_CB_WB.c \
silk/tables_other.c \
silk/tables_pitch_lag.c \
silk/tables_pulses_per_block.c \
silk/VAD.c \
silk/control_audio_bandwidth.c \
silk/quant_LTP_gains.c \
silk/VQ_WMat_EC.c \
silk/HP_variable_cutoff.c \
silk/NLSF_encode.c \
silk/NLSF_VQ.c \
silk/NLSF_unpack.c \
silk/NLSF_del_dec_quant.c \
silk/process_NLSFs.c \
silk/stereo_LR_to_MS.c \
silk/stereo_MS_to_LR.c \
silk/check_control_input.c \
silk/control_SNR.c \
silk/init_encoder.c \
silk/control_codec.c \
silk/A2NLSF.c \
silk/ana_filt_bank_1.c \
silk/biquad_alt.c \
silk/bwexpander_32.c \
silk/bwexpander.c \
silk/debug.c \
silk/decode_pitch.c \
silk/inner_prod_aligned.c \
silk/lin2log.c \
silk/log2lin.c \
silk/LPC_analysis_filter.c \
silk/LPC_inv_pred_gain.c \
silk/table_LSF_cos.c \
silk/NLSF2A.c \
silk/NLSF_stabilize.c \
silk/NLSF_VQ_weights_laroia.c \
silk/pitch_est_tables.c \
silk/resampler.c \
silk/resampler_down2_3.c \
silk/resampler_down2.c \
silk/resampler_private_AR2.c \
silk/resampler_private_down_FIR.c \
silk/resampler_private_IIR_FIR.c \
silk/resampler_private_up2_HQ.c \
silk/resampler_rom.c \
silk/sigm_Q15.c \
silk/sort.c \
silk/sum_sqr_shift.c \
silk/stereo_decode_pred.c \
silk/stereo_encode_pred.c \
silk/stereo_find_predictor.c \
silk/stereo_quant_pred.c \
silk/float/apply_sine_window_FLP.c \
silk/float/corrMatrix_FLP.c \
silk/float/encode_frame_FLP.c \
silk/float/find_LPC_FLP.c \
silk/float/find_LTP_FLP.c \
silk/float/find_pitch_lags_FLP.c \
silk/float/find_pred_coefs_FLP.c \
silk/float/LPC_analysis_filter_FLP.c \
silk/float/LTP_analysis_filter_FLP.c \
silk/float/LTP_scale_ctrl_FLP.c \
silk/float/noise_shape_analysis_FLP.c \
silk/float/prefilter_FLP.c \
silk/float/process_gains_FLP.c \
silk/float/regularize_correlations_FLP.c \
silk/float/residual_energy_FLP.c \
silk/float/solve_LS_FLP.c \
silk/float/warped_autocorrelation_FLP.c \
silk/float/wrappers_FLP.c \
silk/float/autocorrelation_FLP.c \
silk/float/burg_modified_FLP.c \
silk/float/bwexpander_FLP.c \
silk/float/energy_FLP.c \
silk/float/inner_product_FLP.c \
silk/float/k2a_FLP.c \
silk/float/levinsondurbin_FLP.c \
silk/float/LPC_inv_pred_gain_FLP.c \
silk/float/pitch_analysis_core_FLP.c \
silk/float/scale_copy_vector_FLP.c \
silk/float/scale_vector_FLP.c \
silk/float/schur_FLP.c \
silk/float/sort_FLP.c \
src/repacketizer.c \
$(ROOT)/opus.cpp
LOCAL_CFLAGS		:= -DOPUS_BUILD -DVAR_ARRAYS -Wno-traditional -DFLOATING_POINT
LOCAL_CPP_FEATURES := exceptions
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
