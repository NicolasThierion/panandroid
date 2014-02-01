/*
 * ENSICAEN
 * 6 Boulevard Marechal Juin
 * F-14050 Caen Cedex
 *
 * This file is owned by ENSICAEN students.
 * No portion of this code may be reproduced, copied
 * or revised without written permission of the authors.
 */

package fr.ensicaen.panandroid.sphere;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import fr.ensicaen.panandroid.R;
import junit.framework.Assert;


import android.content.Context;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.GLU;

/**
 * Renderer for a GL SphereView. Draws a Sphere at the middle of the GL View.
 * Based on Frank Dürr 's OpenPanandroid, and
 * Guillaume Lesniak's picSphere app.
 *
 * @author Nicolas
 */
public class SphereRenderer implements Renderer {

	private static final String LOG_TAG = SphereRenderer.class.getSimpleName();

	/* *************
	 * GLOBAL RENDERING PARAMS
	 * *************/
	/** Clear colour */
	private static final float CLEAR_RED = 0.0f;
	private static final float CLEAR_GREEN = 0.0f;
	private static final float CLEAR_BLUE = 0.0f;
	private static final float CLEAR_ALPHA = 2.5f;

	/** Perspective setup, field of view component. */
	private static final float DEFAULT_FOV = 60.0f;

	/** Perspective setup, near component. */
	private static final float Z_NEAR = 0.1f;

	/** Perspective setup, far component. */
	private static final float Z_FAR = 100.0f;

	/** Object distance on the screen. */
	private static final float OBJECT_DISTANCE = 0.00f;



	private final static float COORD = (float) Math.sin(Math.PI/4.0);
	private float mRotationFriction = 200; // [deg/s^2]
	private boolean isInertiaActive = false;
	private float mRotationYawSpeed, mRotationPitchSpeed; // [deg/s]
	private float mYaw0, mPitch0;
	private long mT0; // [ms]

	/* *************
	 * ATTRIBUTES
	 * *************/
	/** The sphere. */
	private final Sphere mSphere;

	/** The context. */
	private final Context mContext;

	/** The view to render **/
	//private SphereView mGLView;

	/** Diagonal field of view **/
	private float fovDeg;

	/** The rotation angle of the sphere */
	private float mYaw; /* Rotation around y axis */
	private float mPitch; /* Rotation around x axis */


	// While accessing this matrix, the renderer object has to be locked.
	private float[] mRotationMatrix = new float[16];

	private int mSurfaceWidth, mSurfaceHeight;
	/**
	* Constructor to set the handed over context.
	* @param context The context.
	*/
	public SphereRenderer(final Context context, Sphere sphere)
	{
		mContext = context;
		mSphere = sphere;
		mYaw = mPitch = 0.0f;
	  	setRotation(mYaw, mPitch);
	  	fovDeg = DEFAULT_FOV;
	}


	/* *************
	 * RENDERER OVERRIDES
	 * *************/
	@Override
	public void onDrawFrame(final GL10 gl)
	{

		//clear the whole frame
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

		//reset referential
		gl.glMatrixMode(GL10.GL_MODELVIEW);
		gl.glLoadIdentity();

		//compute an eventual new matrix rotation according to inertia
	  	doInertialRotation();

	  	//load the rotation matrix
	  	synchronized (this) {
	  		gl.glLoadMatrixf(mRotationMatrix, 0);
	  	}

	  	//draw the sphere in this new referential.
		this.mSphere.draw(gl);
	}


	@Override
	public void onSurfaceChanged(final GL10 gl, final int width, final int height)
	{
		mSurfaceWidth = width;
		mSurfaceHeight = height;

		setProjection(gl);
		gl.glViewport(0, 0, width, height);

	}

	@Override
	public void onSurfaceCreated(final GL10 gl, final EGLConfig config)
	{


		this.mSphere.loadGLTexture(gl, this.mContext, R.raw.white_pano);

		//texture mode
		gl.glEnable(GL10.GL_TEXTURE_2D);

		// smooth shader
		gl.glShadeModel(GL10.GL_SMOOTH);
					//gl.glEnable(GL10.GL_BLEND);


		// Don't draw back sides.
					/*
				    gl.glEnable(GL10.GL_CULL_FACE);
				    gl.glFrontFace(GL10.GL_CCW);
				    gl.glCullFace(GL10.GL_BACK);*/

	    gl.glEnable(GL10.GL_DEPTH_TEST);

		gl.glClearColor(CLEAR_RED, CLEAR_GREEN, CLEAR_BLUE, CLEAR_ALPHA);
		gl.glClearDepthf(1.0f);

					//gl.glDepthFunc(GL10.GL_LEQUAL);
		gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);


				    /*//done in drawSphere
					gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
					gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);

					gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);
					*/

					//setupTextures(gl);

	}

	/* *************
	 * PRIVATE FUNCTIONS
	 * ************/
	private synchronized void computeRotationMatrix()
	{
		// Rotation matrix in column mode.
		double yawRad = Math.toRadians(mYaw);
		double pitchRad = Math.toRadians(mPitch);

		float cosYaw = (float) Math.cos(yawRad);
		float sinYaw = (float) Math.sin(yawRad);
		float cosPitch = (float) Math.cos(pitchRad);
		float sinPitch = (float) Math.sin(pitchRad);

		mRotationMatrix[0] = cosYaw;
		mRotationMatrix[1] = sinPitch*sinYaw;
		mRotationMatrix[2] = (float) ((-1.0)*cosPitch*sinYaw);
		mRotationMatrix[3] = 0.0f;

		mRotationMatrix[4] = 0.0f;
		mRotationMatrix[5] = cosPitch;
		mRotationMatrix[6] = sinPitch;
		mRotationMatrix[7] = 0.0f;

		mRotationMatrix[8] = sinYaw;
		mRotationMatrix[9] = (float) (-1.0*sinPitch*cosYaw);
		mRotationMatrix[10] = (float) (cosYaw*cosPitch);
		mRotationMatrix[11] = 0.0f;

		mRotationMatrix[12] = 0.0f;
		mRotationMatrix[13] = 0.0f;
		mRotationMatrix[14] = 0.0f;
		mRotationMatrix[15] = 1.0f;
  	}

	/**
	 * keep pitch and yaw to positive values
	 */
	private void normalizeRotation()
	{

		mYaw %= 360.0f;
		if (mYaw < -180.0f)
			mYaw = 360.0f + mYaw;
		else if (mYaw > 180.0f)
			mYaw = -360.0f + mYaw;


		if (mPitch < -90.0f)
			mPitch = -90.0f;
		else if (mPitch > 90.0f)
			mPitch = 90.0f;

		Assert.assertTrue(mYaw >= -180.0f && mYaw <= 180.0f);
		Assert.assertTrue(mPitch >= -90.0f && mPitch <= 90.0f);
	}

	private void setProjection(GL10 gl)
	{
		final float aspectRatio = (float) mSurfaceWidth / (float) (mSurfaceHeight == 0 ? 1 : mSurfaceHeight);

		gl.glMatrixMode(GL10.GL_PROJECTION);
		gl.glLoadIdentity();

		float fovYDeg = getVFovDeg();
		GLU.gluPerspective(gl, fovYDeg, aspectRatio, Z_NEAR, Z_FAR);

		gl.glMatrixMode(GL10.GL_MODELVIEW);
		gl.glLoadIdentity();
	}


	/* *************
	 * PUBLIC METHODS
	 * *************/
	public void setRotation(float pitch, float yaw)
	{
		mYaw = yaw;
		mPitch = pitch;
		normalizeRotation();
		computeRotationMatrix();
	}


	public synchronized void startInertialScroll(float pitchSpeed, float yawSpeed)
	{
		isInertiaActive = true;
		mRotationYawSpeed = yawSpeed;
		mRotationPitchSpeed = pitchSpeed;
		mYaw0 = mYaw;
		mPitch0 = mPitch;
		mT0 = System.currentTimeMillis();
	}


	public synchronized void stopInertialScrolling()
	{
	  	isInertiaActive = false;
	}



	public void setRotationFriction(float coef) {
		this.mRotationFriction = coef;

	}




	/* ************
	 * GETTERS
	 * ************/
	public float getPitch()
	{
	  	return mPitch;
	}

	public float getYaw()
	{
		return mYaw;
	}

	public int getSurfaceWidth()
	{
		return mSurfaceWidth;
	}

	public int getSurfaceHeight()
	{
		return mSurfaceHeight;
	}

	public float getFov()
	{
		return fovDeg;
	}

	public float getHFovDeg()
	{
		float viewDiagonal = (float) Math.sqrt(mSurfaceHeight*mSurfaceHeight + mSurfaceWidth*mSurfaceWidth);
		float hFovDeg = (float) mSurfaceWidth/viewDiagonal * fovDeg;
		return hFovDeg;
	}

	public float getVFovDeg() {
		float viewDiagonal = (float) Math.sqrt(mSurfaceHeight*mSurfaceHeight + mSurfaceWidth*mSurfaceWidth);
		float vFovDeg = (float) mSurfaceHeight/viewDiagonal * fovDeg;
		return vFovDeg;
	}




/*

	private int[] textureIds = new int[6];

  private FloatBuffer cubeVertexBuffer;
  private ShortBuffer[] faceVertexIndices = new ShortBuffer[6];
  private FloatBuffer[] faceTextureCoordinates = new FloatBuffer[6];
  */




	private synchronized void doInertialRotation()
	{
		if (!isInertiaActive)
			return;

		long currentTime = System.currentTimeMillis();
		float deltaT = (currentTime-mT0)/1000.0f;
		if (deltaT == 0.0f)
			return;


		float rPitch = deltaT*mRotationFriction;
		float rYaw = deltaT*mRotationFriction;

		if (Math.abs(mRotationPitchSpeed) < rPitch && Math.abs(mRotationYawSpeed) < rYaw)
		{
			stopInertialScrolling();
		}

		float deltaPitch, deltaYaw;

		if (Math.abs(mRotationPitchSpeed) >= rPitch)
		{
			float rotSpeedLat_t = mRotationPitchSpeed - Math.signum(mRotationPitchSpeed)*rPitch;
			deltaPitch = (mRotationPitchSpeed+rotSpeedLat_t)/2.0f*deltaT;
		}
		else
		{
			float tMax = Math.abs(mRotationPitchSpeed)/mRotationFriction;
			deltaPitch = 0.5f*tMax*mRotationPitchSpeed;
		}

		if (Math.abs(mRotationYawSpeed) >= rYaw)
		{
			float rotSpeedLon_t = mRotationYawSpeed - Math.signum(mRotationYawSpeed)*rYaw;
			deltaYaw = (mRotationYawSpeed+rotSpeedLon_t)/2.0f*deltaT;
		}
		else
		{
			float tMax = Math.abs(mRotationYawSpeed)/mRotationFriction;
			deltaYaw = 0.5f*tMax*mRotationYawSpeed;
		}

		setRotation(mPitch0+deltaPitch, mYaw0+deltaYaw);
  }






  /*
  void rotate() {
  	float coords[] = {
              COORD, COORD, COORD,    // left, top, front
              -COORD, COORD, COORD,   // right, top, front
              -COORD, COORD, -COORD,  // right, top, back
              COORD, COORD, -COORD,   // left, top, back
              COORD, -COORD, COORD,   // left, bottom, front
              -COORD, -COORD, COORD,  // right, bottom, front
      		-COORD, -COORD, -COORD, // right, bottom, back
      		COORD, -COORD, -COORD   // left, bottom, back
      	};

  	double rotationLongitute = Math.toRadians(mYaw);

  	for (int i = 0; i < coords.length/3; i++) {
  		double x = coords[i*3];
  		double y = coords[i*3 + 1];
  		double z = coords[i*3 + 2];

  		double x2 = Math.cos(rotationLongitute)*x + Math.sin(rotationLongitute)*z;
  		double y2 = y;
  		double z2 = -Math.sin(rotationLongitute)*x + Math.cos(rotationLongitute)*z;

  		coords[i*3] = (float) x2;
  		coords[i*3 + 1] = (float) y2;
  		coords[i*3 + 2] = (float) z2;
  	}

  	cubeVertexBuffer.put(coords);
  	cubeVertexBuffer.position(0);
  }*/


/*
      //gl.glRotatef(rotationLongitude, 0.0f, 1.0f, 0.0f);

      gl.glVertexPointer(3, GL10.GL_FLOAT, 0, cubeVertexBuffer);

      for (CubicPano.TextureFaces face : CubicPano.TextureFaces.values()) {
      	int faceNo = face.ordinal();

       	gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[faceNo]);
      	gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, faceTextureCoordinates[faceNo]);

         	// For each face, we have to draw 4 vertices
      	// (triangle strip with two triangles).
      	gl.glDrawElements(GL10.GL_TRIANGLE_STRIP, 4, GL10.GL_UNSIGNED_SHORT, faceVertexIndices[faceNo]);
      }
	}



	void setupTextures(GL10 gl) {
		gl.glMatrixMode(GL10.GL_TEXTURE);
		gl.glLoadIdentity();

		gl.glGenTextures(6, textureIds, 0);

		for (CubicPanoNative.TextureFaces face : CubicPanoNative.TextureFaces.values()) {
			int faceNo = face.ordinal();

			gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[faceNo]);
			gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
			gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
			//gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
			//gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);

			gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
			gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
			//gl.glTexEnvf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_DECAL);
			gl.glBlendFunc(GL10.GL_ONE, GL10.GL_SRC_COLOR);

			Bitmap bm = null;

			Assert.assertTrue(cubicPano != null);
			bm = cubicPano.getFace(face);

			//bm =  BitmapFactory.decodeResource(view.getActivity().getResources(), R.drawable.texture);
			//Assert.assertTrue(isPowerOfTwo(bm.getWidth()));
			/*
			switch (face) {
			case top :
				bm =  BitmapFactory.decodeResource(view.getActivity().getResources(), R.drawable.top);
				break;
			case bottom :
				bm =  BitmapFactory.decodeResource(view.getActivity().getResources(), R.drawable.bottom);
				break;
			case left :
				bm =  BitmapFactory.decodeResource(view.getActivity().getResources(), R.drawable.left);
				break;
			case right :
				bm =  BitmapFactory.decodeResource(view.getActivity().getResources(), R.drawable.right);
				break;
			case front :
				bm =  BitmapFactory.decodeResource(view.getActivity().getResources(), R.drawable.front);
				break;
			case back :
				bm =  BitmapFactory.decodeResource(view.getActivity().getResources(), R.drawable.back);
				break;
			default :
				Assert.fail();
			}
			*/
	/*
			GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bm, 0);

			// If we save the state of the activity, we should not delete the
			// original bitmaps. Otherwise, we have to reload them from the network.
			//bm.recycle();

			// For each face, we have to define 8 coordinates although only 4 are used
			// at a time -- glDrawElements() uses the same indices as for the vertex array
			// to select texture coordinates. Coordinates that are not used are marked
			// as dummy entries.
			float coordinates[][] = {
					{   // top (vertices 0, 1, 3, 2)
						1.0f, 1.0f,
  					0.0f, 1.0f,
  					0.0f, 0.0f,
  					1.0f, 0.0f,
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f  // dummy
  				},
  				{   // bottom (vertices 6, 5, 7, 4)
						0.0f, 0.0f, // dummy
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f, // dummy
  					1.0f, 1.0f,
  					0.0f, 1.0f,
  					0.0f, 0.0f,
  					1.0f, 0.0f
  				},
  				{   // front (vertices 0, 4, 1, 5)
						0.0f, 1.0f,
  					1.0f, 1.0f,
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f,
  					1.0f, 0.0f,
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f  // dummy
  				},
  				{   // back (vertices 2, 6, 3, 7)
						0.0f, 0.0f, // dummy
  					0.0f, 0.0f, // dummy
  					0.0f, 1.0f,
  					1.0f, 1.0f,
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f,
  					1.0f, 0.0f
  				},
  				{   // right (vertices 1, 5, 2, 6)
						0.0f, 0.0f, // dummy
  					0.0f, 1.0f,
  					1.0f, 1.0f,
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f,
  					1.0f, 0.0f,
  					0.0f, 0.0f  // dummy
  				},
  				{   // left (vertices 3, 7, 0, 4)
						1.0f, 1.0f,
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f, // dummy
  					0.0f, 1.0f,
  					1.0f, 0.0f,
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f, // dummy
  					0.0f, 0.0f
  				}
  		};

			ByteBuffer byteBuffer = ByteBuffer.allocateDirect(8*2*Float.SIZE/8);
  		byteBuffer.order(ByteOrder.nativeOrder());
  		faceTextureCoordinates[faceNo] = byteBuffer.asFloatBuffer();

  		switch (face) {
  		case top :
  			faceTextureCoordinates[faceNo].put(coordinates[0]);
  			break;
  		case bottom :
  			faceTextureCoordinates[faceNo].put(coordinates[1]);
  			break;
  		case front :
  			faceTextureCoordinates[faceNo].put(coordinates[2]);
  			break;
  		case back :
  			faceTextureCoordinates[faceNo].put(coordinates[3]);
  			break;
  		case right :
  			faceTextureCoordinates[faceNo].put(coordinates[4]);
  			break;
  		case left :
  			faceTextureCoordinates[faceNo].put(coordinates[5]);
  			break;
  		}

  		faceTextureCoordinates[faceNo].position(0);
		}

	}

*/


}
