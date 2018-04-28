//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.appspot.apprtc;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.webrtc.JavaI420Buffer;
import org.webrtc.Logging;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturer.CapturerObserver;
import org.webrtc.VideoFrame;

public class PanoVideoCapturer implements VideoCapturer {
    private static final String TAG = "PanoVideoCapturer";
    private CapturerObserver capturerObserver;
    private String inputUrl;
    private LinkedBlockingQueue<VideoFrame> fqueue = new LinkedBlockingQueue<VideoFrame>();
    private LinkedBlockingQueue<byte[]> bqueue = new LinkedBlockingQueue<byte[]>();
    private int width;
    private int height;
    private final Timer timer = new Timer();
    private final TimerTask tickTask = new TimerTask() {
        public void run() {
            PanoVideoCapturer.this.tick();
        }
    };
    private boolean mShouldDecode = false;
    private Thread decodeThread = new Thread() {
        @Override
        public void run() {
            while (mShouldDecode && !Thread.interrupted()) {
                startDecode(PanoVideoCapturer.this);
            }
        }
    };

    public PanoVideoCapturer(String inputFile) throws IOException {
        this.inputUrl = inputFile;
        Log.i("native ffmpeg decode","start");
        initialWithUrl(inputUrl);
    }

    public void tick() {
        VideoFrame frame = null;
        try {
            frame = fqueue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(frame != null){
            this.capturerObserver.onFrameCaptured(frame);
        }
    }

    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver capturerObserver) {
        this.capturerObserver = capturerObserver;
    }

    public void startCapture(int width, int height, int framerate) {
        mShouldDecode = true;
        decodeThread.start();
        this.timer.schedule(this.tickTask, 0L, (long)(1000 / framerate));
    }

    public void stopCapture() throws InterruptedException {
        this.timer.cancel();
        mShouldDecode = false;
        decodeThread.interrupt();
    }

    public void changeCaptureFormat(int width, int height, int framerate) {
    }

    public void dispose() {
        //this.videoReader.close();
    }

    public boolean isScreencast() {
        return false;
    }

    public void setCodecInfo(int fwidth, int fheight){
        this.width = fwidth;
        this.height = fheight;
    }

    public void onFrameDecode(byte[] yuvbuffer){
        long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
        JavaI420Buffer buffer = JavaI420Buffer.allocate(this.width, this.height);
        ByteBuffer dataY = buffer.getDataY();
        ByteBuffer dataU = buffer.getDataU();
        ByteBuffer dataV = buffer.getDataV();
        int ysize = this.width * this.height;
        dataY.put(yuvbuffer, 0, ysize);
        dataU.put(yuvbuffer, ysize, ysize/4);
        dataV.put(yuvbuffer, 5*ysize/4, ysize/4);
//        dataY = ByteBuffer.wrap(yuvbuffer, 0, ysize);
//        dataU = ByteBuffer.wrap(yuvbuffer, ysize, ysize/4);
//        dataV = ByteBuffer.wrap(yuvbuffer, ysize*5/4, ysize/4);
        VideoFrame frame = new VideoFrame(buffer, 0, captureTimeNs);
        fqueue.offer(frame);
        int size = fqueue.size();
    }
    public native void initialWithUrl(String inputurl);
    public native void startDecode(PanoVideoCapturer capturer);

    static {
        System.loadLibrary("avutil");
        System.loadLibrary("swresample");
        System.loadLibrary("avcodec");
        System.loadLibrary("avformat");
        System.loadLibrary("swscale");
        System.loadLibrary("avfilter");
        System.loadLibrary("rtspclient");
    }
}
