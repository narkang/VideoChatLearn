package com.example.videochatb.camera2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import com.example.videochatb.EncodePushLiveH264;
import com.example.videochatb.SocketLive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2TextureView extends TextureView implements TextureView.SurfaceTextureListener, ImageReader.OnImageAvailableListener {

    private static final String TAG = "ruby";

    private Context mContext;

    private String mCameraId;
    private CameraCharacteristics mBackCameraCharacteristics;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CaptureRequest mPreviewRequest;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mImageReaderRequest;
    private CaptureRequest.Builder mImageReaderRequestBuilder;
    private CameraCaptureSession mCaptureSession;

    private Surface mWorkingSurface;
    private ImageReader mImageReader;
    private SurfaceTexture mSurfaceTexture;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private Size mPreviewSize;

    public Camera2TextureView(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.mContext = context;
        initializeCameraManager();
    }

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Camera2TextureView.this.mCameraDevice = cameraDevice;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
        }
    };

    protected void prepareCameraOutputs() {
        //获取相机支持的流的参数的集合
        StreamConfigurationMap map = mBackCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        //寻找一个 最合适的尺寸
        mPreviewSize = getBestSupportedSize(new ArrayList<Size>(Arrays.asList(map.getOutputSizes(SurfaceTexture.class))));

        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                ImageFormat.YUV_420_888, 2);
        mImageReader.setOnImageAvailableListener(this, mBackgroundHandler);
    }

    public void openCamera() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            prepareCameraOutputs();
            if (isAvailable()) {
                mCameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            } else {
                setSurfaceTextureListener(this);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    EncodePushLiveH264 encodePushLiveH264;

    public void startCapture(SocketLive.SocketCallback socketCallback) {
        encodePushLiveH264 = new EncodePushLiveH264(socketCallback, mPreviewSize.getWidth(), mPreviewSize.getHeight());
    }

    public void closeCamera() {
        closePreviewSession();
        closeCameraDevice();
        closeImageReader();
    }

    public void onDestroy() {
        stopBackgroundThread();
        encodePushLiveH264.socketLive.close();
    }

    private void createCaptureSession() {
        try {

            mSurfaceTexture = getSurfaceTexture();

            mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            mWorkingSurface = new Surface(mSurfaceTexture);

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mWorkingSurface);
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());

            mCameraDevice.createCaptureSession(Arrays.asList(mWorkingSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            updatePreview(cameraCaptureSession);
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "Fail while starting preview: ");
                        }
                    }, null);

        } catch (Exception e) {
            Log.e(TAG, "Error while preparing surface for preview: ", e);
        }
    }

    //https://blog.csdn.net/afei__/article/details/86108482
    private void updatePreview(CameraCaptureSession cameraCaptureSession) {
        if (null == mCameraDevice) {
            return;
        }
        mCaptureSession = cameraCaptureSession;
        //设置自动对焦模式为连续自动对焦
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        //创建CaptureRequest
        mPreviewRequest = mPreviewRequestBuilder.build();
        try {
            //开始预览，由于我们现在需要对预览的图像数据做处理
            mCaptureSession.setRepeatingRequest(mPreviewRequest, null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (Build.VERSION.SDK_INT > 17) {
            mBackgroundThread.quitSafely();
        } else mBackgroundThread.quit();

        try {
            mBackgroundThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "stopBackgroundThread: ", e);
        } finally {
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
    }

    private void initializeCameraManager() {

        startBackgroundThread();

        mCameraManager = (CameraManager) this.mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            //获取可用的相机列表
            String[] cameraIdList = mCameraManager.getCameraIdList();
            for (String cameraId : cameraIdList) {
                //获取该相机的CameraCharacteristics，它保存的相机相关的属性
                CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                //获取相机的方向
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                //如果是前置摄像头就continue，我们这里只用后置摄像头
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                //保存cameraId
                this.mCameraId = cameraId;
                this.mBackCameraCharacteristics = cameraCharacteristics;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closePreviewSession() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            try {
                mCaptureSession.abortCaptures();
            } catch (Exception ignore) {
            } finally {
                mCaptureSession = null;
            }
        }
    }

    private void releaseTexture() {
        if (null != mSurfaceTexture) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
    }

    private void closeImageReader() {
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    private void closeCameraDevice() {
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    private byte[] y;
    private byte[] u;
    private byte[] v;

    private byte[] nv21;//width  height
    byte[] nv21_rotated;
    byte[] nv12;

    @Override
    public void onImageAvailable(ImageReader reader) {

        Image image= reader.acquireNextImage();
        Image.Plane[] planes =  image.getPlanes();
        // 重复使用同一批byte数组，减少gc频率
        if (y == null) {
            y = new byte[planes[0].getBuffer().limit() - planes[0].getBuffer().position()];
            u = new byte[planes[1].getBuffer().limit() - planes[1].getBuffer().position()];
            v = new byte[planes[2].getBuffer().limit() - planes[2].getBuffer().position()];
        }
        if (image.getPlanes()[0].getBuffer().remaining() == y.length) {
            planes[0].getBuffer().get(y);
            planes[1].getBuffer().get(u);
            planes[2].getBuffer().get(v);
        }

        if (nv21 == null) {
//            实例化一次
            nv21 = new byte[planes[0].getRowStride() * mPreviewSize.getHeight() * 3 / 2];
            nv21_rotated = new byte[planes[0].getRowStride() * mPreviewSize.getHeight() * 3 / 2];
        }
        ImageUtil.yuvToNv21(y, u, v, nv21, planes[0].getRowStride(), mPreviewSize.getHeight());
        ImageUtil.nv21_rotate_to_90(nv21, nv21_rotated, planes[0].getRowStride(), mPreviewSize.getHeight());
        byte[] temp = ImageUtil.nv21toNV12(nv21_rotated, nv12);

        if(encodePushLiveH264 != null){
            encodePushLiveH264.startLive();
            encodePushLiveH264.encodeFrame(temp);
        }

        image.close();
    }

    private Point previewViewSize;
    private Size getBestSupportedSize(List<Size> sizes) {
        Point maxPreviewSize = new Point(1920, 1080);
        Point minPreviewSize = new Point(1280, 720);
        Size defaultSize = sizes.get(0);
        Size[] tempSizes = sizes.toArray(new Size[0]);
        Arrays.sort(tempSizes, new Comparator<Size>() {
            @Override
            public int compare(Size o1, Size o2) {
                if (o1.getWidth() > o2.getWidth()) {
                    return -1;
                } else if (o1.getWidth() == o2.getWidth()) {
                    return o1.getHeight() > o2.getHeight() ? -1 : 1;
                } else {
                    return 1;
                }
            }
        });
        sizes = new ArrayList<>(Arrays.asList(tempSizes));
        for (int i = sizes.size() - 1; i >= 0; i--) {
            if (maxPreviewSize != null) {
                if (sizes.get(i).getWidth() > maxPreviewSize.x || sizes.get(i).getHeight() > maxPreviewSize.y) {
                    sizes.remove(i);
                    continue;
                }
            }
            if (minPreviewSize != null) {
                if (sizes.get(i).getWidth() < minPreviewSize.x || sizes.get(i).getHeight() < minPreviewSize.y) {
                    sizes.remove(i);
                }
            }
        }
        if (sizes.size() == 0) {
            return defaultSize;
        }
        Size bestSize = sizes.get(0);
        float previewViewRatio;
        if (previewViewSize != null) {
            previewViewRatio = (float) previewViewSize.x / (float) previewViewSize.y;
        } else {
            previewViewRatio = (float) bestSize.getWidth() / (float) bestSize.getHeight();
        }

        if (previewViewRatio > 1) {
            previewViewRatio = 1 / previewViewRatio;
        }

        for (Size s : sizes) {
            if (Math.abs((s.getHeight() / (float) s.getWidth()) - previewViewRatio) < Math.abs(bestSize.getHeight() / (float) bestSize.getWidth() - previewViewRatio)) {
                bestSize = s;
            }
        }
        return bestSize;
    }
}
