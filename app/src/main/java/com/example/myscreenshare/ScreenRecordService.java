package com.example.myscreenshare;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.print.PrinterId;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;

import com.example.myscreenshare.gles.EGLCore;
import com.example.myscreenshare.util.LogUtil;

import java.io.IOException;

public class ScreenRecordService extends Service {
 // 1080 x 1794
    public static final String TAG = ScreenRecordService.class.getSimpleName();

    // params for Screen Recording;
    public static final String RECORD_PARAM_NAME = "param_name";
    public static final String RECORD_PARAM_WIDTH = "param_width";
    public static final String RECOED_PARAM_HEIGHT = "param_height";
    public static final String RECORD_PARAM_DESITYDPI = "param_density";

    // params for Encoder
    public static final String MIME_TYPE = "video/avc";
    public static final long SECS_IN_NANO = 1000000000; // seconds in nanoseconds
    public static final long SECS_IN_MILLI = 1000; // seconds in milliseconds
    public static final int FPS = 30;
    public static final int I_FRAME_INTERVAL = 6;
    private int mWidth = -1;
    private int mHeight = -1;
    private int mBitRate = 2000000000;
    private MediaCodec mEncoder;
    private MediaCodec.BufferInfo mBufferInfo;


    private static MediaProjection mMediaProjection = null;
    private VirtualDisplay mVirtualDisplay;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private int mTextureName;


    private long mLastTime;
    private long mFrameCount;

    private volatile boolean isLoopExit = true;

    // test VirtualDisplay
    ImageReader mImageReader;

    public ScreenRecordService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();

    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
        } else {
            Bundle bundle = intent.getExtras();
            if (bundle.containsKey(RECORD_PARAM_NAME)) {
                int width = bundle.getInt(RECORD_PARAM_WIDTH);
                int height = bundle.getInt(RECOED_PARAM_HEIGHT);
                int densityDpi = bundle.getInt(RECORD_PARAM_DESITYDPI);
                EGLCore eglCore = new EGLCore(null, 0);
                EGLSurface eglSurface = eglCore.createOffscreenSurface(width, height);
                eglCore.makeCurrent(eglSurface);
                mTextureName = createTextureObject();
                mSurfaceTexture = new SurfaceTexture(mTextureName);
                mSurfaceTexture.setDefaultBufferSize(width, height);
                LogUtil.d(TAG, "Texture ID: " + mTextureName);
                mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                    @Override
                    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                        mFrameCount ++;
//                        LogUtil.d(TAG, "FRAME COUNT : " + mFrameCount);
                        surfaceTexture.updateTexImage();
                        // notify gles to fill encoder
                    }
                });
                mSurface = new Surface(mSurfaceTexture);
                mSurfaceTexture.updateTexImage();
                startScreenRecord(width, height, densityDpi);
            }


        }
        return START_STICKY;
    }

    private int createTextureObject() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
//        GlUtil.checkGlError("glGenTextures");

        int texId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
//        GlUtil.checkGlError("glBindTexture " + texId);

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
//        GlUtil.checkGlError("glTexParameter");

        return texId;
    }

    private void startScreenRecord(int width, int height, int densityDpi) {
        LogUtil.d(TAG, "width: " + width + " height: " + height + " density dpi: " + densityDpi);
        setUpMediaProjection(width, height, densityDpi);
    }

    private void setUpMediaProjection(int width, int height, int densityDpi) {
        if (mMediaProjection == null) {
            LogUtil.e(TAG, "MediaProjection is null");
            return;
        }

        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "screen",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSurface,
                null,
                null
        );

        LogUtil.d(TAG, "set up projection");
        mLastTime = System.currentTimeMillis();
        mFrameCount = 0;
    }

    private void prepareEncoder () {
        mBufferInfo = new MediaCodec.BufferInfo();
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mEncoder.start();
    }

    private static class CodecInputSurface {
        private EGLSurface mEGLSurface;
        private Surface mSurface;
        private EGLCore mEGLCore;
        public CodecInputSurface(Surface surface) {
            if (surface == null) {
                throw new NullPointerException();
            }
            mSurface = surface;
            mEGLCore = new EGLCore(null, EGLCore.FLAG_RECORDABLE);
            mEGLSurface = mEGLCore.createWindowSurface(mSurface);
        }

        public void release() {
            mEGLCore.release();
            mSurface.release();
            mEGLSurface = EGL14.EGL_NO_SURFACE;
            mSurface = null;
        }

        public void makeCurrent() {
            mEGLCore.makeCurrent(mEGLSurface);
        }

        public boolean swapBuffers() {
            return true;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG, "Service destroy");
        LogUtil.d(TAG, "When destroy frame count: " + mFrameCount);
        mSurface.release();
        mSurface = null;
        mSurfaceTexture.releaseTexImage();
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }


    public static boolean isRecording() {
        return (mMediaProjection != null);
    }

    public static void setMediaProjection(MediaProjection mediaProjection) {
        mMediaProjection = mediaProjection;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
