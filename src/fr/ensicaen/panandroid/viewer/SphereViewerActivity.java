/*
 * Copyright (C) 2013 Nicolas THIERION, Saloua BENSEDDIK, Jean Marguerite.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package fr.ensicaen.panandroid.viewer;

import java.io.IOException;

import org.json.JSONException;

import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.insideview.Inside3dView;
import fr.ensicaen.panandroid.meshs.Sphere;
import fr.ensicaen.panandroid.snapshot.SnapshotManager;
import fr.ensicaen.panandroid.tools.BitmapDecoder;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;



public class SphereViewerActivity extends Activity
{

	private static final String TAG = SphereViewerActivity.class.getSimpleName();
	
	private static final float YAW_RANGE = 30.0f;
	private static final float PITCH_RANGE = 15.0f;
	
	/** Size of the sphere **/
	private static final float SPHERE_RADIUS = 0.15f;
	
	/** Resolution of the sphere **/
	private static final int SPHERE_RESOLUTION = 4;
			
	/** The OpenGL view. */
	private Inside3dView mSphereView;

	private Sphere mSphere;

	/**
	 * Called when the activity is first created.
	 * @param savedInstanceState The instance state.
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
	
		// view in fullscreen
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
	    
		//bind activity to its layout
		setContentView(R.layout.viewer_activity);

		// init the sphere
		mSphere = new Sphere(SPHERE_RESOLUTION, SPHERE_RADIUS);
	
		//set GL view & its renderer
	    mSphereView = new Inside3dView(this, mSphere);
	    //setContentView(mSphereView);
	    
	    //and populate the layout with it.
  		ViewGroup parent = (ViewGroup) (mSphereView.getParent());
  		if(parent !=null)
  			parent.removeView(mSphereView);

  		ViewGroup container = ((ViewGroup) findViewById(R.id.gl_renderer_container));
  		container.addView(mSphereView);
	    
	    
		/*
		String textureFile = getIntent().getStringExtra("jpg");
		float minPitch,minYaw,maxPitch, maxYaw;
		minPitch = getIntent().getFloatExtra("minPitch", -1000.0f);
	    minYaw = getIntent().getFloatExtra("minYaw", -1000.0f);
	    maxPitch = getIntent().getFloatExtra("maxPitch", 1000.0f);
	    maxYaw = getIntent().getFloatExtra("maxYaw", 1000.0f);
	    */
		// Load project & put panorama as texture
	    String projectFile = getIntent().getStringExtra("projectFile");
	    if(projectFile!="" && projectFile!=null)
	    	loadPanorama(projectFile);
	    else
	    	Log.w(TAG, "created viewer without passing projectFile to intent");
	    	
	    
    	//loadPanorama("/sdcard/sampleCafet/PanoData.json");

	    mSphereView.setEnableInertialRotation(true);
	    mSphereView.setEnableTouchRotation(true);
	    mSphereView.setInertiaFriction(50.0f);
	    mSphereView.setEnablePinchZoom(true);
	    mSphereView.setSensorialButtonVisible(true);
	}
  
	private void loadPanorama(String projectFile)
	{
		SnapshotManager manager;
		try {
			manager = new SnapshotManager(projectFile);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
			
			return;
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return;
		}
	    String textureFile = manager.getPanoramaJpgPath();
	    float minPitch,minYaw,maxPitch, maxYaw;
	
	    minPitch = manager.getMinPitch();
	    minYaw= manager.getMinYaw();
	    maxPitch = manager.getMaxPitch();
	    maxYaw = manager.getMaxYaw();
	
	    Log.i(TAG, "loading panorama "+textureFile);
	    Bitmap texture = BitmapDecoder.safeDecodeBitmap(textureFile);
	    mSphere.setGlTexture(texture);
	   
	    
	    minPitch = (minPitch<-89?-1000 : Math.max(minPitch-PITCH_RANGE, -90));
	    maxPitch = (maxPitch>89?1000 : Math.min(maxPitch+PITCH_RANGE, 90));
	   
	    if(maxYaw-minYaw>360-YAW_RANGE)
	    {
	    	maxYaw=1000 ; minYaw =-1000;
	    }
	    else
	    {
		    minYaw = minYaw-YAW_RANGE;
		    maxYaw = maxYaw+YAW_RANGE;
	    }
	    //mSphereView.setReferenceRotation(0.0f,270-manager.getCameraHFov()/2);

	    //TODO re enable & debug
	    mSphereView.setPitchRange(new float[]{minPitch, maxPitch});
	    mSphereView.setYawRange(new float[]{minYaw, maxYaw});
  }

  
  /**
   * Remember to resume the glSurface.
   */
  @Override
  protected void onResume() {
    super.onResume();
    this.mSphereView.onResume();
  }

  /**
   * Also pause the glSurface.
   */
  @Override
  protected void onPause() {
    this.mSphereView.onPause();
    super.onPause();
  }
}
