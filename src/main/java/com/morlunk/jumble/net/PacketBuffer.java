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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * A {@link java.nio.ByteBuffer} based class for constructing Mumble protocol messages.
 */
public class PacketBuffer {
    private ByteBuffer mBuffer;

    public static PacketBuffer allocate(int len) {
        return new PacketBuffer(ByteBuffer.allocate(len));
    }

    public static PacketBuffer allocateDirect(int len) {
        return new PacketBuffer(ByteBuffer.allocateDirect(len));
    }

    public PacketBuffer(ByteBuffer buffer) {
        mBuffer = buffer;
    }

    public PacketBuffer(byte[] data, int len) {
        mBuffer = ByteBuffer.wrap(data);
        mBuffer.limit(len);
    }

    /**
     * Returns the current size of the packet.
     * @return The number of bytes written to the packet buffer.
     */
    public int size() {
        return mBuffer.position();
    }

    /**
     * Returns the maximum size this packet can contain.
     * @return The capacity of this packet buffer.
     */
    public int capacity() {
        return mBuffer.limit();
    }

    public int left() {
        return mBuffer.limit() - mBuffer.position();
    }

    public void append(long v) {
        mBuffer.put((byte) v);
    }

    public void append(byte[] d, int len) {
        mBuffer.put(d, 0, len);
    }

    public void skip(int len) {
        mBuffer.position(mBuffer.position() + len);
    }

    public int next() {
        return mBuffer.get() & 0xFF;
    }

    /**
     * Slices the underlying byte buffer at the current position and limits it to size.
     * @param size The limit of the buffer to obtain.
     * @return The sliced byte buffer.
     */
    public ByteBuffer bufferBlock(int size) {
        if (size > mBuffer.remaining()) {
            throw new BufferUnderflowException();
        }
        ByteBuffer buffer = mBuffer.slice();
        buffer.limit(size);
        skip(size);
        return buffer;
    }

    /**
     * Retrieves a block of data at the current position from the underlying byte buffer.
     * @param size The size of the data block to allocate.
     * @return The allocated data block.
     */
    public byte[] dataBlock(int size) {
        byte[] block = new byte[size];
        mBuffer.get(block, 0, size);
        return block;
    }

    public boolean readBool() {
        return ((int) readLong() > 0);
    }

    public double readDouble() {
        if (left() < 8) {
            throw new BufferUnderflowException();
        }

        final long i = next() | next() << 8 | next() << 16 | next() << 24 |
                next() << 32 | next() << 40 | next() << 48 |
                next() << 56;
        return i;
    }

    public float readFloat() {
        if (left() < 4) {
            throw new BufferUnderflowException();
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
                    throw new BufferUnderflowException();
            }
        } else if ((v & 0xF0) == 0xE0) {
            i = (v & 0x0F) << 24 | next() << 16 | next() << 8 | next();
        } else if ((v & 0xE0) == 0xC0) {
            i = (v & 0x1F) << 16 | next() << 8 | next();
        }
        return i;
    }

    public void rewind() {
        mBuffer.rewind();
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
