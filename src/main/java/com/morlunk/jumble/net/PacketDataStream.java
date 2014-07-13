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

import java.util.Arrays;

/**
 * Based directly off of the Mumble project's PacketDataStream.h file.
 * Created by andrew on 14/07/13.
 */
public class PacketDataStream {
    private byte[] mData;
    private int mMaxSize;
    private int mOffset;
    private int mOvershoot;
    private boolean mOk;

    public PacketDataStream(byte[] data, int len) {
        mOk = true;
        mData = data;
        mMaxSize = len;
    }

    public int size() {
        return mOffset;
    }

    public int capacity() {
        return mMaxSize;
    }

    public boolean isValid() {
        return mOk;
    }

    public int left() {
        return mMaxSize - mOffset;
    }

    public int undersize() {
        return mOvershoot;
    }

    public void append(long v) {
        if(mOffset < mMaxSize) {
            mData[mOffset] = (byte) v;
            mOffset++;
        } else {
            mOk = false;
            mOvershoot++;
        }
    }

    public void append(byte[] d, int len) {
        if(left() >= len) {
            System.arraycopy(d, 0, mData, mOffset, len);
            mOffset += len;
        } else {
            int l = left();
            Arrays.fill(mData, mOffset, mOffset+1, (byte)0);
            mOffset++;
            mOvershoot += len - 1;
            mOk = false;
        }
    }

    public void skip(int len) {
        if(left() >= len)
            mOffset += len;
        else
            mOk = false;
    }

    public int next() {
        if(mOffset < mMaxSize)
            return mData[mOffset++] & 0xFF;
        else {
            mOk = false;
            return 0;
        }
    }

    public byte[] dataBlock(int size) {
        if(left() >= size) {
            byte[] block = new byte[size];
            System.arraycopy(mData, mOffset, block, 0, size);
            mOffset += size;
            return block;
        } else {
            mOk = false;
            return new byte[0];
        }
    }

    public boolean readBool() {
        final boolean b = ((int) readLong() > 0) ? true : false;
        return b;
    }

    public double readDouble() {
        if (left() < 8) {
            mOk = false;
            return 0;
        }

        final long i = next() | next() << 8 | next() << 16 | next() << 24 |
                next() << 32 | next() << 40 | next() << 48 |
                next() << 56;
        return i;
    }

    public float readFloat() {
        if (left() < 4) {
            mOk = false;
            return 0;
        }

        final int i = next() | next() << 8 | next() << 16 | next() << 24;
        return Float.intBitsToFloat(i);
    }

    public long readLong() {
        long i = 0;
        final long v = next();

        if ((v & 0x80) == 0x00) {
            i = v & 0x7F;
        } else if ((v & 0xC0) == 0x80) {
            i = (v & 0x3F) << 8 | next();
        } else if ((v & 0xF0) == 0xF0) {
            final int tmp = (int) (v & 0xFC);
            switch (tmp) {
                case 0xF0:
                    i = next() << 24 | next() << 16 | next() << 8 | next();
                    break;
                case 0xF4:
                    i = next() << 56 | next() << 48 | next() << 40 | next() << 32 |
                            next() << 24 | next() << 16 | next() << 8 | next();
                    break;
                case 0xF8:
                    i = readLong();
                    i = ~i;
                    break;
                case 0xFC:
                    i = v & 0x03;
                    i = ~i;
                    break;
                default:
                    mOk = false;
                    i = 0;
                    break;
            }
        } else if ((v & 0xF0) == 0xE0) {
            i = (v & 0x0F) << 24 | next() << 16 | next() << 8 | next();
        } else if ((v & 0xE0) == 0xC0) {
            i = (v & 0x1F) << 16 | next() << 8 | next();
        }
        return i;
    }

    public void rewind() {
        mOffset = 0;
    }

    public void setBuffer(byte[] d) {
        mData = d;
        mOk = true;
        mOffset = 0;
        mMaxSize = d.length;
    }

    public void writeBool(boolean b) {
        final int v = b ? 1 : 0;
        writeLong(v);
    }

    public void writeDouble(double v) {
        final long i = Double.doubleToLongBits(v);
        append(i & 0xFF);
        append((i >> 8) & 0xFF);
        append((i >> 16) & 0xFF);
        append((i >> 24) & 0xFF);
        append((i >> 32) & 0xFF);
        append((i >> 40) & 0xFF);
        append((i >> 48) & 0xFF);
        append((i >> 56) & 0xFF);
    }

    public void writeFloat(float v) {
        final int i = Float.floatToIntBits(v);

        append(i & 0xFF);
        append((i >> 8) & 0xFF);
        append((i >> 16) & 0xFF);
        append((i >> 24) & 0xFF);
    }

    public void writeLong(long value) {
        long i = value;

        if (((i & 0x8000000000000000L) > 0) && (~i < 0x100000000L)) {
            // Signed number.
            i = ~i;
            if (i <= 0x3) {
                // Shortcase for -1 to -4
                append(0xFC | i);
                return;
            } else {
                append(0xF8);
            }
        }

        if (i < 0x80) {
            // Need top bit clear
            append(i);
        } else if (i < 0x4000) {
            // Need top two bits clear
            append((i >> 8) | 0x80);
            append(i & 0xFF);
        } else if (i < 0x200000) {
            // Need top three bits clear
            append((i >> 16) | 0xC0);
            append((i >> 8) & 0xFF);
            append(i & 0xFF);
        } else if (i < 0x10000000) {
            // Need top four bits clear
            append((i >> 24) | 0xE0);
            append((i >> 16) & 0xFF);
            append((i >> 8) & 0xFF);
            append(i & 0xFF);
        } else if (i < 0x100000000L) {
            // It's a full 32-bit integer.
            append(0xF0);
            append((i >> 24) & 0xFF);
            append((i >> 16) & 0xFF);
            append((i >> 8) & 0xFF);
            append(i & 0xFF);
        } else {
            // It's a 64-bit value.
            append(0xF4);
            append((i >> 56) & 0xFF);
            append((i >> 48) & 0xFF);
            append((i >> 40) & 0xFF);
            append((i >> 32) & 0xFF);
            append((i >> 24) & 0xFF);
            append((i >> 16) & 0xFF);
            append((i >> 8) & 0xFF);
            append(i & 0xFF);
        }
    }
}
