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

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Timer;

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

    private static final int AES_BLOCK_SIZE = 16;

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
    Timer mLastGood;
    Timer mLastRequest;
    boolean mInit = false;

    public boolean isValid() {
        return mInit;
    }

    /* No need to create a shared secret, no server implementation.
    public void genKey() {
        mInit = true;
    }
     */

    public void setKey(byte[] rkey, byte[] eiv, byte[] div) throws InvalidKeyException{
        mRawKey = rkey;
        mEncryptIV = eiv;
        mDecryptIV = div;
        SecretKey secretKey = new SecretKeySpec(rkey, "AES");
        try {
            mEncryptKey = Cipher.getInstance("AES/ECB/NoPadding");
            mEncryptKey.init(Cipher.ENCRYPT_MODE, secretKey);
            mDecryptKey = Cipher.getInstance("AES/ECB/NoPadding");
            mDecryptKey.init(Cipher.DECRYPT_MODE, secretKey);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException("We use Spongy Castle, this cipher is guaranteed to be here!", e);
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }
        mInit = true;
    }

    public void setDecryptIV(byte[] iv) {
        mDecryptIV = iv;
    }

    public void ocbEncrypt(byte[] plain, byte[] encrypted, int len, byte[] nonce, byte[] tag) {

    }

    public void ocbDecrypt(byte[] encrypted, byte[] plain, int len, byte[] nonce, byte[] tag) {

    }

    public boolean decrypt(byte[] source, byte[] dst, int cryptedLength) {
        if(cryptedLength < 4)
            return false;

        int plainLength = cryptedLength - 4;
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
                for (int i=1;i<AES_BLOCK_SIZE;i++)
                    if (++mDecryptIV[i] != 0)
                        break;
            } else {
                return false;
            }
        } else {
            // This is either out of order or a repeat.

            int diff = ivbyte - mDecryptIV[0];
            if (diff > 128)
                diff = diff-256;
            else if (diff < -128)
                diff = diff+256;

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
                for (int i=1;i<AES_BLOCK_SIZE;i++)
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
                for (int i=1;i<AES_BLOCK_SIZE;i++)
                    if (++mDecryptIV[i] != 0)
                        break;
            } else {
                return false;
            }

            if (mDecryptHistory[mDecryptIV[0]] == mDecryptIV[1]) {
                System.arraycopy(saveiv, 0, mDecryptIV, 0, AES_BLOCK_SIZE);
                return false;
            }
        }

        byte[] tagShiftedDst = new byte[plainLength];
        System.arraycopy(source, 4, tagShiftedDst, 0, plainLength);
        ocbDecrypt(tagShiftedDst, dst, plainLength, mDecryptIV, tag);

        // Validate using first 3 bytes of the tag and bytes 1-3 of the source
        byte[] shiftedSource = new byte[3];
        System.arraycopy(source, 1, shiftedSource, 0, 3);
        byte[] threeTag = new byte[3];
        System.arraycopy(tag, 0, threeTag, 0, 3);

        if (Arrays.equals(shiftedSource, threeTag)) {
            System.arraycopy(saveiv, 0, mDecryptIV, 0, AES_BLOCK_SIZE);
            return false;
        }
        mDecryptHistory[mDecryptIV[0]] = mDecryptIV[1];

        if (restore)
            System.arraycopy(saveiv, 0, mDecryptIV, 0, AES_BLOCK_SIZE);

        mUiGood++;
        mUiLate += late;
        mUiLost += lost;

        //mLastGood.restart(); FIXME
        return true;
    }

    public void encrypt(byte[] source, byte[] dst, int plainLength) {
        final byte[] tag = new byte[AES_BLOCK_SIZE];

        // First, increase our IV.
        for(int i=0;i<AES_BLOCK_SIZE;i++)
            if(++mEncryptIV[i] != 0)
                break;

        ocbEncrypt(source, dst, plainLength, mEncryptIV, tag);

        dst[0] = mEncryptIV[0];
        dst[1] = tag[0];
        dst[2] = tag[1];
        dst[3] = tag[2];
    }
}
