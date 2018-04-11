package org.appspot.apprtc;

/**
 * Created by hanyachao on 2018/4/11.
 */
import android.content.Context;

import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;

import java.util.Timer;
import java.util.TimerTask;

public class PanoVideoCapturer implements VideoCapturer {
    private static final String TAG = "PanoVideoCapturer";
    private final PanoVideoCapturer.VideoReader videoReader;
    private CapturerObserver capturerObserver;
    private final Timer timer = new Timer();
    private final TimerTask tickTask = new TimerTask() {
        public void run() {
            PanoVideoCapturer.this.tick();
        }
    };

    public PanoVideoCapturer(String inputFile) {
        this.videoReader = new PanoVideoCapturer.VideoReaderYUV(inputFile);
    }

    public void tick() {
        //this.capturerObserver.onByteBufferFrameCaptured(...);
    }

    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver capturerObserver) {
        this.capturerObserver = capturerObserver;
    }

    public void startCapture(int width, int height, int framerate) {
        this.timer.schedule(this.tickTask, 0L, (long)(1000 / framerate));
    }

    public void stopCapture() throws InterruptedException {
        this.timer.cancel();
    }

    public void changeCaptureFormat(int width, int height, int framerate) {
    }

    public void dispose(){
        videoReader.close();
    }

    public boolean isScreencast() {
        return false;
    }

    private static class VideoReaderYUV implements PanoVideoCapturer.VideoReader {

        public VideoReaderYUV(String inputFile){

        }

        public void close(){

        }
    }

    private interface VideoReader {
        void close();
    }
}
