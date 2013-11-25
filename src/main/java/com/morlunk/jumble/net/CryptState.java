/*
 * Copyright (C) 2013 Andrew Comminos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.morlunk.jumble.net;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
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

    private static final String AES_TRANSFORMATION = "AES/ECB/NoPadding";
    public static final int AES_BLOCK_SIZE = 16;

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

    Cipher mEncryptKey;
    Cipher mDecryptKey;
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

    /* No need to create a shared secret, no server implementation.
    public void genKey() {
        mInit = true;
    }
     */

    public void setKey(byte[] rkey, byte[] eiv, byte[] div) throws InvalidKeyException {
        mRawKey = rkey;
        mEncryptIV = eiv;
        mDecryptIV = div;
        SecretKey secretKey = new SecretKeySpec(rkey, "AES");
        try {
            mEncryptKey = Cipher.getInstance(AES_TRANSFORMATION);
            mEncryptKey.init(Cipher.ENCRYPT_MODE, secretKey);
            mDecryptKey = Cipher.getInstance(AES_TRANSFORMATION);
            mDecryptKey.init(Cipher.DECRYPT_MODE, secretKey);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException("We use Spongy Castle, this cipher is guaranteed to be here!", e);
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }
        mInit = true;
    }

    public byte[] getEncryptIV() {
        return mEncryptIV;
    }

    public byte[] getDecryptIV() {
        return mDecryptIV;
    }

    public void ocbEncrypt(byte[] plain, byte[] encrypted, int plainLength, byte[] nonce, byte[] tag) throws BadPaddingException, IllegalBlockSizeException, ShortBufferException {
        final byte[] checksum = new byte[AES_BLOCK_SIZE],
                          tmp = new byte[AES_BLOCK_SIZE],
                          pad = new byte[AES_BLOCK_SIZE],
                          delta = mEncryptKey.doFinal(nonce);

        final ByteBuffer plainBuffer = ByteBuffer.wrap(plain);
        final ByteBuffer encryptedBuffer = ByteBuffer.wrap(encrypted);
        final byte[] plainRegion = new byte[AES_BLOCK_SIZE];
        final byte[] encryptedRegion = new byte[AES_BLOCK_SIZE];

        int len = plainLength;
        while(len > AES_BLOCK_SIZE) {
            plainBuffer.get(plainRegion, 0, AES_BLOCK_SIZE);
            encryptedBuffer.get(encryptedRegion, 0, AES_BLOCK_SIZE);

            CryptSupport.S2(delta);
            CryptSupport.XOR(tmp, delta, plainRegion);
            mEncryptKey.doFinal(tmp, 0, AES_BLOCK_SIZE, tmp);
            CryptSupport.XOR(encryptedRegion, delta, tmp);
            CryptSupport.XOR(checksum, checksum, plainRegion);
            len -= AES_BLOCK_SIZE;
        }

        CryptSupport.S2(delta);
        CryptSupport.ZERO(tmp);
        long num = len * 8;
        tmp[AES_BLOCK_SIZE-1] = (byte) ((num >> 8) & 0xFF);
        tmp[AES_BLOCK_SIZE-1] = (byte) (num & 0xFF);
        CryptSupport.XOR(tmp, tmp, delta);
        mEncryptKey.doFinal(tmp, 0, tmp.length, pad);
        System.arraycopy(plain, plainBuffer.position(), tmp, 0, len);
        System.arraycopy(pad, len, tmp, len, AES_BLOCK_SIZE - len);
        CryptSupport.XOR(checksum, checksum, tmp);
        CryptSupport.XOR(tmp, pad, tmp);
        System.arraycopy(tmp, 0, encrypted, encryptedBuffer.position(), len);

        CryptSupport.S3(delta);
        CryptSupport.XOR(tmp, delta, checksum);
        mEncryptKey.doFinal(tmp, 0, AES_BLOCK_SIZE, tag);
    }

    public void ocbDecrypt(byte[] encrypted, byte[] plain, int encryptedLen, byte[] nonce, byte[] tag) throws BadPaddingException, IllegalBlockSizeException, ShortBufferException {
        final byte[] checksum = new byte[AES_BLOCK_SIZE],
                delta = new byte[AES_BLOCK_SIZE],
                tmp = new byte[AES_BLOCK_SIZE],
                pad = new byte[AES_BLOCK_SIZE];

        System.arraycopy(mEncryptKey.doFinal(nonce), 0, delta, 0, AES_BLOCK_SIZE);

        final ByteBuffer plainBuffer = ByteBuffer.wrap(plain);
        final ByteBuffer encryptedBuffer = ByteBuffer.wrap(encrypted);
        final byte[] plainRegion = new byte[AES_BLOCK_SIZE];
        final byte[] encryptedRegion = new byte[AES_BLOCK_SIZE];

        int len = encryptedLen;
        while (len > AES_BLOCK_SIZE) {
            plainBuffer.get(plainRegion);
            encryptedBuffer.get(encryptedRegion);

            CryptSupport.S2(delta);
            CryptSupport.XOR(tmp, delta, encryptedRegion);
            mDecryptKey.doFinal(tmp, 0, AES_BLOCK_SIZE, tmp);
            CryptSupport.XOR(plainRegion, delta, tmp);
            CryptSupport.XOR(checksum, checksum, plainRegion);
            len -= AES_BLOCK_SIZE;
        }

        CryptSupport.S2(delta);
        CryptSupport.ZERO(tmp);
        long num = len * 8;
        tmp[AES_BLOCK_SIZE - 2] = (byte) ((num >> 8) & 0xFF);
        tmp[AES_BLOCK_SIZE - 1] = (byte) (num & 0xFF);
        CryptSupport.XOR(tmp, tmp, delta);
        mEncryptKey.doFinal(tmp, 0, AES_BLOCK_SIZE, pad);
        System.arraycopy(encrypted, encryptedBuffer.position(), tmp, 0, len);
        CryptSupport.XOR(tmp, tmp, pad);
        CryptSupport.XOR(checksum, checksum, tmp);
        System.arraycopy(tmp, 0, plain, plainBuffer.position(), len);

        CryptSupport.S3(delta);
        CryptSupport.XOR(tmp, delta, checksum);
        mEncryptKey.doFinal(tmp, 0, AES_BLOCK_SIZE, tag);

    }

    public synchronized byte[] decrypt(byte[] source, int cryptedLength) {
        if (cryptedLength < 4)
            return null;

        int plainLength = cryptedLength - 4;
        final byte[] dst = new byte[plainLength];
        byte[] saveiv = new byte[AES_BLOCK_SIZE];
        byte ivbyte = source[0];
        boolean restore = false;
        byte[] tag = new byte[AES_BLOCK_SIZE];

        int lost = 0;
        int late = 0;

        System.arraycopy(mDecryptIV, 0, saveiv, 0, AES_BLOCK_SIZE);

        if (((mDecryptIV[0] + 1) & 0xFF) == ivbyte) {
            // In order as expected.
            if (ivbyte > mDecryptIV[0]) {
                mDecryptIV[0] = ivbyte;
            } else if (ivbyte < mDecryptIV[0]) {
                mDecryptIV[0] = ivbyte;
                for (int i = 1; i < AES_BLOCK_SIZE; i++)
                    if (++mDecryptIV[i] != 0)
                        break;
            } else {
                return null;
            }
        } else {
            // This is either out of order or a repeat.

            int diff = ivbyte - mDecryptIV[0];
            if (diff > 128)
                diff = diff - 256;
            else if (diff < -128)
                diff = diff + 256;

            if ((ivbyte < mDecryptIV[0]) && (diff > -30) && (diff < 0)) {
                // Late packet, but no wraparound.
                late = 1;
                lost = -1;
                mDecryptIV[0] = ivbyte;
                restore = true;
            } else if ((ivbyte > mDecryptIV[0]) && (diff > -30) && (diff < 0)) {
                // Last was 0x02, here comes 0xff from last round
                late = 1;
                lost = -1;
                mDecryptIV[0] = ivbyte;
                for (int i = 1; i < AES_BLOCK_SIZE; i++)
                    if (mDecryptIV[i]-- != 0)
                        break;
                restore = true;
            } else if ((ivbyte > mDecryptIV[0]) && (diff > 0)) {
                // Lost a few packets, but beyond that we're good.
                lost = ivbyte - mDecryptIV[0] - 1;
                mDecryptIV[0] = ivbyte;
            } else if ((ivbyte < mDecryptIV[0]) && (diff > 0)) {
                // Lost a few packets, and wrapped around
                lost = 256 - mDecryptIV[0] + ivbyte - 1;
                mDecryptIV[0] = ivbyte;
                for (int i = 1; i < AES_BLOCK_SIZE; i++)
                    if (++mDecryptIV[i] != 0)
                        break;
            } else {
                return null;
            }

            if (mDecryptHistory[mDecryptIV[0]] == mDecryptIV[1]) {
                System.arraycopy(saveiv, 0, mDecryptIV, 0, AES_BLOCK_SIZE);
                return null;
            }
        }

        byte[] tagShiftedDst = new byte[plainLength];
        System.arraycopy(source, 4, tagShiftedDst, 0, plainLength);
        try {
            ocbDecrypt(tagShiftedDst, dst, plainLength, mDecryptIV, tag);
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            // Should never occur. We use a constant, reasonable block size.
            throw new RuntimeException(e);
        } catch (ShortBufferException e) {
            // Should never occur. We use a constant, reasonable block size.
            throw new RuntimeException(e);
        }

        // Validate using first 3 bytes of the tag and bytes 1-3 of the source
        byte[] shiftedSource = new byte[3];
        System.arraycopy(source, 1, shiftedSource, 0, 3);
        byte[] threeTag = new byte[3];
        System.arraycopy(tag, 0, threeTag, 0, 3);

        if (Arrays.equals(shiftedSource, threeTag)) {
            System.arraycopy(saveiv, 0, mDecryptIV, 0, AES_BLOCK_SIZE);
            return null;
        }
        mDecryptHistory[mDecryptIV[0]] = mDecryptIV[1];

        if (restore)
            System.arraycopy(saveiv, 0, mDecryptIV, 0, AES_BLOCK_SIZE);

        mUiGood++;
        mUiLate += late;
        mUiLost += lost;

        mLastGoodStart = System.nanoTime();
        return dst;
    }

    public synchronized byte[] encrypt(byte[] source, int plainLength) {
        final byte[] tag = new byte[AES_BLOCK_SIZE];
        final byte[] dst = new byte[plainLength+4];

        // First, increase our IV.
        for (int i = 0; i < AES_BLOCK_SIZE; i++)
            if ((++mEncryptIV[i]) > 0)
                break;

        try {
            ocbEncrypt(source, dst, plainLength, mEncryptIV, tag);
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            // Should never occur. We use a constant, reasonable block size.
            throw new RuntimeException(e);
        } catch (ShortBufferException e) {
            // Should never occur. We use a constant, reasonable block size.
            throw new RuntimeException(e);
        }

        // First 4 bytes are header data.
        System.arraycopy(dst, 0, dst, 4, plainLength);
        dst[0] = mEncryptIV[0];
        dst[1] = tag[0];
        dst[2] = tag[1];
        dst[3] = tag[2];
        return dst;
    }
}
