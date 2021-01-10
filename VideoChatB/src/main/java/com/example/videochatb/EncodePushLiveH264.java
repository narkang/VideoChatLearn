package com.example.videochatb;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import com.example.videochatb.camera2.YUVUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class EncodePushLiveH264 {
    private static final String TAG = "ruby";
    private MediaCodec mediaCodec;
    int width;
    int height;
    //    传输 过去
    public SocketLive socketLive;
    public static final int NAL_I = 5;
    public static final int NAL_SPS = 7;
    private byte[] sps_pps_buf;
    int frameIndex;

    public EncodePushLiveH264(SocketLive.SocketCallback socketCallback, int width, int height) {
        this.socketLive = new SocketLive(socketCallback);
        socketLive.start();
        this.width = width;
        this.height = height;
    }

    public void startLive() {
        if(mediaCodec == null){
            try {
                mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, height, width);
                mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 2500000);  //码率
                mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);  //帧率
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
                mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2); //IDR帧刷新时间
                mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mediaCodec.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    ///摄像头调用
    public int encodeFrame(byte[] input) {
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(100000);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            inputBuffer.put(input);
            long presentationTimeUs = computePresentationTime(frameIndex);
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, presentationTimeUs, 0);
            frameIndex++;
        }
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 100000);
        while (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
            dealFrame(outputBuffer, bufferInfo);
            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
        return 0;
    }

    private long computePresentationTime(long frameIndex) {
        //第一帧添加个缓存时间
        return 132 + frameIndex * 1000000 / 15;
    }

    private void dealFrame(ByteBuffer bb, MediaCodec.BufferInfo bufferInfo) {
        int offset = 4;
        if (bb.get(2) == 0x01) {
            offset = 3;
        }
        int type = bb.get(offset) & 0x1F;
        if (type == NAL_SPS) {
            sps_pps_buf = new byte[bufferInfo.size];
            bb.get(sps_pps_buf);
        } else if (type == NAL_I) {
            final byte[] bytes = new byte[bufferInfo.size];
            bb.get(bytes);
            byte[] newBuf = new byte[sps_pps_buf.length + bytes.length];
            System.arraycopy(sps_pps_buf, 0, newBuf, 0, sps_pps_buf.length);
            System.arraycopy(bytes, 0, newBuf, sps_pps_buf.length, bytes.length);
            this.socketLive.sendData(newBuf);
            Log.v(TAG, "视频数据  " + Arrays.toString(newBuf));
        } else {
            final byte[] bytes = new byte[bufferInfo.size];
            bb.get(bytes);
            this.socketLive.sendData(bytes);
            Log.v(TAG, "视频数据  " + Arrays.toString(bytes));
        }
    }
}

