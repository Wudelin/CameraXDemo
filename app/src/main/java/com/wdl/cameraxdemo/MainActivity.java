package com.wdl.cameraxdemo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 参考CodeLab
 * https://developer.android.google.cn/training/camerax
 * https://codelabs.developers.google.com/codelabs/camerax-getting-started/#6 需翻墙
 */
public class MainActivity extends AppCompatActivity
{
    private static final String TAG = "MainActivity";
    private TextureView mFinder;
    private ImageButton mIbCapture;
    private ImageButton mIbSwitch;
    private ImageCapture imageCapture;
    private ImageView mResult;
    private CameraX.LensFacing lensFacing = CameraX.LensFacing.BACK;
    private Executor executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFinder = findViewById(R.id.view_finder);
        mIbCapture = findViewById(R.id.capture_button);
        mIbSwitch = findViewById(R.id.switch_button);
        mResult = findViewById(R.id.iv_result);
        if (hasPermission())
        {
            mFinder.post(this::startCamera);
        } else
        {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, 10);
        }

        mFinder.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateTransform()
        );

    }

    /**
     * 旋转时转换、转换等
     */
    private void updateTransform()
    {
        // 矩阵变换
        Matrix matrix = new Matrix();
        float centerX = mFinder.getWidth() / 2f;
        float centerY = mFinder.getHeight() / 2f;
        int ratation = 0;
        switch (mFinder.getDisplay().getRotation())
        {
            case Surface.ROTATION_0:
                ratation = 0;
                break;
            case Surface.ROTATION_90:
                ratation = 90;
                break;
            case Surface.ROTATION_180:
                ratation = 180;
                break;
            case Surface.ROTATION_270:
                ratation = 270;
                break;
        }
        matrix.postRotate(-ratation, centerX, centerY);
        mFinder.setTransform(matrix);
    }

    /**
     * 设置预览参数、更新监听、绑定声明周期
     */
    @SuppressLint("RestrictedApi")
    private void startCamera()
    {
        final PreviewConfig previewConfig = new PreviewConfig.Builder()
                .setTargetResolution(new Size(640, 480))
                .setLensFacing(lensFacing)
                .build();
        final ImageCaptureConfig captureConfig = new ImageCaptureConfig.Builder()
                .setLensFacing(lensFacing)
                .setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                .build();
        imageCapture = new ImageCapture(captureConfig);

        // 拍照
        mIbCapture.setOnClickListener(v ->
        {
            File file = new File(getExternalMediaDirs()[0],
                    System.currentTimeMillis() + ".jpg");
            imageCapture.takePicture(file, executor, new ImageCapture.OnImageSavedListener()
            {
                @Override
                public void onImageSaved(@NonNull File file)
                {
                    Log.e(TAG, "onImageSaved:" + file.getAbsolutePath());
                    mFinder.post(() ->
                    {
                        Toast.makeText(getBaseContext(), "onImageSaved:" + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                    });
                    runOnUiThread(() -> Glide.with(MainActivity.this).load(file).centerCrop().into(mResult));
                }

                @Override
                public void onError(@NonNull ImageCapture.ImageCaptureError imageCaptureError, @NonNull String message, @Nullable Throwable cause)
                {
                    Log.e(TAG, "onError:" + message);
                }
            });
        });

        // 摄像头转换
        mIbSwitch.setOnClickListener(v ->
        {
            lensFacing = CameraX.LensFacing.FRONT == lensFacing ? CameraX.LensFacing.BACK : CameraX.LensFacing.FRONT;
            try
            {
                // Only bind use cases if we can query a camera with this orientation
                CameraX.getCameraWithLensFacing(lensFacing);
                // Unbind all use cases and bind them again with the new lens facing configuration
                CameraX.unbindAll();
                startCamera();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        });


        Preview preview = new Preview(previewConfig);
        preview.setOnPreviewOutputUpdateListener(output ->
        {
            ViewGroup viewGroup = (ViewGroup) mFinder.getParent();
            viewGroup.removeView(mFinder);
            viewGroup.addView(mFinder, 0);

            // 设置预览数据源
            mFinder.setSurfaceTexture(output.getSurfaceTexture());

            updateTransform();
        });

        CameraX.bindToLifecycle(this, preview, imageCapture);
    }

    /**
     * 权限检查
     *
     * @return 是否授权
     */
    private boolean hasPermission()
    {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (requestCode == 10)
        {
            if (hasPermission())
            {
                mFinder.post(this::startCamera);
            } else
            {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        CameraX.unbindAll();
    }
}
