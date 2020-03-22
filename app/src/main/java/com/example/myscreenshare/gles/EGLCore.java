package com.example.myscreenshare.gles;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.view.Surface;

import com.example.myscreenshare.util.LogUtil;

public class EGLCore {
    public static final String TAG = "EGLCore";

    public static final int FLAG_RECORDABLE = 0x01;

    public static final int FLAG_TRY_GLES3 = 0x02;

    public static final int EGL_RECORDABLE_ANDROID = 0x3142;

    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLConfig mEGLConfig = null;
    private int mGlesVersion = -1;

    public EGLCore() {
        this(null, 0);
    }

    public EGLCore(EGLContext sharedContext, int flags) {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("EGL already set up!");
        }

        if (sharedContext == null) {
            sharedContext = EGL14.EGL_NO_CONTEXT;
        }

        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("Unable to get EGL14 display!");
        }

        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            throw new RuntimeException("Unable to init EGL14 !");
        }

        if ((flags & FLAG_TRY_GLES3) != 0) {
            EGLConfig config = getConfig(flags, 3);
            if (config != null) {
                int[] attrib3List = {
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                        EGL14.EGL_NONE
                };
                EGLContext context = EGL14.eglCreateContext(
                        mEGLDisplay,
                        config,
                        sharedContext,
                        attrib3List,
                        0);
                if (EGL14.eglGetError() == EGL14.EGL_SUCCESS) {
                    mEGLContext = context;
                    mEGLConfig = config;
                }
            }
        }

        if (mEGLContext == EGL14.EGL_NO_CONTEXT) {
            EGLConfig config = getConfig(flags, 2);
            if (config == null) {
                throw new RuntimeException("Unable to find suitable EGL Config!");
            }
            int[] attrib2List = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
            };
            EGLContext context = EGL14.eglCreateContext(
                    mEGLDisplay,
                    config,
                    sharedContext,
                    attrib2List,
                    0);
            checkEglError("eglCreateContext");
            mEGLContext = context;
            mEGLConfig = config;
            mGlesVersion = 2;
        }

        int[] values = new int[1];
        EGL14.eglQueryContext(
                mEGLDisplay,
                mEGLContext,
                EGL14.EGL_CONTEXT_CLIENT_VERSION,
                values,
                0);
        LogUtil.d(TAG, "EGLContext created, client version: " + values[0]);
    }

    private EGLConfig getConfig(int flags, int version) {
        int renderableType = EGL14.EGL_OPENGL_ES2_BIT;
        if (version >= 3) {
            renderableType |= EGLExt.EGL_OPENGL_ES3_BIT_KHR;
        }

        int[] attribList = {
                EGL14.EGL_RED_SIZE, 5,
                EGL14.EGL_GREEN_SIZE, 6,
                EGL14.EGL_BLUE_SIZE, 5,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, renderableType,
                EGL14.EGL_NONE, 0, // placeholder for recordable flag
                EGL14.EGL_NONE
        };

        if ((flags & FLAG_RECORDABLE) != 0) {
            attribList[attribList.length - 3] = EGL_RECORDABLE_ANDROID;
            attribList[attribList.length - 2] = 1;
        }

        EGLConfig[] configs = new EGLConfig[1];
        int[] configCount = new int[1];
        if (!EGL14.eglChooseConfig(
                mEGLDisplay,
                attribList,
                0,
                configs,
                0,
                configs.length,
                configCount,
                0
        )) {
            LogUtil.w(TAG, "Unable to find RGB8888 /" + version + " EGLConfig");
            return null;
        }
        return configs[0];
    }

    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + " : EGL error: 0x" + Integer.toHexString(error));
        }
    }

    public void release() {
        if (mEGLContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglMakeCurrent(mEGLDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(mEGLDisplay);
        }
        mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        mEGLContext = EGL14.EGL_NO_CONTEXT;
        mEGLConfig = null;
    }

    public EGLSurface createWindowSurface(Object surface) {
        if (!(surface instanceof Surface) &&
                !(surface instanceof SurfaceTexture)) {
            throw new RuntimeException("Invalid surface: " + surface);
        }

        int[] surfaceAttribs = {
                EGL14.EGL_NONE
        };
        EGLSurface eglSurface = EGL14.eglCreateWindowSurface(
                mEGLDisplay,
                mEGLConfig,
                surface,
                surfaceAttribs,
                0
        );
        checkEglError("eglCreateWindowSurface");
        if (eglSurface == null) {
            throw new RuntimeException("Created surface was null !");
        }
        return eglSurface;
    }

    public EGLSurface createOffscreenSurface(int width, int height) {
        int[] surfaceAttrib = {
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
        };
        EGLSurface eglSurface = EGL14.eglCreatePbufferSurface(
                mEGLDisplay,
                mEGLConfig,
                surfaceAttrib,
                0);
        checkEglError("eglCreatePBufferSurface");
        if (eglSurface == null) {
            throw new RuntimeException("Offscreen surface was null");
        }
        return eglSurface;
    }

    public void makeCurrent(EGLSurface surface) {
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            LogUtil.d(TAG, "CAUTION: MakeCurrent without Display !");
        }
        if (!EGL14.eglMakeCurrent(mEGLDisplay, surface, surface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed!");
        }
    }

    public void makeCurrent(EGLSurface drawSurface, EGLSurface readSurface) {
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            LogUtil.d(TAG, "CAUTION: MakeCurrent without Display !");
        }

        if (!EGL14.eglMakeCurrent(mEGLDisplay, drawSurface, readSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent(draw, read) failed !");
        }
    }

    public boolean swapBuffers(EGLSurface eglSurface) {
        return EGL14.eglSwapBuffers(mEGLDisplay, eglSurface);
    }

    public void setPresentationTime(EGLSurface surface, long nsecs) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, surface, nsecs);
        checkEglError("eglPresentationTimeANDROID");
    }
}
