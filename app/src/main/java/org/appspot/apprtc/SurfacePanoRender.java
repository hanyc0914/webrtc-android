package org.appspot.apprtc;

import android.content.Context;
import android.view.SurfaceHolder;

import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.RendererCommon;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;
import org.webrtc.VideoRenderer;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * Created by hanyachao on 2018/4/3.
 */

public class SurfacePanoRender extends PanoRender implements SurfaceHolder.Callback {
    private static final String TAG = "SurfacePanoRenderer";
    private RendererCommon.RendererEvents rendererEvents;
    private final Object layoutLock = new Object();
    private boolean isRenderingPaused = false;
    private boolean isFirstFrameRendered;
    private int rotatedFrameWidth;
    private int rotatedFrameHeight;
    private int frameRotation;

    public SurfacePanoRender(Context context, String name) {
        super(context, name);
    }

    public void init(EglBase.Context sharedContext, RendererCommon.RendererEvents rendererEvents, int[] configAttributes, RendererCommon.GlDrawer drawer) {
        ThreadUtils.checkIsOnMainThread();
        this.rendererEvents = rendererEvents;
        Object var5 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.isFirstFrameRendered = false;
            this.rotatedFrameWidth = 0;
            this.rotatedFrameHeight = 0;
            this.frameRotation = 0;
        }

        super.init(sharedContext, configAttributes, drawer);
    }

    public void init(EglBase.Context sharedContext, int[] configAttributes, RendererCommon.GlDrawer drawer) {
        this.init(sharedContext, (RendererCommon.RendererEvents)null, configAttributes, drawer);
    }

    public void setFpsReduction(float fps) {
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.isRenderingPaused = fps == 0.0F;
        }

        super.setFpsReduction(fps);
    }

    public void disableFpsReduction() {
        Object var1 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.isRenderingPaused = false;
        }

        super.disableFpsReduction();
    }

    public void pauseVideo() {
        Object var1 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.isRenderingPaused = true;
        }

        super.pauseVideo();
    }

    public void renderFrame(VideoRenderer.I420Frame frame) {
        this.updateFrameDimensionsAndReportEvents(frame);
        super.renderFrame(frame);
    }

    public void onFrame(VideoFrame frame) {
        this.updateFrameDimensionsAndReportEvents(frame);
        super.onFrame(frame);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        ThreadUtils.checkIsOnMainThread();
        this.createPanoSurface(holder.getSurface());
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        ThreadUtils.checkIsOnMainThread();
        CountDownLatch completionLatch = new CountDownLatch(1);
        Objects.requireNonNull(completionLatch);
        this.releasePanoSurface(completionLatch::countDown);
        ThreadUtils.awaitUninterruptibly(completionLatch);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        ThreadUtils.checkIsOnMainThread();
        this.logD("surfaceChanged: format: " + format + " size: " + width + "x" + height);
    }

    private void updateFrameDimensionsAndReportEvents(VideoRenderer.I420Frame frame) {
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            if(!this.isRenderingPaused) {
                if(!this.isFirstFrameRendered) {
                    this.isFirstFrameRendered = true;
                    this.logD("Reporting first rendered frame.");
                    if(this.rendererEvents != null) {
                        this.rendererEvents.onFirstFrameRendered();
                    }
                }

                if(this.rotatedFrameWidth != frame.rotatedWidth() || this.rotatedFrameHeight != frame.rotatedHeight() || this.frameRotation != frame.rotationDegree) {
                    this.logD("Reporting frame resolution changed to " + frame.width + "x" + frame.height + " with rotation " + frame.rotationDegree);
                    if(this.rendererEvents != null) {
                        this.rendererEvents.onFrameResolutionChanged(frame.width, frame.height, frame.rotationDegree);
                    }

                    this.rotatedFrameWidth = frame.rotatedWidth();
                    this.rotatedFrameHeight = frame.rotatedHeight();
                    this.frameRotation = frame.rotationDegree;
                }

            }
        }
    }

    private void updateFrameDimensionsAndReportEvents(VideoFrame frame) {
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            if(!this.isRenderingPaused) {
                if(!this.isFirstFrameRendered) {
                    this.isFirstFrameRendered = true;
                    this.logD("Reporting first rendered frame.");
                    if(this.rendererEvents != null) {
                        this.rendererEvents.onFirstFrameRendered();
                    }
                }

                if(this.rotatedFrameWidth != frame.getRotatedWidth() || this.rotatedFrameHeight != frame.getRotatedHeight() || this.frameRotation != frame.getRotation()) {
                    this.logD("Reporting frame resolution changed to " + frame.getBuffer().getWidth() + "x" + frame.getBuffer().getHeight() + " with rotation " + frame.getRotation());
                    if(this.rendererEvents != null) {
                        this.rendererEvents.onFrameResolutionChanged(frame.getBuffer().getWidth(), frame.getBuffer().getHeight(), frame.getRotation());
                    }

                    this.rotatedFrameWidth = frame.getRotatedWidth();
                    this.rotatedFrameHeight = frame.getRotatedHeight();
                    this.frameRotation = frame.getRotation();
                }

            }
        }
    }

    private void logD(String string) {
        Logging.d("SurfacePanoRenderer", this.name + ": " + string);
    }
}
