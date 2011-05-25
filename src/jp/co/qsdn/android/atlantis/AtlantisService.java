package jp.co.qsdn.android.atlantis;

import android.service.wallpaper.WallpaperService;

import android.util.Log;

import android.view.SurfaceHolder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import jp.co.qsdn.android.atlantis.GLRenderer;




public class AtlantisService extends WallpaperService {
  private static final String TAG = AtlantisService.class.getName();
  private class AtlantisEngine extends Engine {
    private GLRenderer glRenderer;
    private GL10 gl10;
    private EGL10 egl10;
    private EGLContext eglContext;
    private EGLDisplay eglDisplay;
    private EGLSurface eglSurface;
    private ExecutorService executor;
    private Runnable drawCommand = null;
    
    @Override
    public void onCreate(final SurfaceHolder holder) {
      Log.d(TAG, "start onCreate()");
      super.onCreate(holder);

      executor = Executors.newSingleThreadExecutor();

      drawCommand = new Runnable() {
        public void run() {
          glRenderer.onDrawFrame(gl10);
          egl10.eglSwapBuffers(eglDisplay, eglSurface);
          if (isVisible() && egl10.eglGetError() != EGL11.EGL_CONTEXT_LOST) {
            executor.execute(drawCommand);
          }
        }
      };
      Log.d(TAG, "end onCreate()");
    }

    @Override
    public void onDestroy() {
      Log.d(TAG, "start onDestroy()");
      executor.shutdownNow();
      super.onDestroy();
      Log.d(TAG, "end onDestroy()");
    }

    @Override
    public void onSurfaceCreated(final SurfaceHolder holder) {
      Log.d(TAG, "start onSurfaceCreated()");
      super.onSurfaceCreated(holder);
      Runnable surfaceCreatedCommand = new Runnable() {
        @Override
        public void run() {
          /* OpenGLの初期化 */
          egl10 = (EGL10) EGLContext.getEGL();
          eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
          int[] version = new int[2];
          egl10.eglInitialize(eglDisplay, version);
          int[] configSpec = { EGL10.EGL_RED_SIZE, 4,
                               EGL10.EGL_GREEN_SIZE, 4,
                               EGL10.EGL_BLUE_SIZE,4,
                               EGL10.EGL_DEPTH_SIZE, 16,
                               EGL10.EGL_NONE };

          EGLConfig[] configs = new EGLConfig[1];
          int[] numConfig = new int[1];

          /*-----------------------------------------------------------------*/
          /* 条件に見合うEGLConfigを取得                                     */
          /*-----------------------------------------------------------------*/
          egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, numConfig);
          /*-----------------------------------------------------------------*/
          /* もしEGLConfigが取得できなければ                                 */
          /*-----------------------------------------------------------------*/
          if (numConfig[0] == 0) {
            Log.d(TAG, "numConfig[0]=" + numConfig[0] + "");
            /* どうすっかね・・・ */
          }

          EGLConfig config = configs[0];

          /*-----------------------------------------------------------------*/
          /* 取得したEGLDisplayとEGLConfigでEGLContext作成                   */
          /*-----------------------------------------------------------------*/
          eglContext = egl10.eglCreateContext(eglDisplay, config, EGL10.EGL_NO_CONTEXT, null); 
          /*-----------------------------------------------------------------*/
          /* 取得したEGLDisplayとEGLConfigでEGLSurface作成                   */
          /*-----------------------------------------------------------------*/
          eglSurface = egl10.eglCreateWindowSurface(eglDisplay, config, holder, null);
          /*-----------------------------------------------------------------*/
          /* EGLContextとEGLSurfaceを関連付ける(アタッチ)                    */
          /*-----------------------------------------------------------------*/
          egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

          gl10 = (GL10) (eglContext.getGL());
 
          /*-----------------------------------------------------------------*/
          /* Rendererの初期化                                                */
          /*-----------------------------------------------------------------*/
          glRenderer = new GLRenderer(AtlantisService.this);
          glRenderer.onSurfaceCreated(gl10, config);
        }
      };
      executor.execute(surfaceCreatedCommand);
      Log.d(TAG, "end onSurfaceCreated()");
    }

    @Override
    public void onSurfaceDestroyed(final SurfaceHolder holder) {
      Log.d(TAG, "start onSurfaceDestroyed()");
      Runnable surfaceDestroyedCommand = new Runnable() {
        public void run() {
          egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE,
                                           EGL10.EGL_NO_SURFACE,
                                           EGL10.EGL_NO_CONTEXT);
          egl10.eglDestroySurface(eglDisplay, eglSurface);
          egl10.eglDestroyContext(eglDisplay, eglContext);
          egl10.eglTerminate(eglDisplay);
        }
      };
      executor.execute(surfaceDestroyedCommand);
      super.onSurfaceDestroyed(holder);
      Log.d(TAG, "end onSurfaceDestroyed()");
    }

    @Override
    public void onSurfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
      Log.d(TAG, "start onSurfaceChanged()");
      super.onSurfaceChanged(holder, format, width, height);
      Runnable surfaceChangedCommand = new Runnable() {
        public void run() {
          glRenderer.onSurfaceChanged(gl10, width, height);
        };
      };
      executor.execute(surfaceChangedCommand);
      Log.d(TAG, "end onSurfaceChanged()");
    }
 
    @Override
    public void onVisibilityChanged(final boolean visible) {
      Log.d(TAG, "start onVisibilityChanged()");
      super.onVisibilityChanged(visible);
      /* サーフェスが見えるようになったよ！ */
      if (visible && drawCommand != null) {
        executor.execute(drawCommand);
      }
      Log.d(TAG, "end onVisibilityChanged()");
    }

    @Override
    public void onOffsetsChanged(final float xOffset, final float yOffset,
                                 final float xOffsetStep, final float yOffsetStep,
                                 final int xPixelOffset, final int yPixelOffset) {
      Log.d(TAG, "start onOffsetsChanged()");
      Log.d(TAG, 
          "xOffset:[" + xOffset + "]:"
        + "yOffset:[" + yOffset + "]:"
        + "xOffsetStep:[" + xOffsetStep + "]:"
        + "yOffsetStep:[" + yOffsetStep + "]:"
        + "xPixelOffset:[" + xPixelOffset + "]:"
        + "yPixelOffset:[" + yPixelOffset + "]:");
      super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
      Runnable offsetsChangedCommand = new Runnable() {
        public void run() {
          glRenderer.onOffsetsChanged(gl10, xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
        };
      };
      executor.execute(offsetsChangedCommand);
      Log.d(TAG, "end onOffsetChanged()");
    }
  }
  @Override
  public Engine onCreateEngine() {
    Log.d(TAG, "start onCreateEngine()");
    Log.d(TAG, "end onCreateEngine()");
    return new AtlantisEngine();
  }
}
