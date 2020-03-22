package com.example.myscreenshare;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;

import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;

import com.example.myscreenshare.util.LogUtil;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import static android.view.View.GONE;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String TAG = MainActivity.class.getSimpleName();

    public static final int REQUEST_CODE = 1314;

    private MediaProjectionManager mMediaProjManager;

    private MediaProjection mMediaProjection;

    private VirtualDisplay mVirtualDisplay;

    private DisplayMetrics mMetrics;

    private ImageReader mImageReader;

    private FloatingActionButton mGoSharingButton;

    private FloatingActionButton mStopSharingButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextureView v = new TextureView(this);
        v.getSurfaceTexture();


        mMediaProjManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        mMetrics = new DisplayMetrics();

        getWindowManager().getDefaultDisplay().getMetrics(mMetrics);


        mGoSharingButton = findViewById(R.id.go_sharing);

        mGoSharingButton.setOnClickListener(this);

        mStopSharingButton = findViewById(R.id.stop_sharing);

        mStopSharingButton.setOnClickListener(this);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                LogUtil.d(TAG, "Request OK!");
                // start screen recording service;
                mMediaProjection = mMediaProjManager.getMediaProjection(resultCode, data);

                ScreenRecordService.setMediaProjection(mMediaProjection);
                Intent intent = new Intent(this, ScreenRecordService.class);
                Bundle recordParams = new Bundle();
                recordParams.putString(ScreenRecordService.RECORD_PARAM_NAME, "params of screen record");
                recordParams.putInt(ScreenRecordService.RECORD_PARAM_WIDTH, mMetrics.widthPixels);
                recordParams.putInt(ScreenRecordService.RECOED_PARAM_HEIGHT, mMetrics.heightPixels);
                recordParams.putInt(ScreenRecordService.RECORD_PARAM_DESITYDPI, mMetrics.densityDpi);
                intent.putExtras(recordParams);
                startService(intent);
            } else {
                Snackbar.make(getWindow().getDecorView(), "获取权限失败", Snackbar.LENGTH_SHORT)
                        .show();
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.go_sharing:
                LogUtil.d(TAG, "start sharing");
                Intent intent = mMediaProjManager.createScreenCaptureIntent();
                startActivityForResult(intent, REQUEST_CODE);
                break;
            case R.id.stop_sharing:
                LogUtil.d(TAG, "Stop sharing");
                mGoSharingButton.show();
                mStopSharingButton.hide();
                stopService(new Intent(this, ScreenRecordService.class));
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_settings:
                LogUtil.d(TAG, "item settings");
                break;
            case R.id.item_about:
                LogUtil.d(TAG, "item about");
                break;
            case R.id.item_exit:
                LogUtil.d(TAG, "item exit");
                break;
        }
        return true;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        LogUtil.d(TAG, "Restart");

    }

    @SuppressLint("RestrictedApi")
    @Override
    protected void onResume() {
        super.onResume();
        LogUtil.d(TAG, "Resume");
        if (ScreenRecordService.isRecording()) {
            mStopSharingButton.show();
            mGoSharingButton.hide();
        } else {
            mStopSharingButton.hide();
            mGoSharingButton.show();
        }
    }

    @SuppressLint("RestrictedApi")
    private void swichOnGoSharingBtn() {
        if (mGoSharingButton.getVisibility() == GONE) {
            mGoSharingButton.setVisibility(GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG, "Destroy!");
    }
}
