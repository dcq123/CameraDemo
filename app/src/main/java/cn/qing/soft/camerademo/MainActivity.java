package cn.qing.soft.camerademo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    Camera mCamera;
    CameraConfigurationManager cameraConfigurationManager;

    Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            // 先解析数据,获取拍照得到的bitmap,但是该bitmap数据需要进行旋转,才能够得到正确角度的照片
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            // 获取存储路径
            File file = getOutputMediaFile();
            try {
                if (isBackCameraOn) {
                    bitmap = rotateBitmapByDegree(bitmap, cameraConfigurationManager.getDisplayOrientation());
                } else {
                    bitmap = rotateBitmapByDegree(bitmap, -cameraConfigurationManager.getDisplayOrientation());
                }
                FileOutputStream fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.flush();
                fos.close();
                // 获取照片后,相机会stop预览,所以需要手动重新开启预览
                setStartPreview(mCamera, surfaceHolder);
                Toast.makeText(MainActivity.this, "图片保存成功", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private Bitmap rotateBitmapByDegree(Bitmap bitmap, int degree) {
        Bitmap resultBitmap = null;
        Matrix matrix = new Matrix();
        matrix.setRotate(degree);
        if (!isBackCameraOn) {
            matrix.postScale(-1, 1);    // 镜像水平翻转
        }
        resultBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (resultBitmap == null) {
            resultBitmap = bitmap;
        }
        if (bitmap != resultBitmap) {
            bitmap.recycle();
        }
        return resultBitmap;
    }

    /**
     * 获取图片保持路径
     *
     * @return pic Path
     */
    private File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyCameraApp");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + "scan.jpeg");
        return mediaFile;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        cameraConfigurationManager = new CameraConfigurationManager(this);
        initView();
    }

    private void initView() {
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        // 在点击surfaceView时进行一次自动聚焦
        surfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCamera.autoFocus(null);
            }
        });
    }

    /**
     * 拍照
     *
     * @param view
     */
    public void capture(View view) {
        Camera.Parameters params = mCamera.getParameters();
        params.setPictureFormat(ImageFormat.JPEG);
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        mCamera.setParameters(params);
        // 在自动聚焦成功时,获取照片数据
        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if (isBackCameraOn) {
                    if (success) {
                        mCamera.takePicture(null, null, pictureCallback);
                    }
                } else {
                    System.out.println("前置摄像头");
                    mCamera.takePicture(null, null, pictureCallback);
                }
            }
        });
    }

    private boolean isBackCameraOn = true;

    /**
     * 切换前置摄像头
     *
     * @param view
     */
    public void switchCamera(View view) {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int cameraCount = Camera.getNumberOfCameras();
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            // 当前是后置摄像头
            if (isBackCameraOn) {
                // 遍历到前置摄像头时,使用前置摄像头
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    // 先释放相机资源
                    releaseCamera();
                    // 使用带参数的方法,创建相机实例
                    mCamera = Camera.open(i);
                    // 重新配置相机基本参数
                    configCamera();
                    isBackCameraOn = false;
                    break;
                }
            } else {
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    releaseCamera();
                    mCamera = Camera.open(i);
                    configCamera();
                    isBackCameraOn = true;
                    break;
                }
            }
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        // 检查相机是否可用,是否已创建
        if (checkCameraHardware(this) && (mCamera == null)) {
            // 获取相机
            mCamera = getCamera();
            // 对相机参数进行配置
            configCamera();
        }
    }

    private void configCamera() {
        cameraConfigurationManager.initFromCameraParameters(mCamera);
        cameraConfigurationManager.setDesiredCameraParameters(mCamera);
        if (surfaceHolder != null) {
            setStartPreview(mCamera, surfaceHolder);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    /**
     * 将相机和SurfaceView绑定
     *
     * @param camera
     * @param surfaceHolder
     */
    private void setStartPreview(Camera camera, SurfaceHolder surfaceHolder) {
        try {
            // 将SurfaceView的Holder设置给Camera
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Camera getCamera() {
        Camera camera;
        try {
            camera = Camera.open();
        } catch (Exception e) {
            camera = null;
        }
        return camera;
    }

    /**
     * 释放相机
     */
    private void releaseCamera() {
        if (mCamera != null) {
            // 清空预览回到
            mCamera.setPreviewCallback(null);
            // 停止预览视图
            mCamera.stopPreview();
            // 释放相机资源
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * 检查系统是否有相机
     *
     * @param context
     * @return
     */
    private boolean checkCameraHardware(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // 将SurfaceHolder与Camera进行关联,并开启预览
        setStartPreview(mCamera, holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        try {
            // 当surface发生变化时,先暂停预览
            mCamera.stopPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 暂停后重新关联并开启预览
        setStartPreview(mCamera, holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // 释放相机资源
        releaseCamera();
    }
}
