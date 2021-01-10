package com.example.videochatb;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

//通话 服务端
public class SocketLive {
    private static final String TAG = "ruby";
    private WebSocket webSocket;
    private SocketCallback socketCallback;
    private ExecutorService service;

    public SocketLive(SocketCallback socketCallback ) {
        this.socketCallback = socketCallback;

        service = Executors.newFixedThreadPool(5);

    }

    public void start() {
        webSocketServer.start();
    }

    public void close() {
        try {
            if(webSocket != null) webSocket.close();
            if(webSocketServer != null) webSocketServer.stop();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
    private WebSocketServer webSocketServer = new WebSocketServer(new InetSocketAddress(12003)) {
        @Override
        public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
            SocketLive.this.webSocket = webSocket;
            Log.i("TAG", "onOpen: 服务端 打开 socket ");
        }

        @Override
        public void onClose(WebSocket webSocket, int i, String s, boolean b) {
            Log.i(TAG, "onClose: 关闭 socket ");
        }

        @Override
        public void onMessage(WebSocket webSocket, String s) {

        }

        private ReentrantLock lock = new ReentrantLock();
        @Override
        public void onMessage(WebSocket conn, ByteBuffer bytes) {
            byte[] buf = new byte[bytes.remaining()];
            bytes.get(buf);
            socketCallback.callBack(buf);
        }

        @Override
        public void onError(WebSocket webSocket, Exception e) {
            Log.i(TAG, "onError:  " + e.toString());
        }

        @Override
        public void onStart() {

        }
    };

    public void sendData(byte[] bytes) {
        if (webSocket != null && webSocket.isOpen()) {
            Log.i("TAG", "sendData:  " + Arrays.toString(bytes));
            webSocket.send(bytes);
        }
    }
    public interface SocketCallback {
        void callBack(byte[] data);
    }
}

