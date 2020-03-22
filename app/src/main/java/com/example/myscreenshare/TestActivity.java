package com.example.myscreenshare;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.text.BoringLayout;
import android.util.DisplayMetrics;
import android.widget.Button;

import com.example.myscreenshare.util.LogUtil;

public class TestActivity extends AppCompatActivity {

    public static final String TAG = "TestActivity";

    public static final int REQUEST_CODE = 258;

    private MediaProjectionManager mMediaProjManager;
    private MediaProjection mMediaProjection;
    private DisplayMetrics mMetrics;
    private ImageReader mImageReader;
    private Button mStartBtn;

    private long mLastTime;
    private int mFrameCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        mStartBtn = findViewById(R.id.start_record);
        mMediaProjManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        mFrameCount = 0;
        mImageReader = ImageReader.newInstance(mMetrics.widthPixels, mMetrics.heightPixels, PixelFormat.RGBA_8888, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                mFrameCount ++;
                LogUtil.d(TAG, "FRAMES: " + mFrameCount);
                if (System.currentTimeMillis() - mLastTime > 1000) {
                    LogUtil.d(TAG, "FRAMES: " + mFrameCount);
                    mLastTime = System.currentTimeMillis();
                    mFrameCount = 0;
                }
            }
        }, null);
        Intent intent = mMediaProjManager.createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                mMediaProjection = mMediaProjManager.getMediaProjection(resultCode, data);
                try {
                    mMediaProjection.createVirtualDisplay(
                            "name",
                            mMetrics.widthPixels,
                            mMetrics.heightPixels,
                            mMetrics.densityDpi,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                            mImageReader.getSurface(),
                            null,
                            null
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
                LogUtil.d(TAG, "Start capture");
                mLastTime = System.currentTimeMillis();
            }
        }
    }
}
