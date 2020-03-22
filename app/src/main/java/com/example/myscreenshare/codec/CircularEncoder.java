package com.example.myscreenshare.codec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

public class CircularEncoder {

    public static final String TAG = "CircularEncoder";
    public static final String MIME_TYPE = "video/hvc";
    public static final int I_FRAME_INTERVAL = 1;


    public MediaCodec mEncoder;
    public Surface mInputSurface;
    public EncoderThread mEncoderThread;

    public interface CircularEncoderCallback {

        void fileSaveComplete(int status);
        void bufferStatus(long totalTimeMilli);
    }

    public CircularEncoder (int width, int height, int bitRate,
                            int frameRate, int desiredSpanSec,
                            CircularEncoderCallback cb)
            throws IOException {
        if (desiredSpanSec < I_FRAME_INTERVAL * 2) {
            throw new RuntimeException(
                    "Request time span is too short:" +
                    desiredSpanSec + " vs. " + (I_FRAME_INTERVAL *2));
        }

        CircularEncoderBuffer encBuffer = new CircularEncoderBuffer(
                bitRate, frameRate, desiredSpanSec
        );

        MediaFormat format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                width,
                height
        );

        format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        );

        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);

        format.setInteger(
                MediaFormat.KEY_I_FRAME_INTERVAL,
                I_FRAME_INTERVAL
        );

        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);

        mEncoder.configure(
                format,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
        );

        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();

        mEncoderThread.start();
        mEncoderThread.waitUntilReady();
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    public void shutdown() {
        CodecLog.d(TAG, "Release encoder objects");
        Handler handler = mEncoderThread.getHandler();
        handler.sendMessage(handler.obtainMessage(EncoderThread.EncoderHandler.MSG_SHUTDOWN));
        try {
            mEncoderThread.join();
        } catch (InterruptedException ie) {
            CodecLog.d(TAG, "Encoder thread join() was interrupted" + ie);
        }

        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
    }

    public void frameAvailableSoon() {
        Handler handler = mEncoderThread.getHandler();
        handler.sendMessage(handler.obtainMessage(
                EncoderThread.EncoderHandler.MSG_FRAME_AVAILABLE_SOON
        ));
    }

    public void saveVideo(File outputFile) {
        Handler handler = mEncoderThread.getHandler();
        handler.sendMessage(handler.obtainMessage(
                EncoderThread.EncoderHandler.MSG_SAVE_VIDEO, outputFile
        ));
    }

    private static class EncoderThread extends Thread {
        private MediaCodec mEncoder;
        private MediaFormat mFormat;
        private MediaCodec.BufferInfo mBufInfo;

        private EncoderHandler mHandler;
        private CircularEncoderBuffer mEncBuffer;
        private CircularEncoder.CircularEncoderCallback mCallback;
        private int mFrameCount;

        private final Object mLock = new Object();
        private volatile boolean mReady = false;

        public EncoderThread(MediaCodec mediaCodec,
                             CircularEncoderBuffer encBuffer,
                             CircularEncoder.CircularEncoderCallback callback) {
            mEncoder = mediaCodec;
            mEncBuffer = encBuffer;
            mCallback = callback;
            mBufInfo = new MediaCodec.BufferInfo();
        }

        @Override
        public void run() {
            Looper.prepare();
            mHandler = new EncoderHandler(this);
            synchronized (mLock) {
                mReady = true;
                mLock.notify();
            }
            Looper.loop();
            synchronized (mLock) {
                mReady = false;
                mHandler = null;
            }
        }

        public void waitUntilReady() {
            synchronized (mLock) {
                while (!mReady) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException ie) {

                    }
                }
            }
        }



        public EncoderHandler getHandler() {
            synchronized (mLock) {
                if (!mReady) {
                    throw new RuntimeException("not ready");
                }
            }
            return mHandler;
        }

        public void drainEncoder() {
            final int TIMEOUT_USEC = 0;
            for (;;) {
                int outputBufferId
                        = mEncoder.dequeueOutputBuffer(mBufInfo, TIMEOUT_USEC);
                if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break;
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mFormat = mEncoder.getOutputFormat();
                } else if (outputBufferId < 0) {
                    CodecLog.w(
                            TAG,
                            "Unexpected result from MediaCodec.dequeueOutputBuffer: " +
                            outputBufferId);
                } else {
                    ByteBuffer encodedData = mEncoder.getOutputBuffer(outputBufferId);
                    if (encodedData == null) {
                        throw new RuntimeException(
                                "OutputBuffer at " +
                                        outputBufferId +
                                        "was null!");
                    }

                    if ((mBufInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        CodecLog.d(TAG, "Ignore BUFFER_FLAG_CODEC_CONFIG");
                        mBufInfo.size = 0;
                    }

                    if (mBufInfo.size != 0) {
                        encodedData.position(mBufInfo.offset);
                        encodedData.limit(mBufInfo.offset + mBufInfo.size);
                        mEncBuffer.add(
                                encodedData,
                                mBufInfo.flags,
                                mBufInfo.presentationTimeUs
                        );
                        CodecLog.d(
                                TAG,
                                "Sent " + mBufInfo.size +
                                        "bytes to muxer, ts=" +
                                        mBufInfo.presentationTimeUs
                                );
                    }
                    mEncoder.releaseOutputBuffer(outputBufferId, false);
                    if ((mBufInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        CodecLog.w(TAG, "Reach end of stream unexpectedly");
                        break;
                    }
                }
            }
        }

        void frameAvailableSoon() {
            CodecLog.d(TAG, "frameAvailableSoon");
            drainEncoder();
            mFrameCount++;
            if ((mFrameCount % 10) == 0) {
                mCallback.bufferStatus(mEncBuffer.computeTimeSpanMillisecond());
            }
        }

        void saveVideo(File outputFile) {
            // TODO: save video to outputFile
            CodecLog.d(TAG, "Save video fire");
        }

        void shutdown() {
            CodecLog.d(TAG, "shutdown");
            Looper.myLooper().quit();
        }

        private static class EncoderHandler extends Handler {
            public static final int MSG_FRAME_AVAILABLE_SOON = 1;
            public static final int MSG_SAVE_VIDEO = 2;
            public static final int MSG_SHUTDOWN = 3;

            private WeakReference<EncoderThread> mWeakEncoderThread;

            public EncoderHandler (EncoderThread encoderThread) {
                mWeakEncoderThread = new WeakReference<EncoderThread>(encoderThread);
            }

            @Override
            public void handleMessage(Message msg) {
                int what = msg.what;
                CodecLog.v(TAG, "EncoderHandler: what=" + what);
                EncoderThread encoderThread = mWeakEncoderThread.get();
                if (encoderThread == null) {
                    return;
                }
                switch (what) {
                    case MSG_FRAME_AVAILABLE_SOON:
                        encoderThread.frameAvailableSoon();
                        break;
                    case MSG_SAVE_VIDEO:
                        encoderThread.saveVideo((File)msg.obj);
                        break;
                    case MSG_SHUTDOWN:
                        encoderThread.shutdown();
                        break;
                    default:
                        throw new RuntimeException("Unknown message " + what);
                }
            }
        }
    }
}
