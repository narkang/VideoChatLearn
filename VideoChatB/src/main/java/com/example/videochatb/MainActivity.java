package com.example.videochatb;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.example.videochatb.camera2.Camera2TextureView;
import com.example.videochatb.camera2.ImageUtil;

import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements SocketLive.SocketCallback{

    private Camera2TextureView localTextureView;
    private TextureView remoteTextureView;
    private DecodePlayerLiveH264 decodePlayerLiveH264;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        localTextureView = findViewById(R.id.localSurfaceView);

        initRemoteTextureView();
    }

    private void initRemoteTextureView() {

        remoteTextureView = findViewById(R.id.remoteTextureView);
        remoteTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Surface remoteSurface = new Surface(surface);
                decodePlayerLiveH264 = new DecodePlayerLiveH264();
                decodePlayerLiveH264.initDecoder(remoteSurface);
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
        });

    }

    public void connect(View view) {
        localTextureView.startCapture(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermission()) {
            localTextureView.openCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        localTextureView.closeCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        localTextureView.onDestroy();
    }

    public boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
            }, 1);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            localTextureView.openCamera();
        }
    }

    @Override
    public void callBack(byte[] data) {
        if (decodePlayerLiveH264 != null) {
            decodePlayerLiveH264.callBack(data);
        }
    }

    private byte[] nv21;//width  height
    byte[] nv21_rotated;
    byte[] nv12;

}