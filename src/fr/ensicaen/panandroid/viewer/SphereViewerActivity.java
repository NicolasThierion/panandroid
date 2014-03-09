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

import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.insideview.Inside3dView;
import fr.ensicaen.panandroid.meshs.Sphere;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;



public class SphereViewerActivity extends Activity {

	/** Size of the sphere **/
	private static final float SPHERE_RADIUS = 0.15f;
	
	/** Resolution of the sphere **/
	private static final int SPHERE_RESOLUTION = 4;
			
			
	/** The OpenGL view. */
	private Inside3dView mSphereView;

  /**
   * Called when the activity is first created.
   * @param savedInstanceState The instance state.
   */
  @Override
  public void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    //view in fullscreen
    this.requestWindowFeature(Window.FEATURE_NO_TITLE);
    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    
    //init the sphere
    Sphere sphere = new Sphere(SPHERE_RESOLUTION, SPHERE_RADIUS);
    
    //TODO ; remove
    Bitmap texture = BitmapFactory.decodeResource(super.getResources(), R.raw.spherical_pano);
    sphere.setGlTexture(texture);
    
    
 
    //set GL view & its renderer
    this.mSphereView = new Inside3dView(this, sphere);
    this.setContentView(this.mSphereView);
    
    
    mSphereView.enableInertialRotation(true);
    mSphereView.enableTouchRotation(true);
    mSphereView.setInertiaFriction(50.0f);
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
