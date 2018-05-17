package org.appspot.apprtc;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

import org.webrtc.EglBase;
import org.webrtc.GlRectDrawer;
import org.webrtc.Logging;
import org.webrtc.RendererCommon;
import org.webrtc.RendererCommon.RendererEvents;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRenderer.Callbacks;
import org.webrtc.VideoSink;


/**
 * Created by hanyachao on 2018/4/3.
 */

public class SurfaceViewPanoRender extends SurfaceView implements Callback, Callbacks, VideoSink, RendererEvents {
    private static final String TAG = "SurfaceViewPanoRender";
    private final String resourceName = this.getResourceName();
    private final RendererCommon.VideoLayoutMeasure videoLayoutMeasure = new RendererCommon.VideoLayoutMeasure();
    private final SurfacePanoRender panoRenderer;
    private RendererEvents rendererEvents;
    private int rotatedFrameWidth;
    private int rotatedFrameHeight;
    private boolean enableFixedSize;
    private int surfaceWidth;
    private int surfaceHeight;

    public SurfaceViewPanoRender(Context context) {
        super(context);
        this.panoRenderer = new SurfacePanoRender(context, this.resourceName);
        this.getHolder().addCallback(this);
        this.getHolder().addCallback(this.panoRenderer);
    }

    public SurfaceViewPanoRender(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.panoRenderer = new SurfacePanoRender(context, this.resourceName);
        this.getHolder().addCallback(this);
        this.getHolder().addCallback(this.panoRenderer);
    }

    public void init(org.webrtc.EglBase.Context sharedContext, RendererEvents rendererEvents) {
        this.init(sharedContext, rendererEvents, EglBase.CONFIG_PLAIN, new GlRectDrawer());
    }

    public void init(org.webrtc.EglBase.Context sharedContext, RendererEvents rendererEvents, int[] configAttributes, RendererCommon.GlDrawer drawer) {
        ThreadUtils.checkIsOnMainThread();
        this.rendererEvents = rendererEvents;
        this.rotatedFrameWidth = 0;
        this.rotatedFrameHeight = 0;
        this.panoRenderer.init(sharedContext, this, configAttributes, drawer);
    }

    public void release() {
        this.panoRenderer.release();
    }

    public void addFrameListener(PanoRender.FrameListener listener, float scale, RendererCommon.GlDrawer drawerParam) {
        this.panoRenderer.addFrameListener(listener, scale, drawerParam);
    }

    public void addFrameListener(PanoRender.FrameListener listener, float scale) {
        this.panoRenderer.addFrameListener(listener, scale);
    }

    public void removeFrameListener(PanoRender.FrameListener listener) {
        this.panoRenderer.removeFrameListener(listener);
    }

    public void setEnableHardwareScaler(boolean enabled) {
        ThreadUtils.checkIsOnMainThread();
        this.enableFixedSize = enabled;
        this.updateSurfaceSize();
    }

    public void setMirror(boolean mirror) {
        this.panoRenderer.setMirror(mirror);
    }

    public void setScalingType(RendererCommon.ScalingType scalingType) {
        ThreadUtils.checkIsOnMainThread();
        this.videoLayoutMeasure.setScalingType(scalingType);
        this.requestLayout();
    }

    public void setScalingType(RendererCommon.ScalingType scalingTypeMatchOrientation, RendererCommon.ScalingType scalingTypeMismatchOrientation) {
        ThreadUtils.checkIsOnMainThread();
        this.videoLayoutMeasure.setScalingType(scalingTypeMatchOrientation, scalingTypeMismatchOrientation);
        this.requestLayout();
    }

    public void setFpsReduction(float fps) {
        this.panoRenderer.setFpsReduction(fps);
    }

    public void disableFpsReduction() {
        this.panoRenderer.disableFpsReduction();
    }

    public void pauseVideo() {
        this.panoRenderer.pauseVideo();
    }

    public void renderFrame(VideoRenderer.I420Frame frame) {
        this.panoRenderer.renderFrame(frame);
    }

    public void onFrame(VideoFrame frame) {
        this.panoRenderer.onFrame(frame);
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
        ThreadUtils.checkIsOnMainThread();
        Point size = this.videoLayoutMeasure.measure(widthSpec, heightSpec, this.rotatedFrameWidth, this.rotatedFrameHeight);
        this.setMeasuredDimension(size.x, size.y);
        this.logD("onMeasure(). New size: " + size.x + "x" + size.y);
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        ThreadUtils.checkIsOnMainThread();
        this.panoRenderer.setLayoutAspectRatio((float)(right - left) / (float)(bottom - top));
        this.updateSurfaceSize();
    }

    private void updateSurfaceSize() {
        ThreadUtils.checkIsOnMainThread();
        if(this.enableFixedSize && this.rotatedFrameWidth != 0 && this.rotatedFrameHeight != 0 && this.getWidth() != 0 && this.getHeight() != 0) {
            float layoutAspectRatio = (float)this.getWidth() / (float)this.getHeight();
            float frameAspectRatio = (float)this.rotatedFrameWidth / (float)this.rotatedFrameHeight;
            int drawnFrameWidth;
            int drawnFrameHeight;
            if(frameAspectRatio > layoutAspectRatio) {
                drawnFrameWidth = (int)((float)this.rotatedFrameHeight * layoutAspectRatio);
                drawnFrameHeight = this.rotatedFrameHeight;
            } else {
                drawnFrameWidth = this.rotatedFrameWidth;
                drawnFrameHeight = (int)((float)this.rotatedFrameWidth / layoutAspectRatio);
            }

            int width = Math.min(this.getWidth(), drawnFrameWidth);
            int height = Math.min(this.getHeight(), drawnFrameHeight);
            this.logD("updateSurfaceSize. Layout size: " + this.getWidth() + "x" + this.getHeight() + ", frame size: " + this.rotatedFrameWidth + "x" + this.rotatedFrameHeight + ", requested surface size: " + width + "x" + height + ", old surface size: " + this.surfaceWidth + "x" + this.surfaceHeight);
            if(width != this.surfaceWidth || height != this.surfaceHeight) {
                this.surfaceWidth = width;
                this.surfaceHeight = height;
                this.getHolder().setFixedSize(width, height);
            }
        } else {
            this.surfaceWidth = this.surfaceHeight = 0;
            this.getHolder().setSizeFromLayout();
        }

    }

    public void surfaceCreated(SurfaceHolder holder) {
        ThreadUtils.checkIsOnMainThread();
        this.surfaceWidth = this.surfaceHeight = 0;
        this.updateSurfaceSize();
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    private String getResourceName() {
        try {
            return this.getResources().getResourceEntryName(this.getId());
        } catch (Resources.NotFoundException var2) {
            return "";
        }
    }

    public void clearImage() {
        this.panoRenderer.clearImage();
    }

    public void onFirstFrameRendered() {
        if(this.rendererEvents != null) {
            this.rendererEvents.onFirstFrameRendered();
        }

    }

    public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {
        if(this.rendererEvents != null) {
            this.rendererEvents.onFrameResolutionChanged(videoWidth, videoHeight, rotation);
        }

        int rotatedWidth = rotation != 0 && rotation != 180?videoHeight:videoWidth;
        int rotatedHeight = rotation != 0 && rotation != 180?videoWidth:videoHeight;
        this.postOrRun(() -> {
            this.rotatedFrameWidth = rotatedWidth;
            this.rotatedFrameHeight = rotatedHeight;
            this.updateSurfaceSize();
            this.requestLayout();
        });
    }

    private void postOrRun(Runnable r) {
        if(Thread.currentThread() == Looper.getMainLooper().getThread()) {
            r.run();
        } else {
            this.post(r);
        }

    }

    private void logD(String string) {
        Logging.d("SurfaceViewPanoRender", this.resourceName + ": " + string);
    }
}
