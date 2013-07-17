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
					   ../../speex_wrap.c
LOCAL_CFLAGS		:= -D__EMX__ -DUSE_KISS_FFT -DFIXED_POINT -DEXPORT=''
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PATH			:= $(ROOT)/celt/libcelt
LOCAL_MODULE		:= libcelt
LOCAL_SRC_FILES		:= bands.c			celt.c				cwrs.c				dump_modes.c \
					   entcode.c		entdec.c			entenc.c			header.c \
					   kiss_fft.c		laplace.c			mdct.c				modes.c \
					   pitch.c			quant_bands.c		rangedec.c			rangeenc.c \
					   rate.c			vq.c \
					   ../../celt_wrap.c
LOCAL_C_INCLUDES    := $(ROOT)/celt/libcelt/
LOCAL_CFLAGS		:= -I$(ROOT)/celt_wrapper -DHAVE_CONFIG_H -fvisibility=hidden
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PATH			:= $(ROOT)/opus
LOCAL_MODULE		:= libopus
LOCAL_C_INCLUDES	:= $(ROOT)/opus/include $(ROOT)/opus/celt $(ROOT)/opus/silk $(ROOT)/opus/silk/fixed
LOCAL_SRC_FILES		:= src/opus.c \
src/opus_decoder.c \
src/opus_encoder.c \
src/opus_multistream.c \
src/opus_multistream_encoder.c \
src/opus_multistream_decoder.c \
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
silk/fixed/LTP_analysis_filter_FIX.c \
silk/fixed/LTP_scale_ctrl_FIX.c \
silk/fixed/corrMatrix_FIX.c \
silk/fixed/encode_frame_FIX.c \
silk/fixed/find_LPC_FIX.c \
silk/fixed/find_LTP_FIX.c \
silk/fixed/find_pitch_lags_FIX.c \
silk/fixed/find_pred_coefs_FIX.c \
silk/fixed/noise_shape_analysis_FIX.c \
silk/fixed/prefilter_FIX.c \
silk/fixed/process_gains_FIX.c \
silk/fixed/regularize_correlations_FIX.c \
silk/fixed/residual_energy16_FIX.c \
silk/fixed/residual_energy_FIX.c \
silk/fixed/solve_LS_FIX.c \
silk/fixed/warped_autocorrelation_FIX.c \
silk/fixed/apply_sine_window_FIX.c \
silk/fixed/autocorr_FIX.c \
silk/fixed/burg_modified_FIX.c \
silk/fixed/k2a_FIX.c \
silk/fixed/k2a_Q16_FIX.c \
silk/fixed/pitch_analysis_core_FIX.c \
silk/fixed/vector_ops_FIX.c \
silk/fixed/schur64_FIX.c \
silk/fixed/schur_FIX.c \
src/repacketizer.c \
../opus_wrap.c
LOCAL_CFLAGS		:= -DOPUS_BUILD -DVAR_ARRAYS -Wno-traditional -DFIXED_POINT
include $(BUILD_SHARED_LIBRARY)
