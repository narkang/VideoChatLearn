package com.example.videochatA;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;

//通话 客户端
public class SocketLive {
    private static final String TAG = "ruby";
    private SocketCallback socketCallback;
    MyWebSocketClient myWebSocketClient;
    private ExecutorService service;

    public SocketLive(SocketCallback socketCallback) {
        this.socketCallback = socketCallback;

        service = Executors.newFixedThreadPool(5);

    }

    public void start() {
        try {
            URI url = new URI("ws://192.168.137.23:12003");
            myWebSocketClient = new MyWebSocketClient(url);
            myWebSocketClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendData(byte[] bytes) {
        if (myWebSocketClient != null && (myWebSocketClient.isOpen())) {
            myWebSocketClient.send(bytes);
        }
    }


    private class MyWebSocketClient extends WebSocketClient {

        public MyWebSocketClient(URI serverURI) {
            super(serverURI);
        }

        @Override
        public void onOpen(ServerHandshake serverHandshake) {
            Log.i(TAG, "客户端 打开 socket  onOpen: ");
        }

        @Override
        public void onMessage(String s) {
        }

        private ReentrantLock lock = new ReentrantLock();

        @Override
        public void onMessage(ByteBuffer bytes) {
            byte[] buf = new byte[bytes.remaining()];
            bytes.get(buf);
            socketCallback.callBack(buf);
        }

        @Override
        public void onClose(int i, String s, boolean b) {
            Log.i(TAG, "onClose: ");
        }

        @Override
        public void onError(Exception e) {
            Log.i(TAG, "onError: ", e);
        }
    }

    public interface SocketCallback {
        void callBack(byte[] data);
    }
}


