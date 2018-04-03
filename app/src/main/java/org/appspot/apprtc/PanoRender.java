package org.appspot.apprtc;

import org.webrtc.EglBase;
import org.webrtc.GlTextureFrameBuffer;
import org.webrtc.GlUtil;
import org.webrtc.JavaI420Buffer;
import org.webrtc.Logging;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;
import org.webrtc.VideoFrameDrawer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSink;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.Bitmap.Config;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.webrtc.EglBase.Context;
import org.webrtc.RendererCommon.GlDrawer;
import org.webrtc.VideoRenderer.Callbacks;
import org.webrtc.VideoRenderer.I420Frame;

/**
 * Created by hanyachao on 2018/4/3.
 */

public class PanoRender implements Callbacks, VideoSink {
    private static final String TAG = "PanoRender";
    private static final long LOG_INTERVAL_SEC = 4L;
    protected final String name;
    private final Object handlerLock = new Object();
    private Handler renderThreadHandler;
    private final ArrayList<PanoRender.FrameListenerAndParams> frameListeners = new ArrayList();
    private final Object fpsReductionLock = new Object();
    private long nextFrameTimeNs;
    private long minRenderPeriodNs;
    private EglBase eglBase;
    private final VideoFrameDrawer frameDrawer = new VideoFrameDrawer();
    private GlDrawer drawer;
    private final Matrix drawMatrix = new Matrix();
    private final Object frameLock = new Object();
    private VideoFrame pendingFrame;
    private final Object layoutLock = new Object();
    private float layoutAspectRatio;
    private boolean mirror;
    private final Object statisticsLock = new Object();
    private int framesReceived;
    private int framesDropped;
    private int framesRendered;
    private long statisticsStartTimeNs;
    private long renderTimeNs;
    private long renderSwapBufferTimeNs;
    private GlTextureFrameBuffer bitmapTextureFramebuffer;
    private final Runnable logStatisticsRunnable = new Runnable() {
        public void run() {
            PanoRender.this.logStatistics();
            synchronized(PanoRender.this.handlerLock) {
                if(PanoRender.this.renderThreadHandler != null) {
                    PanoRender.this.renderThreadHandler.removeCallbacks(PanoRender.this.logStatisticsRunnable);
                    PanoRender.this.renderThreadHandler.postDelayed(PanoRender.this.logStatisticsRunnable, TimeUnit.SECONDS.toMillis(4L));
                }

            }
        }
    };
    private final PanoRender.PanoSurfaceCreation panoSurfaceCreationRunnable = new PanoRender.PanoSurfaceCreation();

    public PanoRender(String name) {
        this.name = name;
    }

    public void init(Context sharedContext, int[] configAttributes, GlDrawer drawer) {
        Object var4 = this.handlerLock;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler != null) {
                throw new IllegalStateException(this.name + "Already initialized");
            } else {
                this.logD("Initializing PanoRender");
                this.drawer = drawer;
                HandlerThread renderThread = new HandlerThread(this.name + "PanoRender");
                renderThread.start();
                this.renderThreadHandler = new Handler(renderThread.getLooper());
                ThreadUtils.invokeAtFrontUninterruptibly(this.renderThreadHandler, () -> {
                    if(sharedContext == null) {
                        this.logD("EglBase10.create context");
                        this.eglBase = EglBase.createEgl10(configAttributes);
                    } else {
                        this.logD("EglBase.create shared context");
                        this.eglBase = EglBase.create(sharedContext, configAttributes);
                    }

                });
                this.renderThreadHandler.post(this.panoSurfaceCreationRunnable);
                long currentTimeNs = System.nanoTime();
                this.resetStatistics(currentTimeNs);
                this.renderThreadHandler.postDelayed(this.logStatisticsRunnable, TimeUnit.SECONDS.toMillis(4L));
            }
        }
    }

    public void createPanoSurface(Surface surface) {
        this.createPanoSurfaceInternal(surface);
    }

    public void createPanoSurface(SurfaceTexture surfaceTexture) {
        this.createPanoSurfaceInternal(surfaceTexture);
    }

    private void createPanoSurfaceInternal(Object surface) {
        this.panoSurfaceCreationRunnable.setSurface(surface);
        this.postToRenderThread(this.panoSurfaceCreationRunnable);
    }

    public void release() {
        this.logD("Releasing.");
        CountDownLatch panoCleanupBarrier = new CountDownLatch(1);
        Object var2 = this.handlerLock;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler == null) {
                this.logD("Already released");
                return;
            }

            this.renderThreadHandler.removeCallbacks(this.logStatisticsRunnable);
            this.renderThreadHandler.postAtFrontOfQueue(() -> {
                if(this.drawer != null) {
                    this.drawer.release();
                    this.drawer = null;
                }

                this.frameDrawer.release();
                if(this.bitmapTextureFramebuffer != null) {
                    this.bitmapTextureFramebuffer.release();
                    this.bitmapTextureFramebuffer = null;
                }

                if(this.eglBase != null) {
                    this.logD("eglBase detach and release.");
                    this.eglBase.detachCurrent();
                    this.eglBase.release();
                    this.eglBase = null;
                }

                this.frameListeners.clear();
                panoCleanupBarrier.countDown();
            });
            Looper renderLooper = this.renderThreadHandler.getLooper();
            this.renderThreadHandler.post(() -> {
                this.logD("Quitting render thread.");
                renderLooper.quit();
            });
            this.renderThreadHandler = null;
        }

        ThreadUtils.awaitUninterruptibly(panoCleanupBarrier);
        var2 = this.frameLock;
        synchronized(this.frameLock) {
            if(this.pendingFrame != null) {
                this.pendingFrame.release();
                this.pendingFrame = null;
            }
        }

        this.logD("Releasing done.");
    }

    private void resetStatistics(long currentTimeNs) {
        Object var3 = this.statisticsLock;
        synchronized(this.statisticsLock) {
            this.statisticsStartTimeNs = currentTimeNs;
            this.framesReceived = 0;
            this.framesDropped = 0;
            this.framesRendered = 0;
            this.renderTimeNs = 0L;
            this.renderSwapBufferTimeNs = 0L;
        }
    }

    public void printStackTrace() {
        Object var1 = this.handlerLock;
        synchronized(this.handlerLock) {
            Thread renderThread = this.renderThreadHandler == null?null:this.renderThreadHandler.getLooper().getThread();
            if(renderThread != null) {
                StackTraceElement[] renderStackTrace = renderThread.getStackTrace();
                if(renderStackTrace.length > 0) {
                    this.logD("PanoRender stack trace:");
                    StackTraceElement[] var4 = renderStackTrace;
                    int var5 = renderStackTrace.length;

                    for(int var6 = 0; var6 < var5; ++var6) {
                        StackTraceElement traceElem = var4[var6];
                        this.logD(traceElem.toString());
                    }
                }
            }

        }
    }

    public void setMirror(boolean mirror) {
        this.logD("setMirror: " + mirror);
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.mirror = mirror;
        }
    }

    public void setLayoutAspectRatio(float layoutAspectRatio) {
        this.logD("setLayoutAspectRatio: " + layoutAspectRatio);
        Object var2 = this.layoutLock;
        synchronized(this.layoutLock) {
            this.layoutAspectRatio = layoutAspectRatio;
        }
    }

    public void setFpsReduction(float fps) {
        this.logD("setFpsReduction: " + fps);
        Object var2 = this.fpsReductionLock;
        synchronized(this.fpsReductionLock) {
            long previousRenderPeriodNs = this.minRenderPeriodNs;
            if(fps <= 0.0F) {
                this.minRenderPeriodNs = 9223372036854775807L;
            } else {
                this.minRenderPeriodNs = (long)((float)TimeUnit.SECONDS.toNanos(1L) / fps);
            }

            if(this.minRenderPeriodNs != previousRenderPeriodNs) {
                this.nextFrameTimeNs = System.nanoTime();
            }

        }
    }

    public void disableFpsReduction() {
        this.setFpsReduction(1.0F / 0.0F);
    }

    public void pauseVideo() {
        this.setFpsReduction(0.0F);
    }

    public void addFrameListener(PanoRender.FrameListener listener, float scale) {
        this.addFrameListener(listener, scale, (GlDrawer)null, false);
    }

    public void addFrameListener(PanoRender.FrameListener listener, float scale, GlDrawer drawerParam) {
        this.addFrameListener(listener, scale, drawerParam, false);
    }

    public void addFrameListener(PanoRender.FrameListener listener, float scale, GlDrawer drawerParam, boolean applyFpsReduction) {
        this.postToRenderThread(() -> {
            GlDrawer listenerDrawer = drawerParam == null?this.drawer:drawerParam;
            this.frameListeners.add(new PanoRender.FrameListenerAndParams(listener, scale, listenerDrawer, applyFpsReduction));
        });
    }

    public void removeFrameListener(PanoRender.FrameListener listener) {
        CountDownLatch latch = new CountDownLatch(1);
        Object var3 = this.handlerLock;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler == null) {
                return;
            }

            if(Thread.currentThread() == this.renderThreadHandler.getLooper().getThread()) {
                throw new RuntimeException("removeFrameListener must not be called on the render thread.");
            }

            this.postToRenderThread(() -> {
                latch.countDown();
                Iterator iter = this.frameListeners.iterator();

                while(iter.hasNext()) {
                    if(((PanoRender.FrameListenerAndParams)iter.next()).listener == listener) {
                        iter.remove();
                    }
                }

            });
        }

        ThreadUtils.awaitUninterruptibly(latch);
    }

    public void renderFrame(I420Frame frame) {
        Object buffer;
        buffer = JavaI420Buffer.wrap(frame.width, frame.height, frame.yuvPlanes[0], frame.yuvStrides[0], frame.yuvPlanes[1], frame.yuvStrides[1], frame.yuvPlanes[2], frame.yuvStrides[2], () -> {
            VideoRenderer.renderFrameDone(frame);
        });
        VideoFrame videoFrame = new VideoFrame((VideoFrame.Buffer)buffer, frame.rotationDegree, 0L);
        this.onFrame(videoFrame);
        videoFrame.release();
    }

    public void onFrame(VideoFrame frame) {
        Object var2 = this.statisticsLock;
        synchronized(this.statisticsLock) {
            ++this.framesReceived;
        }

        Object var3 = this.handlerLock;
        boolean dropOldFrame;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler == null) {
                this.logD("Dropping frame - Not initialized or already released.");
                return;
            }

            Object var4 = this.frameLock;
            synchronized(this.frameLock) {
                dropOldFrame = this.pendingFrame != null;
                if(dropOldFrame) {
                    this.pendingFrame.release();
                }

                this.pendingFrame = frame;
                this.pendingFrame.retain();
                this.renderThreadHandler.post(this::renderFrameOnRenderThread);
            }
        }

        if(dropOldFrame) {
            var3 = this.statisticsLock;
            synchronized(this.statisticsLock) {
                ++this.framesDropped;
            }
        }

    }

    public void releasePanoSurface(Runnable completionCallback) {
        this.panoSurfaceCreationRunnable.setSurface((Object)null);
        Object var2 = this.handlerLock;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler != null) {
                this.renderThreadHandler.removeCallbacks(this.panoSurfaceCreationRunnable);
                this.renderThreadHandler.postAtFrontOfQueue(() -> {
                    if(this.eglBase != null) {
                        this.eglBase.detachCurrent();
                        this.eglBase.releaseSurface();
                    }

                    completionCallback.run();
                });
                return;
            }
        }

        completionCallback.run();
    }

    private void postToRenderThread(Runnable runnable) {
        Object var2 = this.handlerLock;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler != null) {
                this.renderThreadHandler.post(runnable);
            }

        }
    }

    private void clearSurfaceOnRenderThread(float r, float g, float b, float a) {
        if(this.eglBase != null && this.eglBase.hasSurface()) {
            this.logD("clearSurface");
            GLES20.glClearColor(r, g, b, a);
            GLES20.glClear(16384);
            this.eglBase.swapBuffers();
        }

    }

    public void clearImage() {
        this.clearImage(0.0F, 0.0F, 0.0F, 0.0F);
    }

    public void clearImage(float r, float g, float b, float a) {
        Object var5 = this.handlerLock;
        synchronized(this.handlerLock) {
            if(this.renderThreadHandler != null) {
                this.renderThreadHandler.postAtFrontOfQueue(() -> {
                    this.clearSurfaceOnRenderThread(r, g, b, a);
                });
            }
        }
    }

    private void renderFrameOnRenderThread() {
        Object var2 = this.frameLock;
        VideoFrame frame;
        synchronized(this.frameLock) {
            if(this.pendingFrame == null) {
                return;
            }

            frame = this.pendingFrame;
            this.pendingFrame = null;
        }

        if(this.eglBase != null && this.eglBase.hasSurface()) {
            Object var3 = this.fpsReductionLock;
            boolean shouldRenderFrame;
            synchronized(this.fpsReductionLock) {
                if(this.minRenderPeriodNs == 9223372036854775807L) {
                    shouldRenderFrame = false;
                } else if(this.minRenderPeriodNs <= 0L) {
                    shouldRenderFrame = true;
                } else {
                    long currentTimeNs = System.nanoTime();
                    if(currentTimeNs < this.nextFrameTimeNs) {
                        this.logD("Skipping frame rendering - fps reduction is active.");
                        shouldRenderFrame = false;
                    } else {
                        this.nextFrameTimeNs += this.minRenderPeriodNs;
                        this.nextFrameTimeNs = Math.max(this.nextFrameTimeNs, currentTimeNs);
                        shouldRenderFrame = true;
                    }
                }
            }

            long startTimeNs = System.nanoTime();
            float frameAspectRatio = (float)frame.getRotatedWidth() / (float)frame.getRotatedHeight();
            Object var7 = this.layoutLock;
            float drawnAspectRatio;
            synchronized(this.layoutLock) {
                drawnAspectRatio = this.layoutAspectRatio != 0.0F?this.layoutAspectRatio:frameAspectRatio;
            }

            float scaleY;
            float scaleX;
            if(frameAspectRatio > drawnAspectRatio) {
                scaleX = drawnAspectRatio / frameAspectRatio;
                scaleY = 1.0F;
            } else {
                scaleX = 1.0F;
                scaleY = frameAspectRatio / drawnAspectRatio;
            }

            this.drawMatrix.reset();
            this.drawMatrix.preTranslate(0.5F, 0.5F);
            if(this.mirror) {
                this.drawMatrix.preScale(-1.0F, 1.0F);
            }

            this.drawMatrix.preScale(scaleX, scaleY);
            this.drawMatrix.preTranslate(-0.5F, -0.5F);
            if(shouldRenderFrame) {
                GLES20.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                GLES20.glClear(16384);
                this.frameDrawer.drawFrame(frame, this.drawer, this.drawMatrix, 0, 0, this.eglBase.surfaceWidth(), this.eglBase.surfaceHeight());
                long swapBuffersStartTimeNs = System.nanoTime();
                this.eglBase.swapBuffers();
                long currentTimeNs = System.nanoTime();
                Object var13 = this.statisticsLock;
                synchronized(this.statisticsLock) {
                    ++this.framesRendered;
                    this.renderTimeNs += currentTimeNs - startTimeNs;
                    this.renderSwapBufferTimeNs += currentTimeNs - swapBuffersStartTimeNs;
                }
            }

            this.notifyCallbacks(frame, shouldRenderFrame);
            frame.release();
        } else {
            this.logD("Dropping frame - No surface");
            frame.release();
        }
    }

    private void notifyCallbacks(VideoFrame frame, boolean wasRendered) {
        if(!this.frameListeners.isEmpty()) {
            this.drawMatrix.reset();
            this.drawMatrix.preTranslate(0.5F, 0.5F);
            if(this.mirror) {
                this.drawMatrix.preScale(-1.0F, 1.0F);
            }

            this.drawMatrix.preScale(1.0F, -1.0F);
            this.drawMatrix.preTranslate(-0.5F, -0.5F);
            Iterator it = this.frameListeners.iterator();

            while(true) {
                while(true) {
                    PanoRender.FrameListenerAndParams listenerAndParams;
                    do {
                        if(!it.hasNext()) {
                            return;
                        }

                        listenerAndParams = (PanoRender.FrameListenerAndParams)it.next();
                    } while(!wasRendered && listenerAndParams.applyFpsReduction);

                    it.remove();
                    int scaledWidth = (int)(listenerAndParams.scale * (float)frame.getRotatedWidth());
                    int scaledHeight = (int)(listenerAndParams.scale * (float)frame.getRotatedHeight());
                    if(scaledWidth != 0 && scaledHeight != 0) {
                        if(this.bitmapTextureFramebuffer == null) {
                            this.bitmapTextureFramebuffer = new GlTextureFrameBuffer(6408);
                        }

                        this.bitmapTextureFramebuffer.setSize(scaledWidth, scaledHeight);
                        GLES20.glBindFramebuffer('赀', this.bitmapTextureFramebuffer.getFrameBufferId());
                        GLES20.glFramebufferTexture2D('赀', '賠', 3553, this.bitmapTextureFramebuffer.getTextureId(), 0);
                        GLES20.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                        GLES20.glClear(16384);
                        this.frameDrawer.drawFrame(frame, listenerAndParams.drawer, this.drawMatrix, 0, 0, scaledWidth, scaledHeight);
                        ByteBuffer bitmapBuffer = ByteBuffer.allocateDirect(scaledWidth * scaledHeight * 4);
                        GLES20.glViewport(0, 0, scaledWidth, scaledHeight);
                        GLES20.glReadPixels(0, 0, scaledWidth, scaledHeight, 6408, 5121, bitmapBuffer);
                        GLES20.glBindFramebuffer('赀', 0);
                        GlUtil.checkNoGLES2Error("PanoRender.notifyCallbacks");
                        Bitmap bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Config.ARGB_8888);
                        bitmap.copyPixelsFromBuffer(bitmapBuffer);
                        listenerAndParams.listener.onFrame(bitmap);
                    } else {
                        listenerAndParams.listener.onFrame((Bitmap)null);
                    }
                }
            }
        }
    }

    private String averageTimeAsString(long sumTimeNs, int count) {
        return count <= 0?"NA":TimeUnit.NANOSECONDS.toMicros(sumTimeNs / (long)count) + " μs";
    }

    private void logStatistics() {
        long currentTimeNs = System.nanoTime();
        Object var3 = this.statisticsLock;
        synchronized(this.statisticsLock) {
            long elapsedTimeNs = currentTimeNs - this.statisticsStartTimeNs;
            if(elapsedTimeNs > 0L) {
                float renderFps = (float)((long)this.framesRendered * TimeUnit.SECONDS.toNanos(1L)) / (float)elapsedTimeNs;
                this.logD("Duration: " + TimeUnit.NANOSECONDS.toMillis(elapsedTimeNs) + " ms. Frames received: " + this.framesReceived + ". Dropped: " + this.framesDropped + ". Rendered: " + this.framesRendered + ". Render fps: " + String.format(Locale.US, "%.1f", new Object[]{Float.valueOf(renderFps)}) + ". Average render time: " + this.averageTimeAsString(this.renderTimeNs, this.framesRendered) + ". Average swapBuffer time: " + this.averageTimeAsString(this.renderSwapBufferTimeNs, this.framesRendered) + ".");
                this.resetStatistics(currentTimeNs);
            }
        }
    }

    private void logD(String string) {
        Logging.d("PanoRender", this.name + string);
    }

    private class PanoSurfaceCreation implements Runnable {
        private Object surface;

        private PanoSurfaceCreation() {
        }

        public synchronized void setSurface(Object surface) {
            this.surface = surface;
        }

        public synchronized void run() {
            if(this.surface != null && PanoRender.this.eglBase != null && !PanoRender.this.eglBase.hasSurface()) {
                if(this.surface instanceof Surface) {
                    PanoRender.this.eglBase.createSurface((Surface)this.surface);
                } else {
                    if(!(this.surface instanceof SurfaceTexture)) {
                        throw new IllegalStateException("Invalid surface: " + this.surface);
                    }

                    PanoRender.this.eglBase.createSurface((SurfaceTexture)this.surface);
                }

                PanoRender.this.eglBase.makeCurrent();
                GLES20.glPixelStorei(3317, 1);
            }

        }
    }

    private static class FrameListenerAndParams {
        public final PanoRender.FrameListener listener;
        public final float scale;
        public final GlDrawer drawer;
        public final boolean applyFpsReduction;

        public FrameListenerAndParams(PanoRender.FrameListener listener, float scale, GlDrawer drawer, boolean applyFpsReduction) {
            this.listener = listener;
            this.scale = scale;
            this.drawer = drawer;
            this.applyFpsReduction = applyFpsReduction;
        }
    }

    public interface FrameListener {
        void onFrame(Bitmap var1);
    }
}
