package com.example.myscreenshare.codec;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

public class CircularEncoderBuffer {
    private static final String TAG = "CircularEncoderBuffer";

    private ByteBuffer mDataBufferWrapper;
    private byte[] mDataBuffer;

    //mete-data
    private int[] mPacketFlags;
    private long[] mPacketPtsUsec; // presentation time in milliseconds
    private int[] mPacketStart;
    private int[] mPacketLength;

    // head and tail of queue, head points to an empty node
    // queue is empty, when first==tail
    private int mMetaHead;
    private int mMetaTail;

    // param desire, how many seconds of data we want to save at buffer
    public CircularEncoderBuffer(int bitRate, int frameRate, int desireSpanSec) {
        int dataBufferSize = bitRate * desireSpanSec / 8;
        mDataBuffer = new byte[dataBufferSize];
        mDataBufferWrapper = ByteBuffer.wrap(mDataBuffer);

        int metaBufferCount = frameRate * desireSpanSec * 2;
        mPacketFlags = new int[metaBufferCount];
        mPacketPtsUsec = new long[metaBufferCount];
        mPacketStart = new int[metaBufferCount];
        mPacketLength = new int[metaBufferCount];

        CodecLog.d(TAG, "CEB:\n\t" +
                "bitRate: " + bitRate +
                "frameRate: " + frameRate +
                "desireSpanSec: " + desireSpanSec +
                "bufferSize: " + dataBufferSize +
                "metaBufferCount: " + metaBufferCount);

    }

    public long computeTimeSpanMillisecond() {
        final int metaLen = mPacketStart.length;
        if (mMetaHead == mMetaTail) {
            return 0;
        }

        int beforeHead = (mMetaHead + metaLen - 1) % metaLen;
        return mPacketPtsUsec[beforeHead] - mPacketPtsUsec[mMetaTail];
    }

    public void add(ByteBuffer buf, int flags, long ptsUsec) {
        int size = buf.limit() - buf.position();
        CodecLog.d(TAG, "add size = " + size +
                "flags=0x" + Integer.toHexString(flags) +
                " pts=" + ptsUsec);
        while (!canAdd(size)) {
            removeTail();
        }

        final int dataLen = mDataBuffer.length;
        final int metaLen = mPacketStart.length;
        int packetStart = getHeadStart();
        mPacketFlags[mMetaHead] = flags;
        mPacketPtsUsec[mMetaHead] = ptsUsec;
        mPacketStart[mMetaHead] = packetStart;
        mPacketLength[mMetaHead] = size;

        if (packetStart + size < dataLen) {
            buf.get(mDataBuffer, packetStart, size);
        } else {
            int firstSize = dataLen - packetStart;
            buf.get(mDataBuffer, packetStart, firstSize);
            buf.get(mDataBuffer, 0, size - firstSize);
        }

        mMetaHead = (mMetaHead + 1) % metaLen;
    }

    // return the index of the oldest i-frame.
    public  int getFirstIndex() {
        final int metaLen = mPacketStart.length;
        int index = mMetaTail;
        while (index != mMetaHead) {
            if ((mPacketFlags[index] & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                break;
            }
            index = (index + 1) % metaLen;
        }

        if (index == mMetaHead) {
            CodecLog.w(TAG, "There is no I-Frame in buffer");
            index = -1;
        }
        return index;
    }

    public int getNextIndex(int index) {
        final int metaLen = mPacketStart.length;
        int next = (index + 1) % metaLen;
        if (next == mMetaHead) {
            next = -1;
        }
        return next;
    }

    public ByteBuffer getChunk(int index, MediaCodec.BufferInfo info) {
        final int dataLen = mDataBuffer.length;
        int packetStart = mPacketStart[index];
        int length = mPacketLength[index];
        info.flags = mPacketFlags[index];
        info.offset = packetStart;
        info.presentationTimeUs = mPacketPtsUsec[index];
        info.size = length;

        if (packetStart + length <= dataLen) {
            return mDataBufferWrapper;
        } else {
            ByteBuffer tempBuf = ByteBuffer.allocateDirect(length);
            int firstSize = dataLen - packetStart;
            tempBuf.put(mDataBuffer, mPacketStart[index], firstSize);
            tempBuf.put(mDataBuffer, 0, length - firstSize);
            info.offset = 0;
            return tempBuf;
        }
    }

    private int getHeadStart() {
        if (mMetaHead == mMetaTail) {
            return 0;
        }

        final int dataLen = mDataBuffer.length;
        final int metaLen = mPacketStart.length;
        int beforeHead = (mMetaHead + metaLen - 1) % metaLen;
        return (mPacketStart[beforeHead] + mPacketLength[beforeHead] + 1) % dataLen;
    }

    private boolean canAdd(int size) {
        final int dataLen = mDataBuffer.length;
        final int metaLen = mPacketStart.length;
        if (size > dataLen) {
            throw new RuntimeException(
                    "Enormous packet: " + size +
                            " vs. buffer: " + dataLen);
        }
        if (mMetaHead == mMetaTail) {
            return true;
        }

        int nextHead = (mMetaHead + 1) % metaLen;
        if (nextHead == mMetaTail) {
            CodecLog.v(TAG, "Ran out of metadata (head=" + mMetaHead +
                    " tail=" + mMetaTail);
            return false;
        }

        int headStart = getHeadStart();
        int tailStart = mPacketStart[mMetaTail];
        int freeSpace = (tailStart + dataLen - headStart) % dataLen;
        if (size > freeSpace) {
            CodecLog.v(TAG, "Ran out of data (tailStart=" + tailStart +
                    " headStart=" + headStart + " req=" + size + " free=" + freeSpace + ")");
            return false;
        }
       CodecLog.v(TAG, "OK: size=" + size + " free=" + freeSpace +
               " metaFree=" + ((mMetaTail + metaLen - mMetaHead) % metaLen - 1));

        return true;
    }

    private void removeTail() {
        if (mMetaHead == mMetaTail) {
            throw new RuntimeException("Can't removeTail() in empty buffer");
        }
        final int metaLen = mPacketStart.length;
        mMetaTail = (mMetaTail + 1) % metaLen;
    }
}
