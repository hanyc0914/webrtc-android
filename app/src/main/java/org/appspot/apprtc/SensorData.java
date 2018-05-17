
package org.appspot.apprtc;

import android.annotation.SuppressLint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;
import android.util.Log;


public class SensorData  implements SensorEventListener {
	
	private long    mHandle = 0;

	private SensorManager mSensorManager = null;

    // magnetic field vector
    private float[] magnet = new float[3];
 
    // accelerometer vector
    private float[] accel = new float[3];

    private float[] mMat = new float[]
	{
		1, 0, 0, 0,
			0, 1, 0, 0,
			0, 0, 1, 0,
			0, 0, 0, 1
	};
    private float[] mProj = new float[16];

	private android.content.Context mContext;

    public float[] getMat() {
    	return mProj;
	}

	SensorData(android.content.Context context) {
		mContext = context;
		System.out.print("test");
	}
    
    protected void finalize( )
    {
    	// finalization code here
    	stop();
    }

    public Boolean initListeners(){
    	computePerspective();
    	// TODO pass context to this class
		mSensorManager = (SensorManager) mContext.getSystemService(mContext.SENSOR_SERVICE);
		if (mSensorManager != null) {
			Boolean ret = mSensorManager.registerListener(this,
					mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
					SensorManager.SENSOR_DELAY_FASTEST);
		}
        return true;
    }

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {		
	}

	protected static final int Sensor_ACC_Data		= 1;
	protected static final int Sensor_MAG_Data      = 2;
	protected static final int Sensor_ROT_Data		= 3;
	@SuppressLint("NewApi")
	@Override
	public void onSensorChanged(SensorEvent event) {
		switch(event.sensor.getType()) {
	    case Sensor.TYPE_ACCELEROMETER:
	        // copy new accelerometer data into accel array and calculate orientation
	        System.arraycopy(event.values, 0, accel, 0, 3);
	        float minVal = 0.001f;
	        if(accel[0] < minVal && accel[1] < minVal && accel[2] < minVal)
	        	break;
//			_writeData(mHandle, Sensor_ACC_Data, accel[0], accel[1], accel[2]);
	        break;
	 
	    case Sensor.TYPE_GYROSCOPE:
	        // process gyro data
	        break;
	 
	    case Sensor.TYPE_MAGNETIC_FIELD:
	        // copy new magnetometer data into magnet array
	        System.arraycopy(event.values, 0, magnet, 0, 3);
//			_writeData(mHandle, Sensor_MAG_Data, magnet[0], magnet[1], magnet[2]);
	        break;
	    case Sensor.TYPE_ROTATION_VECTOR:
//	        _writeData(mHandle, Sensor_ROT_Data, event.values[0], event.values[1], event.values[2]);
			updateCamera(event.values[0], event.values[1], event.values[2]);
			// update location
	    	break;
	    }
	}

	
	public void stop() {
		Log.e("ttmn", "stop sensor");
    	if(mSensorManager != null) {
    		mSensorManager.unregisterListener(this);
    		mSensorManager = null;
    	}
        mHandle = 0;
	}
	
	public int start() {
		if (initListeners()) {
			return 0;
		}
		return -1;
	}

//	public Mat4 getMatrix() {
//		return mView.times(mProj);
//	}

	public void updateCamera(float v1, float v2, float v3) {
		double tw = 1 - (v1 * v1 + v2 * v2 + v3 * v3);
		double sqrtValue = tw > 0 ? Math.sqrt(tw) : 0;
		float w = (float) sqrtValue;
		float[] quat = new float[]{w, v1, v2, v3};

		// convert to matrix

		float[] mat = convertQuatToMat(quat);
		// convert mat to MVP

		float[] viewMat = new float[]
				{
					mat[0], mat[1*4], mat[2*4], mat[3*4],
					mat[1], mat[1*4+1], mat[2*4+1], mat[3*4+1],
					mat[2], mat[1*4+2], mat[2*4+2], mat[3*4+2],
					mat[3], mat[1*4+3], mat[2*4+3], mat[3*4+3]
				};

	}

	void computePerspective() {
		float fov = 60;
		float aspect = 1;
		float near = 0.1f;
		float far = 2;

		float rad = (float)Math.toRadians(fov);

		float tanHalfFov = (float)Math.tan(rad / 2.0);
		mProj[0]	= 1.0f / (aspect * tanHalfFov);
		mProj[1 * 4 + 1]	= 1.0f / tanHalfFov;
		mProj[2 * 4 + 2]	= - (far + near) / (far - near);
		mProj[2 * 4 + 3]	= -1.0f;
		mProj[3 * 4 + 2] 	= - (2 * far * near) / (far - near);
	}

	public void computeLookAtMatrix() {

	}

	//TODO: transform quat to a matrix
	private float[] getMatrix(float[] quat) {
		return null;
	}

	private float[] productOfMat(float[] mat1, float[] mat2) {
		float temp[] = new float[16];
		Matrix.multiplyMM(temp, 0, mat1, 0, mat2, 0);
		return temp;
	}

	private float[] productOfQuat(float[] A, float[] B) {
		float[] r = new float[4];
		r[0] = A[0]*B[0] - A[1]*B[1] - A[2]*B[2] - A[3]*B[3];
		r[1] = A[0]*B[1] - A[1]*B[0] - A[2]*B[3] - A[3]*B[2];
		r[2] = A[0]*B[2] - A[2]*B[0] - A[3]*B[1] - A[1]*B[3];
		r[3] = A[0]*B[3] - A[3]*B[0] - A[1]*B[2] - A[2]*B[1];
		return r;
	}

	private float[] convertQuatToMat(float[] q) {
		float[] Result = new float[16];
		float qxx = q[1]*q[1];
		float qyy = q[2]*q[2];
		float qzz = q[3]*q[3];

		float qxz = q[1]*q[3];
		float qxy = q[1]*q[2];
		float qyz = q[2]*q[3];
		float qwx = q[0]*q[1];
		float qwy = q[0]*q[2];
		float qwz = q[0]*q[3];

		Result[0] = 1 - 2 * (qyy +  qzz);
		Result[1] = 2 * (qxy + qwz);
		Result[2] = 2 * (qxz - qwy);
		Result[3] = 0;

		Result[1*4] = 2 * (qxy - qwz);
		Result[1*4 + 1] = 1 - 2 * (qxx +  qzz);
		Result[1*4 + 2] = 2 * (qyz + qwx);
		Result[1*4 + 3] = 0;

		Result[2*4] = 2 * (qxz + qwy);
		Result[2*4 + 1] = 2 * (qyz - qwx);
		Result[2*4 + 2] = 1 - 2 * (qxx +  qyy);
		Result[2*4 + 3] = 1;

		return Result;
	}
}
