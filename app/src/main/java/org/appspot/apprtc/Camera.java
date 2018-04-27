package org.appspot.apprtc;

import android.opengl.Matrix;
/**
 * Created by xiewei on 2018/4/17.
 */

public class Camera {
    float mProjection[] = new float[16];
    float mModel[]      = new float[16];
    float mView[]       = new float[16];
    float mTemp[]       = new float[16];

    float mPos[]        = new float[4];
    float mRight[]      = new float[4];
    float mUp[]         = new float[4];
    float mDir[]        = new float[4];

    public float[] getMVPMatrix() {
        float temp[] = new float[16];
        Matrix.multiplyMM(temp, 0, mProjection, 0, mView, 0);
        Matrix.multiplyMM(mTemp, 0, temp, 0, mModel, 0);
        return mTemp;
    }

    private void init() {

    }
}
