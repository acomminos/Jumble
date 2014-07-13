/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.morlunk.jumble.net;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

/**
 * Based off of the official Mumble project's 'CryptState.h' and 'CryptState.cpp' files.
 *
 * This code implements the patented OCB-AES128 cipher mode of operation.
 * Until recently, this would've posed a problem- Jumble is licensed under Apache v2, and the patent was only licensed for use with GPL software without authorization.
 * As of January 2013, the author has given a free license for any open source software certified by the OSI (Apache v2 included)
 * http://www.cs.ucdavis.edu/~rogaway/ocb/license.htm
 *
 * Created by andrew on 24/06/13.
 */
public class CryptState {
    public static final int AES_BLOCK_SIZE = 16;
    private static final String AES_TRANSFORMATION = "AES/ECB/NoPadding";

    byte[] mRawKey = new byte[AES_BLOCK_SIZE];
    byte[] mEncryptIV = new byte[AES_BLOCK_SIZE];
    byte[] mDecryptIV = new byte[AES_BLOCK_SIZE];
    byte[] mDecryptHistory = new byte[0x100];
    int mUiGood = 0;
    int mUiLate = 0;
    int mUiLost = 0;
    int mUiResync = 0;
    int mUiRemoteGood = 0;
    int mUiRemoteLate = 0;
    int mUiRemoteLost = 0;
    int mUiRemoteResync = 0;
    Cipher mEncryptCipher;
    Cipher mDecryptCipher;
    long mLastGoodStart;
    long mLastRequestStart;
    boolean mInit = false;

    public boolean isValid() {
        return mInit;
    }

    /**
     * @return The time since the last good decrypt in microseconds.
     */
    public long getLastGoodElapsed() {
        return (System.nanoTime() - mLastGoodStart) / 1000;
    }

    /**
     * @return The time since the last request in microseconds.
     */
    public long getLastRequestElapsed() {
        return (System.nanoTime() - mLastRequestStart) / 1000;
    }

    /**
     * Resets the recorded time of the last request to the current time.
     */
    public void resetLastRequestTime() {
        mLastRequestStart = System.nanoTime();
    }

    public byte[] getEncryptIV() {
        return mEncryptIV;
    }

    public byte[] getDecryptIV() {
        return mDecryptIV;
    }

    public synchronized void setKeys(final byte[] rkey, final byte[] eiv, final byte[] div) throws InvalidKeyException {
        try {
            mEncryptCipher = Cipher.getInstance(AES_TRANSFORMATION);
            mDecryptCipher = Cipher.getInstance(AES_TRANSFORMATION);
        } catch (final NoSuchAlgorithmException e) {
            e.printStackTrace();
            return;
        } catch (final NoSuchPaddingException e) {
            e.printStackTrace();
            return;
        }

        final SecretKeySpec cryptKey = new SecretKeySpec(rkey, "AES");
        mRawKey = new byte[rkey.length];
        System.arraycopy(rkey, 0, mRawKey, 0, AES_BLOCK_SIZE);
        mEncryptIV = new byte[eiv.length];
        System.arraycopy(eiv, 0, mEncryptIV, 0, AES_BLOCK_SIZE);
        mDecryptIV = new byte[div.length];
        System.arraycopy(div, 0, mDecryptIV, 0, AES_BLOCK_SIZE);

        mEncryptCipher.init(Cipher.ENCRYPT_MODE, cryptKey);
        mDecryptCipher.init(Cipher.DECRYPT_MODE, cryptKey);

        mInit = true;
    }

    /**
     * Decrypts data using the OCB-AES128 standard.
     * @param source The encoded audio data.
     * @param length The length of the source array.
     */
    public synchronized byte[] decrypt(final byte[] source, final int length) throws BadPaddingException, IllegalBlockSizeException, ShortBufferException {
        if (length < 4) return null;

        final int plainLength = length - 4;
        byte[] dst = new byte[plainLength];

        final byte[] saveiv = new byte[AES_BLOCK_SIZE];
        final short ivbyte = (short) (source[0] & 0xFF);
        boolean restore = false;
        final byte[] tag = new byte[AES_BLOCK_SIZE];

        int lost = 0;
        int late = 0;

        System.arraycopy(mDecryptIV, 0, saveiv, 0, AES_BLOCK_SIZE);

        if (((mDecryptIV[0] + 1) & 0xFF) == ivbyte) {
            // In order as expected.
            if (ivbyte > (mDecryptIV[0] & 0xFF)) {
                mDecryptIV[0] = (byte) ivbyte;
            } else if (ivbyte < (mDecryptIV[0] & 0xFF)) {
                mDecryptIV[0] = (byte) ivbyte;
                for (int i = 1; i < AES_BLOCK_SIZE; i++) {
                    if ((++mDecryptIV[i]) != 0) {
                        break;
                    }
                }
            } else {
                return null;
            }
        } else {
            // This is either out of order or a repeat.
            int diff = ivbyte - (mDecryptIV[0] & 0xFF);
            if (diff > 128) {
                diff = diff - 256;
            } else if (diff < -128) {
                diff = diff + 256;
            }

            if ((ivbyte < (mDecryptIV[0] & 0xFF)) && (diff > -30) && (diff < 0)) {
                // Late packet, but no wraparound.
                late = 1;
                lost = -1;
                mDecryptIV[0] = (byte) ivbyte;
                restore = true;
            } else if ((ivbyte > (mDecryptIV[0] & 0xFF)) && (diff > -30) &&
                    (diff < 0)) {
                // Last was 0x02, here comes 0xff from last round
                late = 1;
                lost = -1;
                mDecryptIV[0] = (byte) ivbyte;
                for (int i = 1; i < AES_BLOCK_SIZE; i++) {
                    if ((mDecryptIV[i]--) != 0) {
                        break;
                    }
                }
                restore = true;
            } else if ((ivbyte > (mDecryptIV[0] & 0xFF)) && (diff > 0)) {
                // Lost a few packets, but beyond that we're good.
                lost = ivbyte - mDecryptIV[0] - 1;
                mDecryptIV[0] = (byte) ivbyte;
            } else if ((ivbyte < (mDecryptIV[0] & 0xFF)) && (diff > 0)) {
                // Lost a few packets, and wrapped around
                lost = 256 - (mDecryptIV[0] & 0xFF) + ivbyte - 1;
                mDecryptIV[0] = (byte) ivbyte;
                for (int i = 1; i < AES_BLOCK_SIZE; i++) {
                    if ((++mDecryptIV[i]) != 0) {
                        break;
                    }
                }
            } else {
                return null;
            }

            if (mDecryptHistory[mDecryptIV[0] & 0xFF] == mEncryptIV[0]) {
                System.arraycopy(saveiv, 0, mDecryptIV, 0, AES_BLOCK_SIZE);
                return null;
            }
        }

        final byte[] tagShiftedDst = new byte[plainLength];
        System.arraycopy(source, 4, tagShiftedDst, 0, plainLength);

        ocbDecrypt(tagShiftedDst, dst, mDecryptIV, tag);

        if (tag[0] != source[1] || tag[1] != source[2] || tag[2] != source[3]) {
            System.arraycopy(saveiv, 0, mDecryptIV, 0, AES_BLOCK_SIZE);
            return null;
        }
        mDecryptHistory[mDecryptIV[0] & 0xff] = mDecryptIV[1];

        if (restore)
            System.arraycopy(saveiv, 0, mDecryptIV, 0, AES_BLOCK_SIZE);

        mUiGood++;
        mUiLate += late;
        mUiLost += lost;

        mLastGoodStart = System.nanoTime();
        return dst;
    }

    public void ocbDecrypt(byte[] encrypted, byte[] plain, byte[] nonce, byte[] tag) throws BadPaddingException, IllegalBlockSizeException, ShortBufferException {
        final byte[] checksum = new byte[AES_BLOCK_SIZE];
        final byte[] tmp = new byte[AES_BLOCK_SIZE];

        final byte[] delta = mEncryptCipher.doFinal(nonce);

        int offset = 0;
        int len = encrypted.length;
        while (len > AES_BLOCK_SIZE) {
            final byte[] buffer = new byte[AES_BLOCK_SIZE];
            CryptSupport.S2(delta);
            System.arraycopy(encrypted, offset, buffer, 0, AES_BLOCK_SIZE);

            CryptSupport.XOR(tmp, delta, buffer);
            mDecryptCipher.doFinal(tmp, 0, AES_BLOCK_SIZE, tmp);

            CryptSupport.XOR(buffer, delta, tmp);
            System.arraycopy(buffer, 0, plain, offset, AES_BLOCK_SIZE);

            CryptSupport.XOR(checksum, checksum, buffer);
            len -= AES_BLOCK_SIZE;
            offset += AES_BLOCK_SIZE;
        }

        CryptSupport.S2(delta);
        CryptSupport.ZERO(tmp);

        final long num = len * 8;
        tmp[AES_BLOCK_SIZE - 2] = (byte) ((num >> 8) & 0xFF);
        tmp[AES_BLOCK_SIZE - 1] = (byte) (num & 0xFF);
        CryptSupport.XOR(tmp, tmp, delta);

        final byte[] pad = mEncryptCipher.doFinal(tmp);
        CryptSupport.ZERO(tmp);
        System.arraycopy(encrypted, offset, tmp, 0, len);

        CryptSupport.XOR(tmp, tmp, pad);
        CryptSupport.XOR(checksum, checksum, tmp);

        System.arraycopy(tmp, 0, plain, offset, len);

        CryptSupport.S3(delta);
        CryptSupport.XOR(tmp, delta, checksum);

        mEncryptCipher.doFinal(tmp, 0, AES_BLOCK_SIZE, tag);
    }

    public synchronized byte[] encrypt(final byte[] source, final int length) throws BadPaddingException, IllegalBlockSizeException, ShortBufferException {
        final byte[] tag = new byte[AES_BLOCK_SIZE];

        // First, increase our IV.
        for (int i = 0; i < AES_BLOCK_SIZE; i++) {
            if ((++mEncryptIV[i]) != 0) {
                break;
            }
        }

        final byte[] dst = new byte[length + 4];
        ocbEncrypt(source, dst, length, mEncryptIV, tag);

        System.arraycopy(dst, 0, dst, 4, length);
        dst[0] = mEncryptIV[0];
        dst[1] = tag[0];
        dst[2] = tag[1];
        dst[3] = tag[2];

        return dst;
    }

    public void ocbEncrypt(byte[] plain, byte[] encrypted, int plainLength, byte[] nonce, byte[] tag) throws BadPaddingException, IllegalBlockSizeException, ShortBufferException {
        final byte[] checksum = new byte[AES_BLOCK_SIZE];
        final byte[] tmp = new byte[AES_BLOCK_SIZE];

        final byte[] delta = mEncryptCipher.doFinal(nonce);

        int offset = 0;
        int len = plainLength;
        while (len > AES_BLOCK_SIZE) {
            final byte[] buffer = new byte[AES_BLOCK_SIZE];
            CryptSupport.S2(delta);
            System.arraycopy(plain, offset, buffer, 0, AES_BLOCK_SIZE);
            CryptSupport.XOR(checksum, checksum, buffer);
            CryptSupport.XOR(tmp, delta, buffer);

            mEncryptCipher.doFinal(tmp, 0, AES_BLOCK_SIZE, tmp);

            CryptSupport.XOR(buffer, delta, tmp);
            System.arraycopy(buffer, 0, encrypted, offset, AES_BLOCK_SIZE);
            len -= AES_BLOCK_SIZE;
            offset += AES_BLOCK_SIZE;
        }

        CryptSupport.S2(delta);
        CryptSupport.ZERO(tmp);
        final long num = len * 8;
        tmp[AES_BLOCK_SIZE - 2] = (byte) ((num >> 8) & 0xFF);
        tmp[AES_BLOCK_SIZE - 1] = (byte) (num & 0xFF);
        CryptSupport.XOR(tmp, tmp, delta);

        final byte[] pad = mEncryptCipher.doFinal(tmp);

        System.arraycopy(plain, offset, tmp, 0, len);
        System.arraycopy(pad, len, tmp, len, AES_BLOCK_SIZE - len);
        CryptSupport.XOR(checksum, checksum, tmp);
        CryptSupport.XOR(tmp, pad, tmp);

        System.arraycopy(tmp, 0, encrypted, offset, len);
        CryptSupport.S3(delta);
        CryptSupport.XOR(tmp, delta, checksum);
        mEncryptCipher.doFinal(tmp, 0, AES_BLOCK_SIZE, tag);
    }

    /**
     * Some functions that provide helpful cryptographic support, like being able to XOR a byte array.
     */
    private static class CryptSupport {

        private static final int SHIFTBITS = 7;

        public static void XOR(final byte[] dst, final byte[] a, final byte[] b) {
            for (int i = 0; i < AES_BLOCK_SIZE; i++) {
                dst[i] = (byte) (a[i] ^ b[i]);
            }
        }

        public static void S2(final byte[] block) {
            int carry = (block[0] >> SHIFTBITS) & 0x1;
            for (int i = 0; i < AES_BLOCK_SIZE - 1; i++) {
                block[i] = (byte) ((block[i] << 1) | ((block[i + 1] >> SHIFTBITS) & 0x1));
            }
            block[AES_BLOCK_SIZE - 1] = (byte) ((block[AES_BLOCK_SIZE - 1] << 1) ^ (carry * 0x87));
        }

        public static void S3(final byte[] block) {
            final int carry = (block[0] >> SHIFTBITS) & 0x1;
            for (int i = 0; i < AES_BLOCK_SIZE - 1; i++) {
                block[i] ^= (block[i] << 1) | ((block[i + 1] >> SHIFTBITS) & 0x1);
            }
            block[AES_BLOCK_SIZE - 1] ^= ((block[AES_BLOCK_SIZE - 1] << 1) ^ (carry * 0x87));
        }

        public static void ZERO(final byte[] block) {
            Arrays.fill(block, (byte) 0);
        }
    }
}
