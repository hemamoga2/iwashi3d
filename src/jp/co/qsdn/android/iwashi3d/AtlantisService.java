package jp.co.qsdn.android.iwashi3d;

import android.content.Context;

import android.os.Bundle;

import android.service.wallpaper.WallpaperService;

import android.util.Log;

import android.view.SurfaceHolder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import jp.co.qsdn.android.iwashi3d.GLRenderer;
import jp.co.qsdn.android.iwashi3d.util.MatrixTrackingGL;

import android.content.Intent;


public class AtlantisService extends WallpaperService {
  private static final String TAG = AtlantisService.class.getName();
  private static final boolean _debug = true;
  private static final int RETRY_COUNT = 3;

  private class AtlantisEngine extends Engine {
    private final String TAG = AtlantisEngine.class.getName();
    private int width = 0;
    private int height = 0;
    private boolean binded = false;
    private boolean mInitialized = false;

    /* EGL関連は毎回再作成？？ */
    private MatrixTrackingGL gl10 = null;
    private EGL10 egl10 = null;
    private EGLContext eglContext = null;
    private EGLDisplay eglDisplay = null;
    private EGLSurface eglSurface = null;
    private GLRenderer glRenderer = null;

    private ExecutorService getExecutor() {
      if (executor == null) {
        executor = Executors.newSingleThreadExecutor();
      }
      return executor;
    }
    private ExecutorService executor = null;
    private Runnable drawCommand = null;

    
    @Override
    public void onCreate(final SurfaceHolder holder) {
      if (_debug) Log.d(TAG, "start onCreate() [" + this + "]");
      super.onCreate(holder);
      /*=====================================================================*/
      /* 携帯電話として機能しなくなるので                                    */
      /* タッチイベントは無効にしておく.                                     */
      /* 画面の空いたところのタッチにだけ反応したいので                      */
      /* Engine.onCommandで対応する                                          */
      /*=====================================================================*/
      setTouchEventsEnabled(false);

      if (! isPreview()) {
        AtlantisNotification.putNotice(AtlantisService.this);
      }
      if (_debug) Log.d(TAG, "end onCreate() [" + this + "]");
    }

    @Override
    public void onDestroy() {
      if (_debug) Log.d(TAG, "start onDestroy() [" + this + "]");
      if (! isPreview()) {
        AtlantisNotification.removeNotice(getApplicationContext());
      }
      else {
      }
      super.onDestroy();
      System.gc();
      if (_debug) Log.d(TAG, "end onDestroy() [" + this + "]");
    }

    @Override
    public void onSurfaceCreated(final SurfaceHolder holder) {
      if (_debug) Log.d(TAG, "start onSurfaceCreated() [" + this + "]");
      super.onSurfaceCreated(holder);
      Runnable surfaceCreatedCommand = new Runnable() {
        @Override
        public void run() {
          if (mInitialized) {
            Log.d(TAG, "already Initialized(surfaceCreatedCommand)");
            return;
          }
          boolean ret;
          /* OpenGLの初期化 */
          int counter = 0;
          while(true) {
            Log.d(TAG, "start EGLContext.getEGL()");
            exitEgl();
            egl10 = (EGL10) EGLContext.getEGL();
            Log.d(TAG, "end EGLContext.getEGL()");
            Log.d(TAG, "start eglGetDisplay");
            eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            Log.d(TAG, "end eglGetDisplay");
            if (eglDisplay == null || EGL10.EGL_NO_DISPLAY.equals(eglDisplay)) {
              Log.d(TAG, "eglGetDisplayがEGL_NO_DISPLAY [" + AtlantisService.getErrorString(egl10.eglGetError()) + "]");
              exitEgl();
              if (++counter >= AtlantisService.RETRY_COUNT) {
                Log.e(TAG, "egl10.eglCreateContextがEGL_NO_DISPLAY");
                throw new RuntimeException("OpenGL Error(EGL_NO_DISPLAY)");
              }     
              Log.d(TAG, "RETRY");
              System.gc();
              waitNano();
              continue;
            }
            {
              egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            }
            int[] version = new int[2];
            if (! egl10.eglInitialize(eglDisplay, version)) {
              Log.d(TAG, "egl10.eglInitializeがfalse [" + AtlantisService.getErrorString(egl10.eglGetError()) + "]");
              exitEgl();
              if (++counter >= AtlantisService.RETRY_COUNT) {
                Log.e(TAG,"egl10.eglInitializeがfalse");
                throw new RuntimeException("OpenGL Error(eglInitialize)");
              }
              Log.d(TAG,"RETRY");
              System.gc();
              waitNano();
              continue;
            }
            int[] configSpec = { EGL10.EGL_RED_SIZE, 4,
                                 EGL10.EGL_GREEN_SIZE, 4,
                                 EGL10.EGL_BLUE_SIZE,4,
                                 EGL10.EGL_DEPTH_SIZE, 16,
                                 EGL10.EGL_STENCIL_SIZE, 1,
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
              if (_debug) Log.d(TAG, "numConfig[0]=" + numConfig[0] + "");
              /* どうすっかね・・・ */
              Log.e(TAG,"eglChooseConfig失敗:"
                 + "numConfig:[" + numConfig[0] + "]");
              exitEgl();
              throw new RuntimeException("OpenGL Error");
            }

            EGLConfig config = configs[0];

            /*-----------------------------------------------------------------*/
            /* 取得したEGLDisplayとEGLConfigでEGLContext作成                   */
            /*-----------------------------------------------------------------*/
            eglContext = egl10.eglCreateContext(eglDisplay, config, EGL10.EGL_NO_CONTEXT, null); 
            if (eglContext == null || EGL10.EGL_NO_CONTEXT.equals(eglContext)) {
              Log.d(TAG, "egl10.eglCreateContext == EGL_NO_CONTEXT [" + AtlantisService.getErrorString(egl10.eglGetError()) + "]");
              exitEgl();
              if (++counter >= AtlantisService.RETRY_COUNT) {
                Log.e(TAG, "egl10.eglCreateContextがEGL_NO_CONTEXT");
                throw new RuntimeException("OpenGL Error(EGL_NO_CONTEXT)");
              }
              Log.d(TAG, "RETRY");
              System.gc();
              waitNano();
              continue;
            }
            Log.d(TAG, "eglCreateContext done.");
            /*-----------------------------------------------------------------*/
            /* 取得したEGLDisplayとEGLConfigでEGLSurface作成                   */
            /*-----------------------------------------------------------------*/
            eglSurface = egl10.eglCreateWindowSurface(eglDisplay, config, holder, null);
            if (eglSurface == null || EGL10.EGL_NO_SURFACE.equals(eglSurface)) {
              Log.d(TAG, "egl10.eglCreateWindowSurface == EGL_NO_SURFACE [" + AtlantisService.getErrorString(egl10.eglGetError()) + "]");
              exitEgl();
              if (++counter >= AtlantisService.RETRY_COUNT) {
                Log.e(TAG, "egl10.eglCreateWindowSurfaceがEGL_NO_SURFACE");
                throw new RuntimeException("OpenGL Error(EGL_NO_SURFACE)");
              }
              Log.e(TAG, "RETRY");
              System.gc();
              waitNano();
              continue;
            }
            Log.d(TAG, "eglCreateWindowSurface done.");
            /*-----------------------------------------------------------------*/
            /* EGLContextとEGLSurfaceを関連付ける(アタッチ)                    */
            /*-----------------------------------------------------------------*/
            if (! egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
              Log.d(TAG, "egl10.eglMakeCurrent == false [" + AtlantisService.getErrorString(egl10.eglGetError()) + "]");
              exitEgl();
              if (++counter >= AtlantisService.RETRY_COUNT) {
                Log.e(TAG,"egl10.eglMakeCurrentがfalse");
                throw new RuntimeException("OpenGL Error(eglMakeCurrent)");
              }
              Log.d(TAG,"RETRY");
              System.gc();
              waitNano();
              continue;
            }
            
            Log.d(TAG, "eglMakeCurrent done.");
            Log.d(TAG, "now create gl10 object");
  
            gl10 = new MatrixTrackingGL((GL10) (eglContext.getGL()));
   
            /*-----------------------------------------------------------------*/
            /* Rendererの初期化                                                */
            /*-----------------------------------------------------------------*/
            glRenderer = GLRenderer.getInstance(getApplicationContext());
            synchronized (glRenderer) {
              glRenderer.onSurfaceCreated(gl10, config, getApplicationContext());
            }
            Log.d(TAG, "EGL initalize done.");
            mInitialized = true;
            if (drawCommand == null) {
              drawCommand = new Runnable() {
                public void run() {
                  if (mInitialized && glRenderer != null && gl10 != null) {
                    synchronized (glRenderer) {
                      glRenderer.onDrawFrame(gl10);
                    }
                    egl10.eglSwapBuffers(eglDisplay, eglSurface);
                    if (!getExecutor().isShutdown() && isVisible() && egl10.eglGetError() != EGL11.EGL_CONTEXT_LOST) {
                      getExecutor().execute(drawCommand);
                    }
                  }
                }
              };
              getExecutor().execute(drawCommand);
            }
            break;
          }
        }
      };
      getExecutor().execute(surfaceCreatedCommand);
      if (_debug) Log.d(TAG, "end onSurfaceCreated() [" + this + "]");
    }

    @Override
    public void onSurfaceDestroyed(final SurfaceHolder holder) {
      if (_debug) Log.d(TAG, "start onSurfaceDestroyed() [" + this + "]");
      Runnable surfaceDestroyedCommand = new Runnable() {
        @Override
        public void run() {
          synchronized (glRenderer) {
            glRenderer.onSurfaceDestroyed(gl10);
          }
          exitEgl();
          gl10.shutdown();
          gl10 = null;
          System.gc();
          mInitialized = false;
        }
      };
      getExecutor().execute(surfaceDestroyedCommand);
      getExecutor().shutdown();
      try {
        if (!getExecutor().awaitTermination(60, TimeUnit.SECONDS)) {
          getExecutor().shutdownNow();
          if (!getExecutor().awaitTermination(60, TimeUnit.SECONDS)) {
            Log.d(TAG,"ExecutorService did not terminate....");
            getExecutor().shutdownNow();
            Thread.currentThread().interrupt();
          }
        }
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
      drawCommand = null;
      executor = null;
      super.onSurfaceDestroyed(holder);
      if (_debug) Log.d(TAG, "end onSurfaceDestroyed() [" + this + "]");
    }

    @Override
    public void onSurfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
      if (_debug) Log.d(TAG, "start onSurfaceChanged() [" + this + "]");
      super.onSurfaceChanged(holder, format, width, height);
      this.width = width;
      this.height = height;
      Runnable surfaceChangedCommand = new Runnable() {
        public void run() {
          if (glRenderer != null && gl10 != null && mInitialized) {
            synchronized (glRenderer) {
              glRenderer.onSurfaceChanged(gl10, width, height);
            }
          }
        };
      };
      getExecutor().execute(surfaceChangedCommand);
      if (_debug) Log.d(TAG, "end onSurfaceChanged() [" + this + "]");
    }
 
    @Override
    public void onVisibilityChanged(final boolean visible) {
      if (_debug) Log.d(TAG, "start onVisibilityChanged()");
      super.onVisibilityChanged(visible);
      /* サーフェスが見えるようになったよ！ */
      if (visible && drawCommand != null && mInitialized) {
        /* 設定変更のタイミング */
        if (glRenderer != null) {
          synchronized (glRenderer) {
            glRenderer.updateSetting(getApplicationContext());
          }
        }
        getExecutor().execute(drawCommand);
      }
      if (_debug) Log.d(TAG, "end onVisibilityChanged()");
    }

    @Override
    public void onOffsetsChanged(final float xOffset, final float yOffset,
                                 final float xOffsetStep, final float yOffsetStep,
                                 final int xPixelOffset, final int yPixelOffset) {
      if (_debug) Log.d(TAG, "start onOffsetsChanged()");
      super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
      if (xOffsetStep == 0.0f && yOffsetStep == 0.0f) {
        if (_debug) Log.d(TAG, "end onOffsetChanged() no execute");
        return;
      }
      Runnable offsetsChangedCommand = new Runnable() {
        public void run() {
          if (mInitialized && glRenderer != null && gl10 != null) {
            synchronized (glRenderer) {
              glRenderer.onOffsetsChanged(gl10, xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
            }
          }
        };
      };
      getExecutor().execute(offsetsChangedCommand);
      if (_debug) Log.d(TAG, "end onOffsetChanged()");
    }
    
    @Override  
    public Bundle onCommand(final String action, final int x, final int y, final int z, final Bundle extras, final boolean resultRequested){
      if (_debug) {
        Log.d(TAG, "start onCommand "
          + "action:[" + action + "]:"
          + "x:[" + x + "]:"
          + "y:[" + y + "]:"
          + "z:[" + z + "]:"
          + "extras:[" + extras + "]:"
          + "resultRequested:[" + resultRequested + "]:"
        );
      }
      /*=====================================================================*/
      /* 画面の何もないところへのタッチにだけ反応するため                    */
      /* actionがandroid.wallpaper.tapのときだけ処理する                     */
      /*=====================================================================*/
      if (action.equals("android.wallpaper.tap")) {
         Runnable onCommandCommand = new Runnable() {
           public void run() {
             if (mInitialized && glRenderer != null && gl10 != null) {
               synchronized (glRenderer) {
                 glRenderer.onCommand(gl10, action, x, y, z, extras, resultRequested);
               }
             }
           }
         };
         getExecutor().execute(onCommandCommand);
      }  
      Bundle ret = super.onCommand(action, x, y, z, extras, resultRequested);
      if (_debug) Log.d(TAG, "end onCommand");
      return ret;
    }  
    public void exitEgl() {
      Log.d(TAG, "start exitEgl");
      if (egl10 != null) {
        if (eglDisplay != null && ! eglDisplay.equals(EGL10.EGL_NO_DISPLAY)) {
          if (! egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE,
                                           EGL10.EGL_NO_SURFACE,
                                           EGL10.EGL_NO_CONTEXT)) {
            Log.d(TAG, "eglMakeCurrentがfalse [" + AtlantisService.getErrorString(egl10.eglGetError()) + "]");
          }
       
          if (eglSurface != null && ! eglSurface.equals(EGL10.EGL_NO_SURFACE)) {
            if (! egl10.eglDestroySurface(eglDisplay, eglSurface)) {
              Log.d(TAG, "eglDestroySurfaceがfalse [" + AtlantisService.getErrorString(egl10.eglGetError()) + "]");
            }
            eglSurface = null;
          }
          if (eglContext != null && ! eglContext.equals(EGL10.EGL_NO_CONTEXT)) {
            if (! egl10.eglDestroyContext(eglDisplay, eglContext)) {
              Log.d(TAG, "eglDestroyContextがfalse [" + AtlantisService.getErrorString(egl10.eglGetError()) + "]");
            }
            eglContext = null;
          }
          if (! egl10.eglTerminate(eglDisplay)) {
            Log.d(TAG, "eglTerminateがfalse [" + AtlantisService.getErrorString(egl10.eglGetError()) + "]");
          }
          eglDisplay = null;
        }
        egl10 = null;
      }
      Log.d(TAG, "end exitEgl");
    }
  }
  @Override
  public Engine onCreateEngine() {
    if (_debug) Log.d(TAG, "start onCreateEngine()");
    AtlantisEngine engine = new AtlantisEngine();
    Log.d(TAG, "engine:[" + engine + "]");
    if (_debug) Log.d(TAG, "end onCreateEngine()");
    return engine;
  }

  public void waitNano() {
    Log.d(TAG, "start waitNano");
    try { 
      //TimeUnit.NANOSECONDS.sleep(5000);
      TimeUnit.SECONDS.sleep(5);
    } catch (InterruptedException e) {
    }
    Log.d(TAG, "end waitNano");
  }

  public static String getErrorString(int err) {
    switch (err) {
    case EGL10.EGL_NOT_INITIALIZED:
      return "EGL_NOT_INITIALIZED";
    case EGL10.EGL_BAD_ACCESS:
      return "EGL_BAD_ACCESS";
    case EGL10.EGL_BAD_ALLOC:
      return "EGL_BAD_ALLOC";
    case EGL10.EGL_BAD_ATTRIBUTE:
      return "EGL_BAD_ATTRIBUTE";
    case EGL10.EGL_BAD_CONTEXT:
      return "EGL_BAD_CONTEXT";
    case EGL10.EGL_BAD_CONFIG:
      return "EGL_BAD_CONFIG";
    case EGL10.EGL_BAD_CURRENT_SURFACE:
      return "EGL_BAD_CURRENT_SURFACE";
    case EGL10.EGL_BAD_DISPLAY:
      return "EGL_BAD_DISPLAY";
    case EGL10.EGL_BAD_SURFACE:
      return "EGL_BAD_SURFACE";
    case EGL10.EGL_BAD_MATCH:
      return "EGL_BAD_MATCH";
    case EGL10.EGL_BAD_PARAMETER:
      return "EGL_BAD_PARAMETER";
    case EGL10.EGL_BAD_NATIVE_PIXMAP:
      return "EGL_BAD_NATIVE_PIXMAP";
    case EGL10.EGL_BAD_NATIVE_WINDOW:
      return "EGL_BAD_NATIVE_WINDOW";
    case EGL11.EGL_CONTEXT_LOST:
      return "EGL_CONTEXT_LOST";
    default:
      return "OTHER";
    }
  }

}
