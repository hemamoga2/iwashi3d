package jp.co.qsdn.android.iwashi3d.model;

import android.content.Context;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.opengl.GLUtils;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import java.util.Random;

import javax.microedition.khronos.opengles.GL10;

import jp.co.qsdn.android.iwashi3d.Aquarium;
import jp.co.qsdn.android.iwashi3d.Bait;
import jp.co.qsdn.android.iwashi3d.BaitManager;
import jp.co.qsdn.android.iwashi3d.util.CoordUtil;

public class Iwashi implements Model {
  private static final boolean debug = false;
  private static final String TAG = Iwashi.class.getName();
  private static final long BASE_TICK = 17852783L;
  private static boolean mTextureLoaded = false;
  private final FloatBuffer mVertexBuffer;
  private final FloatBuffer mTextureBuffer;  
  private final FloatBuffer mNormalBuffer;  
  private long prevTime = 0;
  private long tick = 0;
  private float scale = 0.1035156288414f;
  private float center_xyz[] = {-0.185271816326531f, 0.344428326530612f, -0.00509786734693878f };
  private CoordUtil coordUtil = new CoordUtil();
  private long seed = 0;
  private BaitManager baitManager;
  private boolean enableBoids = true;
  public float[] distances = new float[100];
  private Random rand = null;
  private float size = 10f * scale * 0.7f;
  /*
   * 仲間、同種
   */
  private Iwashi[] species;
  private double separate_dist  = 5.0d * scale * 0.5d;
  private double alignment_dist = 25.0d * scale * 0.5d;
  private double school_dist    = 50.0d * scale * 0.5d;
  private double cohesion_dist  = 100.0d * scale * 0.5d;
  private float[] schoolCenter = {0f,0f,0f};
  private int schoolCount = 0;

  private enum STATUS {
    TO_CENTER, /* 画面の真ん中へ向かい中 */
    TO_BAIT,   /* 餌へ向かっている最中   */
    SEPARATE,  /* 近づき過ぎたので離れる */
    ALIGNMENT, /* 整列中 */
    COHESION,  /* 近づく */
    TO_SCHOOL_CENTER,   /* 群れの真ん中へ */
    NORMAL,    /* ランダム */
  };

  /** 現在の行動中の行動 */
  private STATUS status = STATUS.NORMAL;


  private int[] mScratch128i = new int[128];
  private float[] mScratch4f = new float[4];
  private float[] mScratch4f_1 = new float[4];
  private float[] mScratch4f_2 = new float[4];
  private Iwashi[] mScratch3Iwashi = new Iwashi[3];


  /*=========================================================================*/
  /* 現在位置                                                                */
  /*=========================================================================*/
  // メモ 1.0f >= z >= -50.0fまで
  // zが0.0fのときy=1.0fが限界
  // zが0.0fのときy=-1.0fは半分土に埋まっている
  // zが-20.0fのとき、x=-5.0f, x=5.0fで半分切れる
  //
  // 水槽の大きさ（案）
  // 10.0f >= x  >= -10.0f
  // 8.0f >= y >= 0.0f
  // -50.0f > z >= 0.0f
  private float[] position = { 0.0f, 1.0f, 0.0f };
  /*=========================================================================*/
  /* 向き                                                                    */
  /*=========================================================================*/
  private float[] direction = { -1.0f, 0.0f, 0.0f};

  /* 上下 */
  private float x_angle = 0;
  /* 左右 */
  private float y_angle = 0;
  /*=========================================================================*/
  /* スピード                                                                */
  /*=========================================================================*/
  private float speed = 0.020f * 0.5f;
  private float speed_unit = speed / 5f * 0.5f;
  private float speed_max = 0.050f * 0.5f;
  private float speed_min = speed_unit;
  private float cohesion_speed = speed * 2f * 0.5f;
  private float sv_speed = speed;

  private int iwashiNo = 0;

  public Iwashi(int ii) {

    ByteBuffer nbb = ByteBuffer.allocateDirect(IwashiData.normals.length * 4);
    nbb.order(ByteOrder.nativeOrder());
    mNormalBuffer = nbb.asFloatBuffer();
    mNormalBuffer.put(IwashiData.normals);
    mNormalBuffer.position(0);

    ByteBuffer tbb = ByteBuffer.allocateDirect(IwashiData.texCoords.length * 4);
    tbb.order(ByteOrder.nativeOrder());
    mTextureBuffer = tbb.asFloatBuffer();
    mTextureBuffer.put(IwashiData.texCoords);
    mTextureBuffer.position(0);

    ByteBuffer vbb = ByteBuffer.allocateDirect(IwashiData.vertices.length * 4);
    vbb.order(ByteOrder.nativeOrder());
    mVertexBuffer = vbb.asFloatBuffer();

    // 初期配置
    this.rand = new java.util.Random(System.nanoTime() + (ii * 500));
    this.seed = (long)(this.rand.nextFloat() * 5000f);
    position[0] = this.rand.nextFloat() * 8f - 4f;
    position[1] = this.rand.nextFloat() * 8f - 4f;
    position[2] = this.rand.nextFloat() * 4f - 2f;
    iwashiNo = ii;
  }

  protected static int[] textureIds = null;
  public static void loadTexture(GL10 gl10, Context context, int resource) {
    textureIds = new int[1];
    Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), resource);
    gl10.glGenTextures(1, textureIds, 0);
    gl10.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[0]);
    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bmp, 0);
    gl10.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
    gl10.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
    bmp.recycle();
    bmp = null;
    mTextureLoaded = true;
  }
  public static void deleteTexture(GL10 gl10) {
    if (textureIds != null) {
      gl10.glDeleteTextures(1, textureIds, 0);
    }
  }
  public static boolean isTextureLoaded() {
    return mTextureLoaded;
  }

  private float getMoveWidth(float x) {
    /*=======================================================================*/
    /* z = 1/3 * x^2 の2次関数から算出                                       */
    /*=======================================================================*/
    float xt = x / scale + center_xyz[0];
    return xt * xt / 20.0f - 0.4f;
  }


  private void animate() {
    long current = System.currentTimeMillis() + this.seed;
    /* 大体１秒間に2回ヒレを動かす */
    float nf = (float)((current / 100) % 10000);
    float s = (float)Math.sin((double)nf) * scale;
     
    //303 101 {4.725803, 1.603915, -0.000000}
    //309 103 {4.725803, 1.603915, -0.000000}
    synchronized (mScratch128i) {
//      int idx[] = { 101,103,};
      mScratch128i[0] = 101;
      mScratch128i[1] = 103;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<2; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }

    //300 100 {4.734376, 1.502248, -0.009085}
    //312 104 {4.727424, 1.502259, 0.009085}
    //1290 430 {4.727424, 1.502259, 0.009085}
    //1317 439 {4.734376, 1.502248, -0.009085}
    synchronized (mScratch128i) {
//      int idx[] = { 100,104,430,439,};
      mScratch128i[0] = 100;
      mScratch128i[1] = 104;
      mScratch128i[2] = 430;
      mScratch128i[3] = 439;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<4; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //IwashiData.vertices[2+3*100] = IwashiData.org_vertices[2+3*100] + (1.0f * s);
    //IwashiData.vertices[2+3*104] = IwashiData.org_vertices[2+3*104] + (1.0f * s);
    //IwashiData.vertices[2+3*430] = IwashiData.org_vertices[2+3*430] + (1.0f * s);
    //IwashiData.vertices[2+3*439] = IwashiData.org_vertices[2+3*439] + (1.0f * s);

    //318 106 {4.497553, 1.130905, 0.009254}
    //1293 431 {4.497553, 1.130905, 0.009254}
    //1299 433 {4.497553, 1.130905, 0.009254}
    synchronized (mScratch128i) {
//      int idx[] = { 106,431,433,};
      mScratch128i[0] = 106;
      mScratch128i[1] = 431;
      mScratch128i[2] = 433;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<3; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }

    // 096 032 {3.943874, 0.549283, 0.006373}
    // 102 034 {3.943874, 0.549283, 0.006373}
    // 132 044 {3.931480, 0.549297, -0.006373}
    // 138 046 {3.931480, 0.549297, -0.006373}
    // 285 095 {3.943874, 0.549283, 0.006373}
    // 288 096 {3.943874, 0.549283, 0.006373}
    // 321 107 {3.931480, 0.549297, -0.006373}
    // 324 108 {3.931480, 0.549297, -0.006373}
    synchronized (mScratch128i) {
      //int idx[] = { 32,34,44,46,95,96,107,108,};
      mScratch128i[0] = 32;
      mScratch128i[1] = 34;
      mScratch128i[2] = 44;
      mScratch128i[3] = 46;
      mScratch128i[4] = 95;
      mScratch128i[5] = 96;
      mScratch128i[6] = 107;
      mScratch128i[7] = 108;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<8; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
 
    // 264 088 {4.587202, 0.163779, 0.009247}
    // 276 092 {4.597796, 0.163766, -0.009247}
    // 282 094 {4.597796, 0.163766, -0.009247}
    // 327 109 {4.587202, 0.163779, 0.009247}
    synchronized (mScratch128i) {
      //int idx[] = { 88,92,94,109,};
      mScratch128i[0] = 88;
      mScratch128i[1] = 92;
      mScratch128i[2] = 94;
      mScratch128i[3] = 109;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<4; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    // 267 089 {4.865566, -0.206893, 0.009037}
    // 273 091 {4.871437, -0.206896, -0.009037}
    //1329 443 {4.871437, -0.206896, -0.009037}
    //1335 445 {4.871437, -0.206896, -0.009037}
    //1344 448 {4.865566, -0.206893, 0.009037}
    //1350 450 {4.865566, -0.206893, 0.009037}
    synchronized (mScratch128i) {
      //int idx[] = { 89,91,443,445,448,450,};
      mScratch128i[0] = 89;
      mScratch128i[1] = 91;
      mScratch128i[2] = 443;
      mScratch128i[3] = 445;
      mScratch128i[4] = 448;
      mScratch128i[5] = 450;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<6; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //291 097 {4.508326, 1.130889, -0.009254}
    //1308 436 {4.508326, 1.130889, -0.009254}
    //1314 438 {4.508326, 1.130889, -0.009254}
    synchronized (mScratch128i) {
      //int idx[] = { 97,436,438,};
      mScratch128i[0] = 97;
      mScratch128i[1] = 436;
      mScratch128i[2] = 438;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<3; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //1326 442 {4.868408, -0.319613, -0.000000}
    //1353 451 {4.868408, -0.319613, -0.000000}
    synchronized (mScratch128i) {
      //int idx[] = { 442,451,};
      mScratch128i[0] = 442;
      mScratch128i[1] = 451;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<2; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    // 231 077 {4.189324, -0.027536, -0.000000}
    // 237 079 {4.189324, -0.027536, -0.000000}
    //1323 441 {4.189324, -0.027536, -0.000000}
    //1332 444 {4.189324, -0.027536, -0.000000}
    //1347 449 {4.189324, -0.027536, -0.000000}
    //1356 452 {4.189324, -0.027536, -0.000000}
    synchronized (mScratch128i) {
      //int idx[] = { 77,79,441,444,449,452,};
      mScratch128i[0] = 77;
      mScratch128i[1] = 79;
      mScratch128i[2] = 441;
      mScratch128i[3] = 444;
      mScratch128i[4] = 449;
      mScratch128i[5] = 452;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<6; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //084 028 {3.994344, 0.212614, -0.011905}
    //093 031 {3.994344, 0.212614, -0.011905}
    //141 047 {3.985378, 0.212621, -0.040541}
    //150 050 {3.985378, 0.212621, -0.040541}
    //228 076 {3.985378, 0.212621, -0.040541}
    //240 080 {3.994344, 0.212614, -0.011905}
    //261 087 {3.985378, 0.212621, -0.040541}
    //270 090 {3.994344, 0.212614, -0.011905}
    //279 093 {3.994344, 0.212614, -0.011905}
    //330 110 {3.985378, 0.212621, -0.040541}
    //1338 446 {3.994344, 0.212614, -0.011905}
    //1341 447 {3.985378, 0.212621, -0.040541}
    synchronized (mScratch128i) {
      //int idx[] = { 28,31,47,50,76,80,87,90,93,110,446,447,};
      mScratch128i[0] = 28;
      mScratch128i[1] = 31;
      mScratch128i[2] = 47;
      mScratch128i[3] = 50;
      mScratch128i[4] = 76;
      mScratch128i[5] = 80;
      mScratch128i[6] = 87;
      mScratch128i[7] = 90;
      mScratch128i[8] = 93;
      mScratch128i[9] = 110;
      mScratch128i[10] = 446;
      mScratch128i[11] = 447;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<12; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //105 035 {4.001855, 0.959487, -0.012866}
    //111 037 {4.001855, 0.959487, -0.012866}
    //246 082 {4.001855, 0.959487, -0.012866}
    //294 098 {4.001855, 0.959487, -0.012866}
    //1305 435 {4.001855, 0.959487, -0.012866}
    // XXXX
    //120 040 {3.992240, 0.959496, -0.039771}
    //129 043 {3.992240, 0.959496, -0.039771}
    //258 086 {3.992240, 0.959496, -0.039771}
    //315 105 {3.992240, 0.959496, -0.039771}
    //1302 434 {3.992240, 0.959496, -0.039771}
    synchronized (mScratch128i) {
      //int idx[] = { 35,37,82,98,435,40,43,86,105,434,};
      mScratch128i[0] = 35;
      mScratch128i[1] = 37;
      mScratch128i[2] = 82;
      mScratch128i[3] = 98;
      mScratch128i[4] = 435;
      mScratch128i[5] = 40;
      mScratch128i[6] = 43;
      mScratch128i[7] = 86;
      mScratch128i[8] = 105;
      mScratch128i[9] = 434;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<10; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    // 249 083 {4.250497, 1.351480, -0.030413}
    // 255 085 {4.250497, 1.351480, -0.030413}
    // 297 099 {4.250497, 1.351480, -0.030413}
    // 306 102 {4.250497, 1.351480, -0.030413}
    //1287 429 {4.250497, 1.351480, -0.030413}
    //1296 432 {4.250497, 1.351480, -0.030413}
    //1311 437 {4.250497, 1.351480, -0.030413}
    //1320 440 {4.250497, 1.351480, -0.030413}
    synchronized (mScratch128i) {
      //int idx[] = { 83,85,99,102,429,432,437,440,};
      mScratch128i[0] = 83;
      mScratch128i[1] = 85;
      mScratch128i[2] = 99;
      mScratch128i[3] = 102;
      mScratch128i[4] = 429;
      mScratch128i[5] = 432;
      mScratch128i[6] = 437;
      mScratch128i[7] = 440;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<8; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //114 038 {3.393267, 0.860405, -0.028042}
    //117 039 {3.393267, 0.860405, -0.028042}
    //243 081 {3.393267, 0.860405, -0.028042}
    //252 084 {3.393267, 0.860405, -0.028042}
    //705 235 {3.393267, 0.860405, -0.028042}
    //714 238 {3.393267, 0.860405, -0.028042}
    synchronized (mScratch128i) {
      //int idx[] = { 38,39,81,84,235,238, };
      mScratch128i[0] = 38;
      mScratch128i[1] = 39;
      mScratch128i[2] = 81;
      mScratch128i[3] = 84;
      mScratch128i[4] = 235;
      mScratch128i[5] = 238;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<6; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //081 027 {3.465865, 0.220323, -0.023851}
    //144 048 {3.465865, 0.220323, -0.023851}
    //225 075 {3.465865, 0.220323, -0.023851}
    //234 078 {3.465865, 0.220323, -0.023851}
    //660 220 {3.465865, 0.220323, -0.023851}
    //690 230 {3.465865, 0.220323, -0.023851}
    //696 232 {3.465865, 0.220323, -0.023851}
    //720 240 {3.465865, 0.220323, -0.023851}
    synchronized (mScratch128i) {
      //int idx[] = { 27,48,75,78,220,230,232,240,};
      mScratch128i[0] = 27;
      mScratch128i[1] = 48;
      mScratch128i[2] = 75;
      mScratch128i[3] = 78;
      mScratch128i[4] = 220;
      mScratch128i[5] = 230;
      mScratch128i[6] = 232;
      mScratch128i[7] = 240;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<8; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //663 221 {3.128526, 0.180488, -0.023306}
    //669 223 {3.128526, 0.180488, -0.023306}
    //678 226 {3.128526, 0.180488, -0.023306}
    //687 229 {3.128526, 0.180488, -0.023306}
    synchronized (mScratch128i) {
      //int idx[] = { 221,223,226,229,};
      mScratch128i[0] = 221;
      mScratch128i[1] = 223;
      mScratch128i[2] = 226;
      mScratch128i[3] = 229;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<4; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //087 029 {2.908598, 0.545923, 0.068958}
    //090 030 {2.908598, 0.545923, 0.068958}
    //099 033 {2.908598, 0.545923, 0.068958}
    //108 036 {2.908598, 0.545923, 0.068958}
    //123 041 {2.897367, 0.545929, -0.111540}
    //126 042 {2.897367, 0.545929, -0.111540}
    //135 045 {2.897367, 0.545929, -0.111540}
    //147 049 {2.897367, 0.545929, -0.111540}
    //177 059 {2.908598, 0.545923, 0.068958}
    //183 061 {2.908598, 0.545923, 0.068958}
    //192 064 {2.908598, 0.545923, 0.068958}
    //201 067 {2.897367, 0.545929, -0.111540}
    //210 070 {2.897367, 0.545929, -0.111540}
    //222 074 {2.897367, 0.545929, -0.111540}
    //627 209 {2.908598, 0.545923, 0.068958}
    //633 211 {2.908598, 0.545923, 0.068958}
    //645 215 {2.897367, 0.545929, -0.111540}
    //654 218 {2.897367, 0.545929, -0.111540}
    //699 233 {2.908598, 0.545923, 0.068958}
    //702 234 {2.908598, 0.545923, 0.068958}
    //717 239 {2.897367, 0.545929, -0.111540}
    //726 242 {2.897367, 0.545929, -0.111540}
    //1371 457 {2.897367, 0.545929, -0.111540}
    //1380 460 {2.908598, 0.545923, 0.068958}
    synchronized (mScratch128i) {
      //int idx[] = { 29,30,33,36,41,42,45,49,59,61,64,67,70,74,209,211,215,218,233,234,239,242,457,460,};
      mScratch128i[0] = 29;
      mScratch128i[1] = 30;
      mScratch128i[2] = 33;
      mScratch128i[3] = 36;
      mScratch128i[4] = 41;
      mScratch128i[5] = 42;
      mScratch128i[6] = 45;
      mScratch128i[7] = 49;
      mScratch128i[8] = 59;
      mScratch128i[9] = 61;
      mScratch128i[10] = 64;
      mScratch128i[11] = 67;
      mScratch128i[12] = 70;
      mScratch128i[13] = 74;
      mScratch128i[14] = 209;
      mScratch128i[15] = 211;
      mScratch128i[16] = 215;
      mScratch128i[17] = 218;
      mScratch128i[18] = 233;
      mScratch128i[19] = 234;
      mScratch128i[20] = 239;
      mScratch128i[21] = 242;
      mScratch128i[22] = 457;
      mScratch128i[23] = 460;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<24; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //672 224 {2.755704, 0.041151, -0.025086}
    //675 225 {2.755704, 0.041151, -0.025086}
    //1182 394 {2.755704, 0.041151, -0.025086}
    //1188 396 {2.755704, 0.041151, -0.025086}
    //1203 401 {2.755704, 0.041151, -0.025086}
    //1209 403 {2.755704, 0.041151, -0.025086}
    synchronized (mScratch128i) {
      //int idx[] = { 224,225,394,396,401,403,};
      mScratch128i[0] = 224;
      mScratch128i[1] = 225;
      mScratch128i[2] = 394;
      mScratch128i[3] = 396;
      mScratch128i[4] = 401;
      mScratch128i[5] = 403;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<6; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    // 606 202 {2.601744, 0.072730, -0.082255}
    // 615 205 {2.608089, 0.072728, 0.042083}
    // 624 208 {2.608089, 0.072728, 0.042083}
    // 648 216 {2.601744, 0.072730, -0.082255}
    // 657 219 {2.601744, 0.072730, -0.082255}
    // 666 222 {2.601744, 0.072730, -0.082255}
    // 681 227 {2.608089, 0.072728, 0.042083}
    // 684 228 {2.608089, 0.072728, 0.042083}
    // 693 231 {2.608089, 0.072728, 0.042083}
    // 723 241 {2.601744, 0.072730, -0.082255}
    //1191 397 {2.608089, 0.072728, 0.042083}
    //1200 400 {2.601744, 0.072730, -0.082255}
    synchronized (mScratch128i) {
      //int idx[] = { 202,205,208,216,219,222,227,228,231,241,397,400,};
      mScratch128i[0] = 202;
      mScratch128i[1] = 205;
      mScratch128i[2] = 208;
      mScratch128i[3] = 216;
      mScratch128i[4] = 219;
      mScratch128i[5] = 222;
      mScratch128i[6] = 227;
      mScratch128i[7] = 228;
      mScratch128i[8] = 231;
      mScratch128i[9] = 241;
      mScratch128i[10] = 397;
      mScratch128i[11] = 400;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<12; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //636 212 {2.606399, 0.965839, -0.022280}
    //642 214 {2.606399, 0.965839, -0.022280}
    //708 236 {2.606399, 0.965839, -0.022280}
    //711 237 {2.606399, 0.965839, -0.022280}
    synchronized (mScratch128i) {
      //int idx[] = { 212,214,236,237,};
      mScratch128i[0] = 212;
      mScratch128i[1] = 214;
      mScratch128i[2] = 236;
      mScratch128i[3] = 237;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<4; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //174 058 {1.993230, -0.000729, 0.124182}
    //216 072 {1.985328, -0.000726, -0.159362}
    //561 187 {1.990646, 1.132275, -0.019784}
    //570 190 {1.990646, 1.132275, -0.019784}
    //603 201 {1.985328, -0.000726, -0.159362}
    //618 206 {1.993230, -0.000729, 0.124182}
    //621 207 {1.993230, -0.000729, 0.124182}
    //630 210 {1.990646, 1.132275, -0.019784}
    //639 213 {1.990646, 1.132275, -0.019784}
    //651 217 {1.985328, -0.000726, -0.159362}
    //1179 393 {1.954150, -0.416138, -0.022541}
    //1212 404 {1.954150, -0.416138, -0.022541}
    //1362 454 {1.990646, 1.132275, -0.019784}
    //1368 456 {1.990646, 1.132275, -0.019784}
    //1383 461 {1.990646, 1.132275, -0.019784}
    //1389 463 {1.990646, 1.132275, -0.019784}
    //1401 467 {1.993230, -0.000729, 0.124182}
    //1407 469 {1.993230, -0.000729, 0.124182}
    //1416 472 {1.985328, -0.000726, -0.159362}
    //1422 474 {1.985328, -0.000726, -0.159362}
    synchronized (mScratch128i) {
      //int idx[] = { 58,72,187,190,201,206,207,210,213,217,393,404,454,456,461,463,467,469,472,474, };
      mScratch128i[0]  = 58;
      mScratch128i[1]  = 72;
      mScratch128i[2]  = 187;
      mScratch128i[3]  = 190;
      mScratch128i[4]  = 201;
      mScratch128i[5]  = 206;
      mScratch128i[6]  = 207;
      mScratch128i[7]  = 210;
      mScratch128i[8]  = 213;
      mScratch128i[9]  = 217;
      mScratch128i[10] = 393;
      mScratch128i[11] = 404;
      mScratch128i[12] = 454;
      mScratch128i[13] = 456;
      mScratch128i[14] = 461;
      mScratch128i[15] = 463;
      mScratch128i[16] = 467;
      mScratch128i[17] = 469;
      mScratch128i[18] = 472;
      mScratch128i[19] = 474;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<20; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //609 203 {1.841455, -0.150632, -0.019362}
    //612 204 {1.841455, -0.150632, -0.019362}
    //1185 395 {1.841455, -0.150632, -0.019362}
    //1194 398 {1.841455, -0.150632, -0.019362}
    //1197 399 {1.841455, -0.150632, -0.019362}
    //1206 402 {1.841455, -0.150632, -0.019362}
    //1398 466 {1.841455, -0.150632, -0.019362}
    //1425 475 {1.841455, -0.150632, -0.019362}
    synchronized (mScratch128i) {
      //int idx[] = { 203,204,395,398,399,402,466,475, }; 
      mScratch128i[0] = 203;
      mScratch128i[1] = 204;
      mScratch128i[2] = 395;
      mScratch128i[3] = 398;
      mScratch128i[4] = 399;
      mScratch128i[5] = 402;
      mScratch128i[6] = 466;
      mScratch128i[7] = 475;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<8; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //1218 406 {0.956889, -0.352683, -0.017794}
    //1224 408 {0.956889, -0.352683, -0.017794}
    //1239 413 {0.956889, -0.352683, -0.017794}
    //1245 415 {0.956889, -0.352683, -0.017794}
    //1395 465 {0.956889, -0.352683, -0.017794}
    //1404 468 {0.956889, -0.352683, -0.017794}
    //1419 473 {0.956889, -0.352683, -0.017794}
    //1428 476 {0.956889, -0.352683, -0.017794}
    synchronized (mScratch128i) {
      //int idx[] = { 406,408,413,415,465,468,473,476, };
      mScratch128i[0] = 406;
      mScratch128i[1] = 408;
      mScratch128i[2] = 413;
      mScratch128i[3] = 415;
      mScratch128i[4] = 465;
      mScratch128i[5] = 468;
      mScratch128i[6] = 473;
      mScratch128i[7] = 476;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<8; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //171 057 {0.581339, -0.149219, 0.291680}
    //180 060 {0.581339, -0.149219, 0.291680}
    //186 062 {0.583216, 0.232926, 0.394389}
    //189 063 {0.583216, 0.232926, 0.394389}
    //195 065 {0.583626, 0.694177, 0.392306}
    //198 066 {0.571708, 0.694188, -0.416034}
    //204 068 {0.571238, 0.232938, -0.418118}
    //207 069 {0.571238, 0.232938, -0.418118}
    //213 071 {0.572380, -0.149212, -0.315408}
    //219 073 {0.572380, -0.149212, -0.315408}
    //558 186 {0.581885, 1.091331, 0.247236}
    //573 191 {0.574212, 1.091338, -0.270963}
    //576 192 {0.581885, 1.091331, 0.247236}
    //600 200 {0.574212, 1.091338, -0.270963}
    //930 310 {0.571238, 0.232938, -0.418118}
    //933 311 {0.572380, -0.149212, -0.315408}
    //939 313 {0.572380, -0.149212, -0.315408}
    //948 316 {0.571708, 0.694188, -0.416034}
    //951 317 {0.571238, 0.232938, -0.418118}
    //957 319 {0.571238, 0.232938, -0.418118}
    //966 322 {0.574212, 1.091338, -0.270963}
    //969 323 {0.571708, 0.694188, -0.416034}
    //975 325 {0.571708, 0.694188, -0.416034}
    //993 331 {0.574212, 1.091338, -0.270963}
    //1002 334 {0.581885, 1.091331, 0.247236}
    //1020 340 {0.583626, 0.694177, 0.392306}
    //1026 342 {0.583626, 0.694177, 0.392306}
    //1029 343 {0.581885, 1.091331, 0.247236}
    //1038 346 {0.583216, 0.232926, 0.394389}
    //1044 348 {0.583216, 0.232926, 0.394389}
    //1047 349 {0.583626, 0.694177, 0.392306}
    //1056 352 {0.581339, -0.149219, 0.291680}
    //1062 354 {0.581339, -0.149219, 0.291680}
    //1065 355 {0.583216, 0.232926, 0.394389}
    //1077 359 {0.581339, -0.149219, 0.291680}
    //1083 361 {0.581339, -0.149219, 0.291680}
    //1164 388 {0.572380, -0.149212, -0.315408}
    //1170 390 {0.572380, -0.149212, -0.315408}
    //1227 409 {0.581339, -0.149219, 0.291680}
    //1236 412 {0.572380, -0.149212, -0.315408}
    //1359 453 {0.574212, 1.091338, -0.270963}
    //1365 455 {0.571708, 0.694188, -0.416034}
    //1374 458 {0.571708, 0.694188, -0.416034}
    //1377 459 {0.583626, 0.694177, 0.392306}
    //1386 462 {0.583626, 0.694177, 0.392306}
    //1392 464 {0.581885, 1.091331, 0.247236}
    //1410 470 {0.581339, -0.149219, 0.291680}
    //1413 471 {0.572380, -0.149212, -0.315408}
    synchronized (mScratch128i) {
//      int idx[] = { 57, 60, 62, 63, 65, 66, 68, 69, 71, 73, 186, 191, 192, 200, 310, 
//                    311, 313, 316, 317, 319, 322, 323, 325, 331, 334, 340, 342, 343, 
//                    346, 348, 349, 352, 354, 355, 359, 361, 388, 390, 409, 412, 453, 
//                    455, 458, 459, 462, 464, 470, 471, };
      mScratch128i[0] = 57;
      mScratch128i[1] = 60;
      mScratch128i[2] = 62;
      mScratch128i[3] = 63;
      mScratch128i[4] = 65;
      mScratch128i[5] = 66;
      mScratch128i[6] = 68;
      mScratch128i[7] = 69;
      mScratch128i[8] = 71;
      mScratch128i[9] = 73;
      mScratch128i[10] = 186;
      mScratch128i[11] = 191;
      mScratch128i[12] = 192;
      mScratch128i[13] = 200;
      mScratch128i[14] = 310;
      mScratch128i[15] = 311;
      mScratch128i[16] = 313;
      mScratch128i[17] = 316;
      mScratch128i[18] = 317;
      mScratch128i[19] = 319;
      mScratch128i[20] = 322;
      mScratch128i[21] = 323;
      mScratch128i[22] = 325;
      mScratch128i[23] = 331;
      mScratch128i[24] = 334;
      mScratch128i[25] = 340;
      mScratch128i[26] = 342;
      mScratch128i[27] = 343;
      mScratch128i[28] = 346;
      mScratch128i[29] = 348;
      mScratch128i[30] = 349;
      mScratch128i[31] = 352;
      mScratch128i[32] = 354;
      mScratch128i[33] = 355;
      mScratch128i[34] = 359;
      mScratch128i[35] = 361;
      mScratch128i[36] = 388;
      mScratch128i[37] = 390;
      mScratch128i[38] = 409;
      mScratch128i[39] = 412;
      mScratch128i[40] = 453;
      mScratch128i[41] = 455;
      mScratch128i[42] = 458;
      mScratch128i[43] = 459;
      mScratch128i[44] = 462;
      mScratch128i[45] = 464;
      mScratch128i[46] = 470;
      mScratch128i[47] = 471;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<48; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //1095 365 {0.389382, -0.656846, 0.166823}
    //1101 367 {0.389382, -0.656846, 0.166823}
    //1149 383 {0.384593, -0.656843, -0.186195}
    //1155 385 {0.384593, -0.656843, -0.186195}
    synchronized (mScratch128i) {
      //int idx[] = { 365,367,383,385,};
      mScratch128i[0] = 365;
      mScratch128i[1] = 367;
      mScratch128i[2] = 383;
      mScratch128i[3] = 385;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<4; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //564 188 {0.354908, 1.325953, -0.010446}
    //567 189 {0.354908, 1.325953, -0.010446}
    //579 193 {0.354908, 1.325953, -0.010446}
    //588 196 {0.354908, 1.325953, -0.010446}
    //597 199 {0.354908, 1.325953, -0.010446}
    synchronized (mScratch128i) {
      //int idx[] = { 188,189,193,196,199,};
      mScratch128i[0] = 188;
      mScratch128i[1] = 189;
      mScratch128i[2] = 193;
      mScratch128i[3] = 196;
      mScratch128i[4] = 199;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<5; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //591 197 {0.288338, 1.460179, -0.019098}
    synchronized (mScratch128i) {
      //int idx[] = { 197, };
      mScratch128i[0] = 197;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<1; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //1074 358 {0.199635, -0.364357, 0.152904}
    //1092 364 {0.199635, -0.364357, 0.152904}
    //1119 373 {0.199635, -0.364357, 0.152904}
    //1128 376 {0.193223, -0.364353, -0.172992}
    //1146 382 {0.193223, -0.364353, -0.172992}
    //1173 391 {0.193223, -0.364353, -0.172992}
    //1221 407 {0.199635, -0.364357, 0.152904}
    //1230 410 {0.199635, -0.364357, 0.152904}
    //1233 411 {0.193223, -0.364353, -0.172992}
    //1242 414 {0.193223, -0.364353, -0.172992}
    synchronized (mScratch128i) {
      //int idx[] = { 358,364,373,376,382,391,407,410,411,414, };
      mScratch128i[0] = 358;
      mScratch128i[1] = 364;
      mScratch128i[2] = 373;
      mScratch128i[3] = 376;
      mScratch128i[4] = 382;
      mScratch128i[5] = 391;
      mScratch128i[6] = 407;
      mScratch128i[7] = 410;
      mScratch128i[8] = 411;
      mScratch128i[9] = 414;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<10; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //1110 370 {0.157074, -0.503665, -0.017842}
    //1116 372 {0.157074, -0.503665, -0.017842}
    //1131 377 {0.157074, -0.503665, -0.017842}
    //1137 379 {0.157074, -0.503665, -0.017842}
    //1215 405 {0.157074, -0.503665, -0.017842}
    //1248 416 {0.157074, -0.503665, -0.017842}
    synchronized (mScratch128i) {
      //int idx[] = { 370,372,377,379,405,416, };
      mScratch128i[0] = 370;
      mScratch128i[1] = 372;
      mScratch128i[2] = 377;
      mScratch128i[3] = 379;
      mScratch128i[4] = 405;
      mScratch128i[5] = 416;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii; 
      for (ii=0; ii<6; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //1104 368 {0.139649, -0.683434, 0.172252}
    //1158 386 {0.134860, -0.683430, -0.190388}
    synchronized (mScratch128i) {
      //int idx[] = { 368,386,};
      mScratch128i[0] = 368;
      mScratch128i[1] = 386;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<2; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //552 184 {-0.187596, 1.774881, -0.017535}
    //735 245 {-0.187596, 1.774881, -0.017535}
    synchronized (mScratch128i) {
      //int idx[] = { 184,245,};
      mScratch128i[0] = 184;
      mScratch128i[1] = 245;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<2; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //516 172 {-0.267770, -0.458273, -0.190623}
    //525 175 {-0.267770, -0.458273, -0.190623}
    //528 176 {-0.265469, -0.539220, -0.017226}
    //534 178 {-0.265469, -0.539220, -0.017226}
    //537 179 {-0.263027, -0.458277, 0.173962}
    //543 181 {-0.263027, -0.458277, 0.173962}
    //1071 357 {-0.263027, -0.458277, 0.173962}
    //1080 360 {-0.263027, -0.458277, 0.173962}
    //1089 363 {-0.263027, -0.458277, 0.173962}
    //1098 366 {-0.263027, -0.458277, 0.173962}
    //1107 369 {-0.265469, -0.539220, -0.017226}
    //1113 371 {-0.263027, -0.458277, 0.173962}
    //1122 374 {-0.263027, -0.458277, 0.173962}
    //1125 375 {-0.267770, -0.458273, -0.190623}
    //1134 378 {-0.267770, -0.458273, -0.190623}
    //1140 380 {-0.265469, -0.539220, -0.017226}
    //1143 381 {-0.267770, -0.458273, -0.190623}
    //1152 384 {-0.267770, -0.458273, -0.190623}
    //1167 389 {-0.267770, -0.458273, -0.190623}
    //1176 392 {-0.267770, -0.458273, -0.190623}
    synchronized (mScratch128i) {
      //int idx[] = { 172,175,176,178,179,181,357,360,363,366,369,371,374,375,378,380,381,384,389,392, };
      mScratch128i[0] = 172;
      mScratch128i[1] = 175;
      mScratch128i[2] = 176;
      mScratch128i[3] = 178;
      mScratch128i[4] = 179;
      mScratch128i[5] = 181;
      mScratch128i[6] = 357;
      mScratch128i[7] = 360;
      mScratch128i[8] = 363;
      mScratch128i[9] = 366;
      mScratch128i[10] = 369;
      mScratch128i[11] = 371;
      mScratch128i[12] = 374;
      mScratch128i[13] = 375;
      mScratch128i[14] = 378;
      mScratch128i[15] = 380;
      mScratch128i[16] = 381;
      mScratch128i[17] = 384;
      mScratch128i[18] = 389;
      mScratch128i[19] = 392;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<20; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //555 185 {-0.302373, 2.225360, -0.016608}
    synchronized (mScratch128i) {
      //int idx[] = { 185, };
      mScratch128i[0] = 185;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<1; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //156 052 {-1.178311, 1.362515, -0.010047}
    //165 055 {-1.178311, 1.362515, -0.010047}
    //549 183 {-1.178311, 1.362515, -0.010047}
    //729 243 {-1.178311, 1.362515, -0.010047}
    //981 327 {-1.178311, 1.362515, -0.010047}
    //1014 338 {-1.178311, 1.362515, -0.010047}
    synchronized (mScratch128i) {
      //int idx[] = { 52,55,183,243,327,338, };
      mScratch128i[0] = 52;
      mScratch128i[1] = 55;
      mScratch128i[2] = 183;
      mScratch128i[3] = 243;
      mScratch128i[4] = 327;
      mScratch128i[5] = 338;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<6; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //153 051 {-1.440711, 1.066319, 0.368437}
    //168 056 {-1.449087, 1.066327, -0.375763}
    //513 171 {-1.450822, -0.196619, -0.432703}
    //546 182 {-1.441195, -0.196627, 0.425376}
    //741 247 {-1.450822, -0.196619, -0.432703}
    //771 257 {-1.441195, -0.196627, 0.425376}
    //777 259 {-1.439693, 0.174198, 0.532284}
    //786 262 {-1.439693, 0.174198, 0.532284}
    //789 263 {-1.439283, 0.675618, 0.529550}
    //795 265 {-1.440711, 1.066319, 0.368437}
    //804 268 {-1.449087, 1.066327, -0.375763}
    //813 271 {-1.451202, 0.675629, -0.536876}
    //816 272 {-1.451672, 0.174209, -0.539610}
    //822 274 {-1.451672, 0.174209, -0.539610}
    //858 286 {-1.451672, 0.174209, -0.539610}
    //864 288 {-1.451672, 0.174209, -0.539610}
    //867 289 {-1.450822, -0.196619, -0.432703}
    //876 292 {-1.449087, 1.066327, -0.375763}
    //879 293 {-1.451202, 0.675629, -0.536876}
    //885 295 {-1.451202, 0.675629, -0.536876}
    //894 298 {-1.439283, 0.675618, 0.529550}
    //900 300 {-1.439283, 0.675618, 0.529550}
    //903 301 {-1.440711, 1.066319, 0.368437}
    //912 304 {-1.441195, -0.196627, 0.425376}
    //915 305 {-1.439693, 0.174198, 0.532284}
    //921 307 {-1.439693, 0.174198, 0.532284}
    //927 309 {-1.451672, 0.174209, -0.539610}
    //936 312 {-1.451672, 0.174209, -0.539610}
    //942 314 {-1.450822, -0.196619, -0.432703}
    //945 315 {-1.451202, 0.675629, -0.536876}
    //954 318 {-1.451202, 0.675629, -0.536876}
    //960 320 {-1.451672, 0.174209, -0.539610}
    //963 321 {-1.449087, 1.066327, -0.375763}
    //972 324 {-1.449087, 1.066327, -0.375763}
    //978 326 {-1.451202, 0.675629, -0.536876}
    //987 329 {-1.449087, 1.066327, -0.375763}
    //996 332 {-1.449087, 1.066327, -0.375763}
    //999 333 {-1.440711, 1.066319, 0.368437}
    //1008 336 {-1.440711, 1.066319, 0.368437}
    //1017 339 {-1.439283, 0.675618, 0.529550}
    //1023 341 {-1.440711, 1.066319, 0.368437}
    //1032 344 {-1.440711, 1.066319, 0.368437}
    //1035 345 {-1.439693, 0.174198, 0.532284}
    //1041 347 {-1.439283, 0.675618, 0.529550}
    //1050 350 {-1.439283, 0.675618, 0.529550}
    //1053 351 {-1.441195, -0.196627, 0.425376}
    //1059 353 {-1.439693, 0.174198, 0.532284}
    //1068 356 {-1.439693, 0.174198, 0.532284}
    //1086 362 {-1.441195, -0.196627, 0.425376}
    //1161 387 {-1.450822, -0.196619, -0.432703}
    synchronized (mScratch128i) {
      mScratch128i[0] = 51;
      mScratch128i[1] = 56;
      mScratch128i[2] = 171;
      mScratch128i[3] = 182;
      mScratch128i[4] = 247;
      mScratch128i[5] = 257;
      mScratch128i[6] = 259;
      mScratch128i[7] = 262;
      mScratch128i[8] = 263;
      mScratch128i[9] = 265;
      mScratch128i[10] = 268;
      mScratch128i[11] = 271;
      mScratch128i[12] = 272;
      mScratch128i[13] = 274;
      mScratch128i[14] = 286;
      mScratch128i[15] = 288;
      mScratch128i[16] = 289;
      mScratch128i[17] = 292;
      mScratch128i[18] = 293;
      mScratch128i[19] = 295;
      mScratch128i[20] = 298;
      mScratch128i[21] = 300;
      mScratch128i[22] = 301;
      mScratch128i[23] = 304;
      mScratch128i[24] = 305;
      mScratch128i[25] = 307;
      mScratch128i[26] = 309;
      mScratch128i[27] = 312;
      mScratch128i[28] = 314;
      mScratch128i[29] = 315;
      mScratch128i[30] = 318;
      mScratch128i[31] = 320;
      mScratch128i[32] = 321;
      mScratch128i[33] = 324;
      mScratch128i[34] = 326;
      mScratch128i[35] = 329;
      mScratch128i[36] = 332;
      mScratch128i[37] = 333;
      mScratch128i[38] = 336;
      mScratch128i[39] = 339;
      mScratch128i[40] = 341;
      mScratch128i[41] = 344;
      mScratch128i[42] = 345;
      mScratch128i[43] = 347;
      mScratch128i[44] = 350;
      mScratch128i[45] = 351;
      mScratch128i[46] = 353;
      mScratch128i[47] = 356;
      mScratch128i[48] = 362;
      mScratch128i[49] = 387;
//      int idx[] = { 51,56,171,182,247,257,259,262,263,265,268,271,272,274,286,288,289,292,293,
//                    295,298,300,301,304,305,307,309,312,314,315,318,320,321,324,326,329,332,333,
//                    336,339,341,344,345,347,350,351,353,356,362,387, };
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<50; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //519 173 {-1.599839, -0.511679, -0.007429}
    //522 174 {-1.599839, -0.511679, -0.007429}
    //531 177 {-1.599839, -0.511679, -0.007429}
    //540 180 {-1.599839, -0.511679, -0.007429}
    //744 248 {-1.599839, -0.511679, -0.007429}
    //750 250 {-1.599839, -0.511679, -0.007429}
    //759 253 {-1.599839, -0.511679, -0.007429}
    //768 256 {-1.599839, -0.511679, -0.007429}
    synchronized (mScratch128i) {
      //int idx[] = { 173,174,177,180,248,250,253,256, };
      mScratch128i[0] = 173;
      mScratch128i[1] = 174;
      mScratch128i[2] = 177;
      mScratch128i[3] = 180;
      mScratch128i[4] = 248;
      mScratch128i[5] = 250;
      mScratch128i[6] = 253;
      mScratch128i[7] = 256;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<8; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //840 280 {-2.228655, -0.728475, -0.650837}
    //849 283 {-2.215483, -0.728483, 0.657006}
    synchronized (mScratch128i) {
      //int idx[] = { 280,283,};
      mScratch128i[0] = 280;
      mScratch128i[1] = 283;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<2; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //843 281 {-2.823029, -0.686539, -0.662640}
    //852 284 {-2.811438, -0.686546, 0.670077}
    synchronized (mScratch128i) {
      //int idx[] = { 281,284,};
      mScratch128i[0] = 281;
      mScratch128i[1] = 284;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<2; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //024 008 {-3.196742, -0.049302, 0.447789}
    //762 254 {-3.196742, -0.049302, 0.447789}
    //765 255 {-3.196742, -0.049302, 0.447789}
    //846 282 {-3.196742, -0.049302, 0.447789}
    //909 303 {-3.196742, -0.049302, 0.447789}
    //918 306 {-3.196742, -0.049302, 0.447789}
    synchronized (mScratch128i) {
      //int idx[] = { 8,254,255,282,303,306, };
      mScratch128i[0] = 8;
      mScratch128i[1] = 254;
      mScratch128i[2] = 255;
      mScratch128i[3] = 282;
      mScratch128i[4] = 303;
      mScratch128i[5] = 306;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<6; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //030 010 {-3.354060, 0.467534, 0.517028}
    //039 013 {-3.354060, 0.467534, 0.517028}
    //048 016 {-3.354060, 0.467534, 0.517028}
    //051 017 {-3.355715, 0.978260, 0.327890}
    //057 019 {-3.363465, 0.978265, -0.319692}
    //060 020 {-3.366217, 0.467542, -0.508830}
    //066 022 {-3.366217, 0.467542, -0.508830}
    //075 025 {-3.366217, 0.467542, -0.508830}
    //780 260 {-3.354060, 0.467534, 0.517028}
    //783 261 {-3.354060, 0.467534, 0.517028}
    //792 264 {-3.355715, 0.978260, 0.327890}
    //807 269 {-3.363465, 0.978265, -0.319692}
    //810 270 {-3.366217, 0.467542, -0.508830}
    //819 273 {-3.366217, 0.467542, -0.508830}
    //873 291 {-3.363465, 0.978265, -0.319692}
    //882 294 {-3.363465, 0.978265, -0.319692}
    //888 296 {-3.366217, 0.467542, -0.508830}
    //891 297 {-3.354060, 0.467534, 0.517028}
    //897 299 {-3.355715, 0.978260, 0.327890}
    //906 302 {-3.355715, 0.978260, 0.327890}
    //1437 479 {-3.363465, 0.978265, -0.319692}
    //1443 481 {-3.363465, 0.978265, -0.319692}
    //1452 484 {-3.355715, 0.978260, 0.327890}
    //1458 486 {-3.355715, 0.978260, 0.327890}
    synchronized (mScratch128i) {
      //int idx[] = { 10,13,16,17,19,20,22,25,260,261,264,269,270,273,291,294,296,297,299,302,479,481,484,486, };
      mScratch128i[0] = 10;
      mScratch128i[1] = 13;
      mScratch128i[2] = 16;
      mScratch128i[3] = 17;
      mScratch128i[4] = 19;
      mScratch128i[5] = 20;
      mScratch128i[6] = 22;
      mScratch128i[7] = 25;
      mScratch128i[8] = 260;
      mScratch128i[9] = 261;
      mScratch128i[10] = 264;
      mScratch128i[11] = 269;
      mScratch128i[12] = 270;
      mScratch128i[13] = 273;
      mScratch128i[14] = 291;
      mScratch128i[15] = 294;
      mScratch128i[16] = 296;
      mScratch128i[17] = 297;
      mScratch128i[18] = 299;
      mScratch128i[19] = 302;
      mScratch128i[20] = 479;
      mScratch128i[21] = 481;
      mScratch128i[22] = 484;
      mScratch128i[23] = 486;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<24; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //003 001 {-3.482355, -0.314469, 0.028982}
    //012 004 {-3.482355, -0.314469, 0.028982}
    //021 007 {-3.482355, -0.314469, 0.028982}
    //753 251 {-3.482355, -0.314469, 0.028982}
    //756 252 {-3.482355, -0.314469, 0.028982}
    //834 278 {-3.482355, -0.314469, 0.028982}
    synchronized (mScratch128i) {
      //int idx[] = { 1,4,7,251,252,278,};
      mScratch128i[0] = 1;
      mScratch128i[1] = 4;
      mScratch128i[2] = 7;
      mScratch128i[3] = 251;
      mScratch128i[4] = 252;
      mScratch128i[5] = 278;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<6; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //000 000 {-3.563356, -0.050557, -0.371373}
    //015 005 {-3.518670, 0.077497, 0.434307}
    //018 006 {-3.518670, 0.077497, 0.434307}
    //027 009 {-3.518670, 0.077497, 0.434307}
    //078 026 {-3.563356, -0.050557, -0.371373}
    //159 053 {-3.592829, 1.130262, 0.000657}
    //162 054 {-3.592829, 1.130262, 0.000657}
    //462 154 {-3.563356, -0.050557, -0.371373}
    //474 158 {-3.518670, 0.077497, 0.434307}
    //480 160 {-3.518670, 0.077497, 0.434307}
    //507 169 {-3.563356, -0.050557, -0.371373}
    //774 258 {-3.518670, 0.077497, 0.434307}
    //798 266 {-3.592829, 1.130262, 0.000657}
    //801 267 {-3.592829, 1.130262, 0.000657}
    //825 275 {-3.563356, -0.050557, -0.371373}
    //828 276 {-3.563356, -0.050557, -0.371373}
    //855 285 {-3.563356, -0.050557, -0.371373}
    //924 308 {-3.518670, 0.077497, 0.434307}
    //1434 478 {-3.592829, 1.130262, 0.000657}
    //1461 487 {-3.592829, 1.130262, 0.000657}
    synchronized (mScratch128i) {
      //int idx[] = { 0,5,6,9,26,53,54,154,158,160,169,258,266,267,275,276,285,308,478,487, };
      mScratch128i[0] = 0;
      mScratch128i[1] = 5;
      mScratch128i[2] = 6;
      mScratch128i[3] = 9;
      mScratch128i[4] = 26;
      mScratch128i[5] = 53;
      mScratch128i[6] = 54;
      mScratch128i[7] = 154;
      mScratch128i[8] = 158;
      mScratch128i[9] = 160;
      mScratch128i[10] = 169;
      mScratch128i[11] = 258;
      mScratch128i[12] = 266;
      mScratch128i[13] = 267;
      mScratch128i[14] = 275;
      mScratch128i[15] = 276;
      mScratch128i[16] = 285;
      mScratch128i[17] = 308;
      mScratch128i[18] = 478;
      mScratch128i[19] = 487;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<20; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //489 163 {-4.281947, 0.878867, 0.003449}
    //498 166 {-4.281947, 0.878867, 0.003449}
    //1431 477 {-4.281947, 0.878867, 0.003449}
    //1440 480 {-4.281947, 0.878867, 0.003449}
    //1455 485 {-4.281947, 0.878867, 0.003449}
    //1464 488 {-4.281947, 0.878867, 0.003449}
    synchronized (mScratch128i) {
      //int idx[] = { 163,166,477,480,485,488, };
      mScratch128i[0] = 163;
      mScratch128i[1] = 166;
      mScratch128i[2] = 477;
      mScratch128i[3] = 480;
      mScratch128i[4] = 485;
      mScratch128i[5] = 488;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<6; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //006 002 {-4.318616, -0.053987, 0.004143}
    //009 003 {-4.318616, -0.053987, 0.004143}
    //033 011 {-4.382598, 0.380261, 0.198944}
    //036 012 {-4.382598, 0.380261, 0.198944}
    //042 014 {-4.345534, 0.664362, 0.218885}
    //045 015 {-4.345534, 0.664362, 0.218885}
    //054 018 {-4.349825, 0.664365, -0.202633}
    //063 021 {-4.349825, 0.664365, -0.202633}
    //069 023 {-4.387043, 0.380264, -0.181603}
    //072 024 {-4.387043, 0.380264, -0.181603}
    //348 116 {-4.318616, -0.053987, 0.004143}
    //354 118 {-4.318616, -0.053987, 0.004143}
    //381 127 {-4.382598, 0.380261, 0.198944}
    //390 130 {-4.382598, 0.380261, 0.198944}
    //393 131 {-4.345534, 0.664362, 0.218885}
    //399 133 {-4.345534, 0.664362, 0.218885}
    //408 136 {-4.345534, 0.664362, 0.218885}
    //420 140 {-4.349825, 0.664365, -0.202633}
    //426 142 {-4.349825, 0.664365, -0.202633}
    //435 145 {-4.349825, 0.664365, -0.202633}
    //438 146 {-4.387043, 0.380264, -0.181603}
    //444 148 {-4.387043, 0.380264, -0.181603}
    //465 155 {-4.318616, -0.053987, 0.004143}
    //471 157 {-4.318616, -0.053987, 0.004143}
    //483 161 {-4.382598, 0.380261, 0.198944}
    //486 162 {-4.345534, 0.664362, 0.218885}
    //501 167 {-4.349825, 0.664365, -0.202633}
    //504 168 {-4.387043, 0.380264, -0.181603}
    //1254 418 {-4.387043, 0.380264, -0.181603}
    //1281 427 {-4.382598, 0.380261, 0.198944}
    //1446 482 {-4.349825, 0.664365, -0.202633}
    //1449 483 {-4.345534, 0.664362, 0.218885}
    synchronized (mScratch128i) {
//      int idx[] = { 2,3,11,12,14,15,18,21,23,24,116,118,127,130,131,133,136,140,142,145,146,148,155,157,161,162,167,168,418,427,482,483, };
      mScratch128i[0] = 2;
      mScratch128i[1] = 3;
      mScratch128i[2] = 11;
      mScratch128i[3] = 12;
      mScratch128i[4] = 14;
      mScratch128i[5] = 15;
      mScratch128i[6] = 18;
      mScratch128i[7] = 21;
      mScratch128i[8] = 23;
      mScratch128i[9] = 24;
      mScratch128i[10] = 116;
      mScratch128i[11] = 118;
      mScratch128i[12] = 127;
      mScratch128i[13] = 130;
      mScratch128i[14] = 131;
      mScratch128i[15] = 133;
      mScratch128i[16] = 136;
      mScratch128i[17] = 140;
      mScratch128i[18] = 142;
      mScratch128i[19] = 145;
      mScratch128i[20] = 146;
      mScratch128i[21] = 148;
      mScratch128i[22] = 155;
      mScratch128i[23] = 157;
      mScratch128i[24] = 161;
      mScratch128i[25] = 162;
      mScratch128i[26] = 167;
      mScratch128i[27] = 168;
      mScratch128i[28] = 418;
      mScratch128i[29] = 427;
      mScratch128i[30] = 482;
      mScratch128i[31] = 483;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<32; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //336 112 {-4.535238, 0.159280, -0.085091}
    //345 115 {-4.535238, 0.159280, -0.085091}
    //357 119 {-4.532741, 0.159278, 0.102805}
    //366 122 {-4.532741, 0.159278, 0.102805}
    //372 124 {-4.532741, 0.159278, 0.102805}
    //378 126 {-4.523820, 0.323676, 0.001114}
    //411 137 {-4.568282, 0.724235, 0.008114}
    //417 139 {-4.568282, 0.724235, 0.008114}
    //447 149 {-4.525612, 0.323678, 0.018667}
    //450 150 {-4.535238, 0.159280, -0.085091}
    //459 153 {-4.535238, 0.159280, -0.085091}
    //468 156 {-4.532741, 0.159278, 0.102805}
    //477 159 {-4.532741, 0.159278, 0.102805}
    //492 164 {-4.568282, 0.724235, 0.008114}
    //495 165 {-4.568282, 0.724235, 0.008114}
    //510 170 {-4.535238, 0.159280, -0.085091}
    //1251 417 {-4.525612, 0.323678, 0.018667}
    //1257 419 {-4.535238, 0.159280, -0.085091}
    //1260 420 {-4.525612, 0.323678, 0.018667}
    //1263 421 {-4.535238, 0.159280, -0.085091}
    //1272 424 {-4.532741, 0.159278, 0.102805}
    //1275 425 {-4.523820, 0.323676, 0.001114}
    //1278 426 {-4.532741, 0.159278, 0.102805}
    //1284 428 {-4.523820, 0.323676, 0.001114}
    synchronized (mScratch128i) {
      //int idx[] = { 112,115,119,122,124,126,137,139,149,150,153,156,159,164,165,170,417,419,420,421,424,425,426,428, };
      mScratch128i[0] = 112;
      mScratch128i[1] = 115;
      mScratch128i[2] = 119;
      mScratch128i[3] = 122;
      mScratch128i[4] = 124;
      mScratch128i[5] = 126;
      mScratch128i[6] = 137;
      mScratch128i[7] = 139;
      mScratch128i[8] = 149;
      mScratch128i[9] = 150;
      mScratch128i[10] = 153;
      mScratch128i[11] = 156;
      mScratch128i[12] = 159;
      mScratch128i[13] = 164;
      mScratch128i[14] = 165;
      mScratch128i[15] = 170;
      mScratch128i[16] = 417;
      mScratch128i[17] = 419;
      mScratch128i[18] = 420;
      mScratch128i[19] = 421;
      mScratch128i[20] = 424;
      mScratch128i[21] = 425;
      mScratch128i[22] = 426;
      mScratch128i[23] = 428;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<24; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //339 113 {-4.638330, 0.149347, 0.007086}
    //342 114 {-4.638330, 0.149347, 0.007086}
    //351 117 {-4.638330, 0.149347, 0.007086}
    //363 121 {-4.638330, 0.149347, 0.007086}
    //384 128 {-4.654118, 0.449342, 0.001114}
    //387 129 {-4.654118, 0.449342, 0.001114}
    //396 132 {-4.654118, 0.449342, 0.001114}
    //429 143 {-4.655901, 0.449344, 0.018667}
    //432 144 {-4.655901, 0.449344, 0.018667}
    //441 147 {-4.655901, 0.449344, 0.018667}
    synchronized (mScratch128i) {
      //int idx[] = { 113,114,117,121,128,129,132,143,144,147, };
      mScratch128i[0] = 113;
      mScratch128i[1] = 114;
      mScratch128i[2] = 117;
      mScratch128i[3] = 121;
      mScratch128i[4] = 128;
      mScratch128i[5] = 129;
      mScratch128i[6] = 132;
      mScratch128i[7] = 143;
      mScratch128i[8] = 144;
      mScratch128i[9] = 147;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<10; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //333 111 {-4.788940, 0.265594, 0.009013}
    //360 120 {-4.788940, 0.265594, 0.009013}
    //369 123 {-4.788940, 0.265594, 0.009013}
    //375 125 {-4.718528, 0.351371, 0.001114}
    //402 134 {-4.784564, 0.579400, 0.009013}
    //405 135 {-4.784564, 0.579400, 0.009013}
    //414 138 {-4.784564, 0.579400, 0.009013}
    //423 141 {-4.784564, 0.579400, 0.009013}
    //453 151 {-4.788940, 0.265594, 0.009013}
    //456 152 {-4.720216, 0.351373, 0.018667}
    //1266 422 {-4.720216, 0.351373, 0.018667}
    //1269 423 {-4.718528, 0.351371, 0.001114}
    synchronized (mScratch128i) {
      //int idx[] = { 111,120,123,125,134,135,138,141,151,152,422,423, };
      mScratch128i[0] = 111;
      mScratch128i[1] = 120;
      mScratch128i[2] = 123;
      mScratch128i[3] = 125;
      mScratch128i[4] = 134;
      mScratch128i[5] = 135;
      mScratch128i[6] = 138;
      mScratch128i[7] = 141;
      mScratch128i[8] = 151;
      mScratch128i[9] = 152;
      mScratch128i[10] = 422;
      mScratch128i[11] = 423;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<12; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //738 246 {-3.207148, -0.049295, -0.440878}
    //747 249 {-3.207148, -0.049295, -0.440878}
    //831 277 {-3.207148, -0.049295, -0.440878}
    //837 279 {-3.207148, -0.049295, -0.440878}
    //861 287 {-3.207148, -0.049295, -0.440878}
    //870 290 {-3.207148, -0.049295, -0.440878}
    synchronized (mScratch128i) {
      //int idx[] = { 246,249,277,279,287,290, };
      mScratch128i[0] = 246;
      mScratch128i[1] = 249;
      mScratch128i[2] = 277;
      mScratch128i[3] = 279;
      mScratch128i[4] = 287;
      mScratch128i[5] = 290;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<6; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //582 194 {0.041021, 1.354216, -0.010364}
    //585 195 {0.041021, 1.354216, -0.010364}
    //594 198 {0.041021, 1.354216, -0.010364}
    //732 244 {0.041021, 1.354216, -0.010364}
    //984 328 {0.041021, 1.354216, -0.010364}
    //990 330 {0.041021, 1.354216, -0.010364}
    //1005 335 {0.041021, 1.354216, -0.010364}
    //1011 337 {0.041021, 1.354216, -0.010364}
    synchronized (mScratch128i) {
      //int idx[] = { 194,195,198,244,328,330,335,337, };
      mScratch128i[0] = 194;
      mScratch128i[1] = 195;
      mScratch128i[2] = 198;
      mScratch128i[3] = 244;
      mScratch128i[4] = 328;
      mScratch128i[5] = 330;
      mScratch128i[6] = 335;
      mScratch128i[7] = 337;
      float width = getMoveWidth(IwashiData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<8; ii++) {
        IwashiData.vertices[2+3*mScratch128i[ii]] = IwashiData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }


    //ByteBuffer vbb = ByteBuffer.allocateDirect(IwashiData.vertices.length * 4);
    //vbb.order(ByteOrder.nativeOrder());
    //mVertexBuffer = vbb.asFloatBuffer();
    mVertexBuffer.position(0);
    mVertexBuffer.put(IwashiData.vertices);
    mVertexBuffer.position(0);

  }

  public void calc() {
    synchronized (this) {
      think();
      move();
      animate();
    }
  }

  public void draw(GL10 gl10) {
    gl10.glPushMatrix();


    gl10.glPushMatrix();
    {
      /*=======================================================================*/
      /* 環境光の材質色設定                                                    */
      /*=======================================================================*/
      synchronized (mScratch4f) {
        mScratch4f[0] = 0.07f;
        mScratch4f[1] = 0.07f;
        mScratch4f[2] = 0.07f;
        mScratch4f[3] = 1.0f;
        gl10.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_AMBIENT, mScratch4f, 0);
      }
      /*=======================================================================*/
      /* 拡散反射光の色設定                                                    */
      /*=======================================================================*/
      synchronized (mScratch4f) {
        mScratch4f[0] = 0.24f;
        mScratch4f[1] = 0.24f;
        mScratch4f[2] = 0.24f;
        mScratch4f[3] = 1.0f;
        gl10.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_DIFFUSE, mScratch4f, 0);
      }
      /*=======================================================================*/
      /* 鏡面反射光の質感色設定                                                */
      /*=======================================================================*/
      synchronized (mScratch4f) {
        mScratch4f[0] = 1.0f;
        mScratch4f[1] = 1.0f;
        mScratch4f[2] = 1.0f;
        mScratch4f[3] = 1.0f;
        gl10.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_SPECULAR, mScratch4f, 0);
      }
      gl10.glMaterialf(GL10.GL_FRONT_AND_BACK, GL10.GL_SHININESS, 64f);
    }
    gl10.glTranslatef(getX(),getY(),getZ());
    gl10.glScalef(0.7f,0.7f,0.7f);

    gl10.glRotatef(y_angle, 0.0f, 1.0f, 0.0f);
    gl10.glRotatef(x_angle * -1f, 0.0f, 0.0f, 1.0f);

    gl10.glColor4f(1,1,1,1);
    gl10.glVertexPointer(3, GL10.GL_FLOAT, 0, mVertexBuffer);
    gl10.glNormalPointer(GL10.GL_FLOAT, 0, mNormalBuffer);
    gl10.glEnable(GL10.GL_TEXTURE_2D);
    gl10.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[0]);
    gl10.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mTextureBuffer);
    gl10.glDrawArrays(GL10.GL_TRIANGLES, 0, IwashiData.iwashiNumVerts);



    gl10.glPopMatrix();
    gl10.glPopMatrix();
  }

  public boolean doSeparation(Iwashi target) {
    /*===================================================================*/
    /* セパレーション領域にターゲットがいる場合                          */
    /*===================================================================*/
    if (debug) {
      if (iwashiNo == 0) {
        Log.d(TAG, "doSeparation");
      }
    }
    setStatus(STATUS.SEPARATE);
    turnSeparation(target);
    return true;
  }
  public boolean doAlignment(Iwashi target) {
    int per = 3000;
    if (this.schoolCount < 3) {
      /* 3匹以上群れてなければ高確率でCohesion/Schoolへ */
      per = 9000; 
    }
    per = adjustTick(per);
    if (this.rand.nextInt(10000) <= per) {
      return false;
    }
    /*===================================================================*/
    /* アラインメント領域にターゲットがいる場合                          */
    /*===================================================================*/
    if (debug) {
      if (iwashiNo == 0) {
        Log.d(TAG, "doAlignment");
      }
    }
    setStatus(STATUS.ALIGNMENT);
    turnAlignment(target);
    return true;
  }
  public int adjustTick(int val) {
    if (debug) {
      if (iwashiNo == 0) {
        Log.d(TAG, "prev " + val);
      }
    }
    int ret = (int)((float)val * ((float)tick / (float)BASE_TICK));
    if (tick / BASE_TICK >= 2) {
      ret = (int)((float)val * 1.2f);
    }
    if (ret >= 10000) {
      ret = 9900;
    }
    if (debug) {
      if (iwashiNo == 0) {
        Log.d(TAG, "aflter " + ret);
      }
    }
    return ret;
  }
  public boolean doCohesion(Iwashi target) {
    /* 鰯は結構な確率でCohesionするものと思われる */
    if (getStatus() == STATUS.COHESION) {
      if (this.rand.nextInt(10000) <= adjustTick(500)) {
        /* 前回COHESIONである場合今回もCOHESIONである可能性は高い */
        return false;
      }
    }
    else {
      if (this.rand.nextInt(10000) <= adjustTick(1000)) {
        return false;
      }
    }
    /*===================================================================*/
    /* コアージョン領域にターゲットがいる場合                            */
    /*===================================================================*/
    if (debug) {
      if (iwashiNo == 0) {
        Log.d(TAG, "doCohesion ("+schoolCount+")");
      }
    }
    setStatus(STATUS.COHESION);
    turnCohesion(target);
    return true;
  }
  public boolean doSchoolCenter() {
    if (this.rand.nextInt(10000) <= adjustTick(3000)) {
      return false;
    }
    if (schoolCount < 3) {
      return false;
    }
    if (debug) {
      if (iwashiNo == 0) {
        Log.d(TAG, "doSchoolCenter");
      }
    }
    setStatus(STATUS.TO_SCHOOL_CENTER);
    aimSchoolCenter();
    return true;
  }
  public void update_speed() {
    sv_speed = speed;
    if (getStatus() == STATUS.COHESION) {
      speed = cohesion_speed;
      return;
    }
    speed = sv_speed;

    if (this.rand.nextInt(10000) <= adjustTick(1000)) {
      // 変更なし
      return;
    }
    speed += (this.rand.nextFloat() * (speed_unit * 2f) / 2f);
    if (speed <= speed_min) {
      speed = speed_min;
    }
    if (speed > speed_max) {
      speed = speed_max;
    }
  }

  /** 
   * もっとも近い鰯を返す
   */
  public Iwashi[] getTarget() {
    float targetDistanceS = 10000f;
    float targetDistanceA = 10000f;
    float targetDistanceC = 10000f;
    int targetS = 9999;
    int targetA = 9999;
    int targetC = 9999;
    /* alignment数をカウント */
    this.schoolCount = 0;
    this.schoolCenter[0] = 0f;
    this.schoolCenter[1] = 0f;
    this.schoolCenter[2] = 0f;
    for (int ii=0; ii<species.length; ii++) {
      float dist = 0f;
      if (ii < iwashiNo) {
        dist = species[ii].distances[iwashiNo];
      }
      else if (ii == iwashiNo) {
        continue;
      }
      else {
        dist = (float)Math.sqrt(
            Math.pow(getX()-species[ii].getX(), 2)
          + Math.pow(getY()-species[ii].getY(), 2)
          + Math.pow(getZ()-species[ii].getZ(), 2));
      }
      this.distances[ii] = dist;
      if (dist < separate_dist) {
        if (targetDistanceS > dist) {
          targetDistanceS = dist;
          targetS = ii;
        }
        continue;
      }
      if (dist < alignment_dist) {
        {
          /* alignmentの位置にいれば、それだけでカウント */
          this.schoolCount++;
          schoolCenter[0] += species[ii].getX();;
          schoolCenter[1] += species[ii].getY();;
          schoolCenter[2] += species[ii].getZ();;
        }
        if (targetDistanceA > dist) {
          synchronized (mScratch4f_1) {
            synchronized (mScratch4f_2) {
              mScratch4f_1[0] = getDirectionX();
              mScratch4f_1[1] = getDirectionY();
              mScratch4f_1[2] = getDirectionZ();
              mScratch4f_2[0] = species[ii].getX() - getX();
              mScratch4f_2[1] = species[ii].getY() - getY();
              mScratch4f_2[2] = species[ii].getZ() - getZ();
              float degree = CoordUtil.includedAngle(mScratch4f_1, mScratch4f_2, 3);
              if (degree <= 150f && degree >= 0f) {
                targetDistanceA = dist;
                targetA = ii;
              }
            }
          }
        }
        continue;
      }
      if (dist < cohesion_dist) {
        if (dist < school_dist) {
          this.schoolCount++;
          schoolCenter[0] += species[ii].getX();;
          schoolCenter[1] += species[ii].getY();;
          schoolCenter[2] += species[ii].getZ();;
        }
        if (targetDistanceC > dist) {
          synchronized (mScratch4f_1) {
            synchronized (mScratch4f_2) {
              mScratch4f_1[0] = getDirectionX();
              mScratch4f_1[1] = getDirectionY();
              mScratch4f_1[2] = getDirectionZ();
              mScratch4f_2[0] = species[ii].getX() - getX();
              mScratch4f_2[1] = species[ii].getY() - getY();
              mScratch4f_2[2] = species[ii].getZ() - getZ();
              float degree = CoordUtil.includedAngle(mScratch4f_1, mScratch4f_2, 3);
              if (degree <= 90f && degree >= 0f) {
                /* おおむね前方だったら */
                targetDistanceC = dist;
                targetC = ii;
              }
            }
          }
        }
      }
    }
    if (schoolCount != 0) {
      schoolCenter[0] = schoolCenter[0] / (float)schoolCount;
      schoolCenter[1] = schoolCenter[1] / (float)schoolCount;
      schoolCenter[2] = schoolCenter[2] / (float)schoolCount;
    }
    if (targetS != 9999) {
      mScratch3Iwashi[0] = species[targetS];
    }
    else {
      mScratch3Iwashi[0] = null;
    }
    if (targetA != 9999) {
      mScratch3Iwashi[1] = species[targetA];
    }
    else {
      mScratch3Iwashi[1] = null;
    }
    if (targetC != 9999) {
      mScratch3Iwashi[2] = species[targetC];
    }
    else {
      mScratch3Iwashi[2] = null;
    }
    return mScratch3Iwashi;
  }
  /**
   * どの方向に進むか考える
   */
  public void think() {
    long nowTime = System.nanoTime();
    if (prevTime != 0) {
      tick = nowTime - prevTime;
    }
    if (getStatus() == STATUS.COHESION) {
      /* 元に戻す */
      speed = sv_speed;
    }
    prevTime = nowTime;
    if (  (Aquarium.min_x.floatValue() >= position[0] || Aquarium.max_x.floatValue() <= position[0])
      ||  (Aquarium.min_y.floatValue() >= position[1] || Aquarium.max_y.floatValue() <= position[1])
      ||  (Aquarium.min_z.floatValue() >= position[2] || Aquarium.max_z.floatValue() <= position[2])) {
      /*=====================================================================*/
      /* 水槽からはみ出てる                                                  */
      /*=====================================================================*/
      if (debug) {
        if (iwashiNo == 0) {
          Log.d(TAG, "doAquariumCenter");
        }
      }
      setStatus(STATUS.TO_CENTER);
      aimAquariumCenter();
      update_speed();
      return;
    }
    /**
     * 餌ロジック
     */
    Bait bait = baitManager.getBait();
    if (bait != null) {
      if (this.rand.nextInt(10000) <= adjustTick(5500)) {
        if (aimBait(bait)) {
          if (debug) {
            if (iwashiNo == 0) {
              Log.d(TAG, "doBait");
            }
          }
          setStatus(STATUS.TO_BAIT);
          update_speed();
          return;
        }
      }
    }


    if (getEnableBoids()) {
      /**
       * １　セパレーション（Separation）：分離
       *  　　→仲間に近づきすぎたら離れる
       * ２　アラインメント（Alignment）：整列
       *  　　→仲間と同じ方向に同じ速度で飛ぶ
       * ３　コアージョン（Cohesion）：凝集
       *  　　→仲間の中心方向に飛ぶ
       */
      // separation
      Iwashi[] target = getTarget();
      if (target[0] != null) {
        if (doSeparation(target[0])) {
          update_speed();
          target[0] = null;
          target[1] = null;
          target[2] = null;
          return;
        }
      }
      if (target[1] != null) {
        // alignment
        if (doAlignment(target[1])) {
          target[0] = null;
          target[1] = null;
          target[2] = null;
          return;
        }
      }
      if (schoolCount >= 3) {
        if (doSchoolCenter()) {
          update_speed();
          return;
        }
      }
      if (target[2] != null) {
        // cohesion
        if (doCohesion(target[2])) {
          update_speed();
          target[0] = null;
          target[1] = null;
          target[2] = null;
          return;
        }
      }
      target[0] = null;
      target[1] = null;
      target[2] = null;
    }

    if (this.rand.nextInt(10000) <= adjustTick(9500)) {
      // 変更なし
      return;
    }
    if (debug) {
      if (iwashiNo == 0) {
        Log.d(TAG, "doNormal");
      }
    }
    setStatus(STATUS.NORMAL);
    turn();
    update_speed();
  }


  public void turn() {
    // 方向転換
    // 45 >= x >= -45
    // 360 >= y >= 0
    // 一回の方向転換のMAX
    // 45 >= x >= -45
    // 45 >= y >= -45
    float old_angle_x = x_angle;
    float old_angle_y = y_angle;
    x_angle = old_angle_x;
    y_angle = old_angle_y;
    float newAngleX = this.rand.nextFloat() * 45f - 22.5f;
    float newAngleY = this.rand.nextFloat() * 45f - 22.5f;
    if (newAngleX + x_angle <= 45f && newAngleX + x_angle >= -45f) {
      x_angle = x_angle + newAngleX;
    } 
    else {
      if (newAngleX + x_angle >= 45f) {
        x_angle = (this.rand.nextFloat() * 45f);
      }
      else if (newAngleX + x_angle <= -45f) {
        x_angle = (this.rand.nextFloat() * -45f);
      }
    }
    y_angle = (float)((int)(y_angle + newAngleY) % 360);
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
  }
  public void aimTargetDegree(float angle_x, float angle_y) {
    float newAngle = this.rand.nextFloat() * 22.5f;
    float xx = angle_x - x_angle;
    if (xx < 0.0f) {
      if (xx > -22.5f) {
        x_angle += xx;
      }
      else {
        x_angle += -newAngle;
      }
    }
    else {
      if (xx < 22.5f) {
        x_angle += xx;
      }
      else {
        x_angle += newAngle;
      }
    }
    if (x_angle > 45.0f) {
      x_angle = 45.0f;
    }
    if (x_angle < -45.0f) {
      x_angle = -45.0f;
    }

    float yy = angle_y - y_angle;
    if (yy > 180.0f) {
      yy = -360f + yy;
    }
    else if (yy < -180.0f) {
      yy = 360f - yy;
    }

    if (yy < 0.0f) {
      if (yy > -22.5f) {
        y_angle += yy;
      }
      else {
        y_angle += -newAngle;
      }
    }
    else {
      if (yy < 22.5f) {
        y_angle += yy;
      }
      else {
        y_angle += newAngle;
      }
    }
    y_angle = y_angle % 360f;
    if (y_angle < 0f) {
      y_angle = 360f + y_angle;
    }
  }
  public void aimTargetSpeed(float t_speed) {
    if (t_speed <= speed) {
      /* 自分のスピードよりも相手の方が遅い場合 */
      if (false) {
        speed -= (this.rand.nextFloat() * speed_unit);
        if (speed <= speed_min) {
          speed = speed_unit;
        }
      }
      else {
       update_speed();
      }
    }
    else {
      /* 相手の方が早い場合 */
      speed += (this.rand.nextFloat() * speed_unit);
      if (t_speed < speed) {
        /* 越えちゃったらちょっとだけ遅く*/
        speed = t_speed - (this.rand.nextFloat() * speed_unit);
      }
      if (speed > speed_max) {
        speed = speed_max;
      }
    }
  }
  public void turnSeparation(Iwashi target) {
    if (debug) {
      Log.d(TAG, "start turnSeparate");
    }
    /*=======================================================================*/
    /* ターゲットのいる方向とは逆の方向を算出                                */
    /*=======================================================================*/
    float v_x = (target.getX() - getX()) * -1f;
    float v_y = (target.getY() - getY()) * -1f;
    float v_z = (target.getZ() - getZ()) * -1f;
    if (v_x == 0f && v_y == 0f && v_z == 0f) {
      /*=====================================================================*/
      /* もし、算出できないのであれば、ターゲットの方向とは逆方向            */
      /*=====================================================================*/
      v_x = target.getDirection()[0] * -1;
      v_y = target.getDirection()[1] * -1;
      v_z = target.getDirection()[2] * -1;
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向"
       + " x:[" + v_x + "]:"
       + " y:[" + v_y + "]:"
       + " z:[" + v_z + "]:");
    }

    /* 上下角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_x = (float)coordUtil.convertDegreeXY((double)v_x, (double)v_y);
    /* 左右角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_y = (float)coordUtil.convertDegreeXZ((double)v_x * -1d, (double)v_z);
    if (angle_x > 180f) {
      angle_x = angle_x - 360f;
    }
    if ((angle_x < 0.0f && v_y > 0.0f) || (angle_x > 0.0f && v_y < 0.0f)) {
      angle_x *= -1f;
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向のangle_y:[" + angle_y + "]");
      Log.d(TAG, "向かいたい方向のangle_x:[" + angle_x + "]");
    }

    /* その角度へ近づける */
    aimTargetDegree(angle_x, angle_y);
    if (debug) {
      Log.d(TAG, "実際に向かう方向のy_angle:[" + y_angle + "]");
      Log.d(TAG, "実際に向かう方向のx_angle:[" + x_angle + "]");
    }

    /* direction設定 */
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
    if (debug) {
      Log.d(TAG, "結果的に向かう方向"
       + " x:[" + direction[0] + "]:"
       + " y:[" + direction[1] + "]:"
       + " z:[" + direction[2] + "]:");
      Log.d(TAG, "end turnSeparate");
    }
  }
  public void turnAlignment(Iwashi target) {
    if (debug) {
      Log.d(TAG, "start turnAlignment");
    }
    /* ターゲットの角度 */
    float angle_x = target.getX_angle();
    float angle_y = target.getY_angle();
    if (debug) {
      Log.d(TAG, "向かいたい方向のangle_y:[" + angle_y + "]");
      Log.d(TAG, "向かいたい方向のangle_x:[" + angle_x + "]");
    }

    /* その角度へ近づける */
    aimTargetDegree(angle_x, angle_y);
    if (debug) {
      Log.d(TAG, "実際に向かう方向のy_angle:[" + y_angle + "]");
      Log.d(TAG, "実際に向かう方向のx_angle:[" + x_angle + "]");
    }

    /* direction設定 */
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
    if (debug) {
      Log.d(TAG, "結果的に向かう方向"
       + " x:[" + direction[0] + "]:"
       + " y:[" + direction[1] + "]:"
       + " z:[" + direction[2] + "]:");
    }

    /* スピードも合わせる */
    aimTargetSpeed(target.getSpeed());

    if (debug) {
      Log.d(TAG, "end turnAlignment");
    }
  }
  public void turnCohesion(Iwashi target) {
    if (debug) {
      Log.d(TAG, "start turnCohesion");
    }
    /* 順方向へのベクトルを算出 */
    float v_x = (target.getX() - getX());
    float v_y = (target.getY() - getY());
    float v_z = (target.getZ() - getZ());
    if (v_x == 0f && v_y == 0f && v_z == 0f) {
      v_x = target.getDirection()[0];
      v_y = target.getDirection()[1];
      v_z = target.getDirection()[2];
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向"
       + " x:[" + v_x + "]:"
       + " y:[" + v_y + "]:"
       + " z:[" + v_z + "]:");
    }


    /* 上下角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_x = (float)coordUtil.convertDegreeXY((double)v_x, (double)v_y);
    /* 左右角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_y = (float)coordUtil.convertDegreeXZ((double)v_x * -1d, (double)v_z);
    if (angle_x > 180f) {
      angle_x = angle_x - 360f;
    }
    if ((angle_x < 0.0f && v_y > 0.0f) || (angle_x > 0.0f && v_y < 0.0f)) {
      angle_x *= -1f;
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向のangle_y:[" + angle_y + "]");
      Log.d(TAG, "向かいたい方向のangle_x:[" + angle_x + "]");
    }

    /* その角度へ近づける */
    aimTargetDegree(angle_x, angle_y);
    if (debug) {
      Log.d(TAG, "実際に向かう方向のy_angle:[" + y_angle + "]");
      Log.d(TAG, "実際に向かう方向のx_angle:[" + x_angle + "]");
    }

    /* direction設定 */
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
    if (debug) {
      Log.d(TAG, "結果的に向かう方向"
       + " x:[" + direction[0] + "]:"
       + " y:[" + direction[1] + "]:"
       + " z:[" + direction[2] + "]:");
      Log.d(TAG, "end turnCohesion");
    }
  }

  /**
   * 強制的に水槽の中心へ徐々に向ける
   */
  public void aimAquariumCenter() {
    if (debug) {
      Log.d(TAG, "start aimAquariumCenter ");
    }
    float v_x = (Aquarium.center[0] - getX());
    float v_y = (Aquarium.center[1] - getY());
    float v_z = (Aquarium.center[2] - getZ());
    if (Aquarium.min_x.floatValue() < getX() && Aquarium.max_x.floatValue() > getX()
    &&  Aquarium.min_y.floatValue() < getY() && Aquarium.max_y.floatValue() > getY()) {
      /* Zだけはみ出た */
      v_x = 0.0f;
      v_y = 0.0f;
    }
    else 
    if (Aquarium.min_x.floatValue() < getX() && Aquarium.max_x.floatValue() > getX()
    &&  Aquarium.min_z.floatValue() < getZ() && Aquarium.max_z.floatValue() > getZ()) {
      /* Yだけはみ出た */
      v_x = 0.0f;
      v_z = 0.0f;
    }
    else 
    if (Aquarium.min_y.floatValue() < getY() && Aquarium.max_y.floatValue() > getY()
    &&  Aquarium.min_z.floatValue() < getZ() && Aquarium.max_z.floatValue() > getZ()) {
      /* Xだけはみ出た */
      v_y = 0.0f;
      v_z = 0.0f;
    }
    /* 上下角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_x = (float)coordUtil.convertDegreeXY((double)v_x, (double)v_y);
    /* 左右角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_y = (float)coordUtil.convertDegreeXZ((double)v_x * -1d, (double)v_z);
    if (angle_x > 180f) {
      angle_x = angle_x - 360f;
    }
    if ((angle_x < 0.0f && v_y > 0.0f) || (angle_x > 0.0f && v_y < 0.0f)) {
      angle_x *= -1f;
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向のangle_y:[" + angle_y + "]");
      Log.d(TAG, "向かいたい方向のangle_x:[" + angle_x + "]");
    }

    if (angle_y < 0.0f) {
      angle_y = 360f + angle_y;
    }
    angle_y = angle_y % 360f;

    /* その角度へ近づける */
    aimTargetDegree(angle_x, angle_y);
    if (debug) {
      Log.d(TAG, "実際に向かう方向のy_angle:[" + y_angle + "]");
      Log.d(TAG, "実際に向かう方向のx_angle:[" + x_angle + "]");
    }

    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
    if (debug) {
      Log.d(TAG, "end aimAquariumCenter "
        + "x:[" + direction[0] + "]:"
        + "y:[" + direction[1] + "]:"
        + "z:[" + direction[2] + "]:");
    }
  }
  public void aimSchoolCenter() {
    if (debug) {
      Log.d(TAG, "start aimSchoolCenter ");
    }
    float v_x = (schoolCenter[0] - getX());
    float v_y = (schoolCenter[1] - getY());
    float v_z = (schoolCenter[2] - getZ());

    /* 上下角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_x = (float)coordUtil.convertDegreeXY((double)v_x, (double)v_y);
    /* 左右角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_y = (float)coordUtil.convertDegreeXZ((double)v_x * -1d, (double)v_z);
    if (angle_x > 180f) {
      angle_x = angle_x - 360f;
    }
    if ((angle_x < 0.0f && v_y > 0.0f) || (angle_x > 0.0f && v_y < 0.0f)) {
      angle_x *= -1f;
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向のangle_y:[" + angle_y + "]");
      Log.d(TAG, "向かいたい方向のangle_x:[" + angle_x + "]");
    }

    if (angle_y < 0.0f) {
      angle_y = 360f + angle_y;
    }
    angle_y = angle_y % 360f;

    /* その角度へ近づける */
    aimTargetDegree(angle_x, angle_y);
    if (debug) {
      Log.d(TAG, "実際に向かう方向のy_angle:[" + y_angle + "]");
      Log.d(TAG, "実際に向かう方向のx_angle:[" + x_angle + "]");
    }

    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
    if (debug) {
      Log.d(TAG, "end aimSchoolCenter "
        + "x:[" + direction[0] + "]:"
        + "y:[" + direction[1] + "]:"
        + "z:[" + direction[2] + "]:");
    }
  }
  public boolean aimBait(Bait bait) {
    if (debug) {
      Log.d(TAG, "start aimBait ");
    }
    double dist = Math.sqrt(
        Math.pow(position[0]-bait.getX(), 2)
      + Math.pow(position[1]-bait.getY(), 2)
      + Math.pow(position[2]-bait.getZ(), 2));
    if (dist <= separate_dist) {
      baitManager.eat(bait);
      return false;
    }
    float v_x = (bait.getX() - getX());
    float v_y = (bait.getY() - getY());
    float v_z = (bait.getZ() - getZ());
    if (debug) {
      Log.d(TAG, "向かいたい方向"
       + " x:[" + v_x + "]:"
       + " y:[" + v_y + "]:"
       + " z:[" + v_z + "]:");
    }

    /* 上下角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_x = (float)coordUtil.convertDegreeXY((double)v_x, (double)v_y);
    /* 左右角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_y = (float)coordUtil.convertDegreeXZ((double)v_x * -1d, (double)v_z);
    if (angle_x > 180f) {
      angle_x = angle_x - 360f;
    }
    if ((angle_x < 0.0f && v_y > 0.0f) || (angle_x > 0.0f && v_y < 0.0f)) {
      angle_x *= -1f;
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向のangle_y:[" + angle_y + "]");
      Log.d(TAG, "向かいたい方向のangle_x:[" + angle_x + "]");
    }

    /* その角度へ近づける */
    aimTargetDegree(angle_x, angle_y);
    if (debug) {
      Log.d(TAG, "実際に向かう方向のy_angle:[" + y_angle + "]");
      Log.d(TAG, "実際に向かう方向のx_angle:[" + x_angle + "]");
    }

    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
    if (debug) {
      Log.d(TAG, "end aimBait "
        + "x:[" + direction[0] + "]:"
        + "y:[" + direction[1] + "]:"
        + "z:[" + direction[2] + "]:");
    }
    return true;
  }
  public void move() {
    /*=======================================================================*/
    /* 処理速度を考慮した増分                                                */
    /*=======================================================================*/
    float moveWidth = getSpeed() * (float)(tick / BASE_TICK);

    if (getX() + getDirectionX() * moveWidth >= Aquarium.max_x) {
      setX(Aquarium.max_x);
    }
    else if (getX() + getDirectionX() * moveWidth <= Aquarium.min_x) {
      setX(Aquarium.min_x);
    }
    else {
      setX(getX() + getDirectionX() * moveWidth);
    }
    if (getY() + getDirectionY() * moveWidth >= Aquarium.max_y) {
      setY(Aquarium.max_y);
    }
    else if (getY() + getDirectionY() * moveWidth <= Aquarium.min_y) {
      setY(Aquarium.min_y);
    }
    else {
      setY(getY() + getDirectionY() * moveWidth);
    }
    if (getZ() + getDirectionZ() * moveWidth >= Aquarium.max_z) {
      setZ(Aquarium.max_z);
    }
    else if (getZ() + getDirectionZ() * moveWidth <= Aquarium.min_z) {
      setZ(Aquarium.min_z);
    }
    else {
      setZ(getZ() + getDirectionZ() * moveWidth);
    }
    if (debug) {
      Log.d(TAG, "end move "
        + "dx:[" + getDirectionX() + "]:"
        + "dy:[" + getDirectionY() + "]:"
        + "dz:[" + getDirectionZ() + "]:"
        + "speed:[" + getSpeed() + "]:"
        + "x:[" + getX() + "]:"
        + "y:[" + getY() + "]:"
        + "z:[" + getZ() + "]:"
        + "x_angle:[" + x_angle + "]:"
        + "y_angle:[" + y_angle + "]:"
        );
    }
  }


  public float[] getPosition() {
    return position;
  }
  public void setPosition(float[] pos) {
    this.position = pos;
  }
  
  public float getX() {
    return position[0];
  }
  
  public void setX(float x) {
    this.position[0] = x;
  }
  
  public float getY() {
    return position[1];
  }
  
  public void setY(float y) {
    this.position[1] = y;
  }
  
  public float getZ() {
    return position[2];
  }
  
  public void setZ(float z) {
    this.position[2] = z;
  }

  public float getDirectionX() {
    return direction[0];
  }
  public float getDirectionY() {
    return direction[1];
  }
  public float getDirectionZ() {
    return direction[2];
  }
  public void setDirectionX(float x) {
    this.direction[0] = x;
  }
  public void setDirectionY(float y) {
    this.direction[1] = y;
  }
  public void setDirectionZ(float z) {
    this.direction[2] = z;
  }
  
  public float getSpeed() {
    return speed;
  }
  
  public void setSpeed(float speed) {
    this.speed = speed * 0.5f;
    this.speed_unit = speed / 5f * 0.5f;
    this.speed_max = speed + 0.03f * 0.5f;
    this.speed_min = this.speed_unit * 2f;
    this.cohesion_speed = speed * 2f * 0.5f;
    this.sv_speed = speed;
  }
  
  public float[] getDirection() {
    return direction;
  }
  
  public float getDirection(int index) {
    return direction[index];
  }
  
  public void setDirection(float[] direction) {
    this.direction = direction;
  }
  
  public void setDirection(float direction, int index) {
    this.direction[index] = direction;
  }
  
  /**
   * Get species.
   *
   * @return species as Iwashi[].
   */
  public Iwashi[] getSpecies() {
    return species;
  }
  
  /**
   * Get species element at specified index.
   *
   * @param index the index.
   * @return species at index as Iwashi.
   */
  public Iwashi getSpecies(int index) {
    return species[index];
  }
  
  /**
   * Set species.
   *
   * @param species the value to set.
   */
  public void setSpecies(Iwashi[] species) {
    this.species = species;
    for (int ii=0; ii<species.length; ii++) {
      this.distances[ii] = 10000f;
    }
  }
  
  /**
   * Set species at the specified index.
   *
   * @param species the value to set.
   * @param index the index.
   */
  public void setSpecies(Iwashi species, int index) {
    this.species[index] = species;
  }
  
  /**
   * Get x_angle.
   *
   * @return x_angle as float.
   */
  public float getX_angle()
  {
      return x_angle;
  }
  
  /**
   * Set x_angle.
   *
   * @param x_angle the value to set.
   */
  public void setX_angle(float x_angle)
  {
      this.x_angle = x_angle;
  }
  
  /**
   * Get y_angle.
   *
   * @return y_angle as float.
   */
  public float getY_angle()
  {
      return y_angle;
  }
  
  /**
   * Set y_angle.
   *
   * @param y_angle the value to set.
   */
  public void setY_angle(float y_angle)
  {
      this.y_angle = y_angle;
  }
  
  /**
   * Get schoolCenter.
   *
   * @return schoolCenter as float[].
   */
  public float[] getSchoolCenter()
  {
      return schoolCenter;
  }
  
  /**
   * Get schoolCenter element at specified index.
   *
   * @param index the index.
   * @return schoolCenter at index as float.
   */
  public float getSchoolCenter(int index)
  {
      return schoolCenter[index];
  }
  
  /**
   * Set schoolCenter.
   *
   * @param schoolCenter the value to set.
   */
  public void setSchoolCenter(float[] schoolCenter) {
      this.schoolCenter = schoolCenter;
  }
  
  /**
   * Set schoolCenter at the specified index.
   *
   * @param schoolCenter the value to set.
   * @param index the index.
   */
  public void setSchoolCenter(float schoolCenter, int index)
  {
      this.schoolCenter[index] = schoolCenter;
  }
  
  /**
   * Get baitManager.
   *
   * @return baitManager as BaitManager.
   */
  public BaitManager getBaitManager()
  {
      return baitManager;
  }
  
  /**
   * Set baitManager.
   *
   * @param baitManager the value to set.
   */
  public void setBaitManager(BaitManager baitManager)
  {
      this.baitManager = baitManager;
  }
  
  
  /**
   * Get enableBoids.
   *
   * @return enableBoids as boolean.
   */
  public boolean getEnableBoids()
  {
      return enableBoids;
  }
  
  /**
   * Set enableBoids.
   *
   * @param enableBoids the value to set.
   */
  public void setEnableBoids(boolean enableBoids)
  {
      this.enableBoids = enableBoids;
  }
  
  /**
   * Get status.
   *
   * @return status as STATUS.
   */
  public STATUS getStatus() {
    return status;
  }
  
  /**
   * Set status.
   *
   * @param status the value to set.
   */
  public void setStatus(STATUS status) {
    this.status = status;
  }
  
  /**
   * Get size.
   *
   * @return size as float.
   */
  public float getSize()
  {
      return size;
  }
  
  /**
   * Set size.
   *
   * @param size the value to set.
   */
  public void setSize(float size)
  {
      this.size = size;
  }
}
