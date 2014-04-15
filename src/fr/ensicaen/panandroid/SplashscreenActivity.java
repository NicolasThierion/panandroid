/*
 * Copyright (C) 2013 Saloua BENSEDDIK, Jean MARGUERITE, Nicolas THIERION
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */

package fr.ensicaen.panandroid;

import fr.ensicaen.panandroid.capture.CameraManager;
import fr.ensicaen.panandroid.tools.SensorFusionManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

/**
 * Splashscreen of the application.
 * @author Jean Marguerite <jean.marguerite@ecole.ensicaen.fr>
 */
public class SplashscreenActivity extends Activity {
    /**************
     * PARAMETERS *
     **************/
    /** Splash screen timer */
    private static int SPLASH_TIME_OUT = 50;

    /******************
     * PUBLIC METHODS *
     ******************/
    /**
     * Called when SplashscreenActivity is starting.
     * @param savedInstanceState Contains the data it most recently supplied in.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splashscreen);
       

        new Thread()
        {
        	public void run()
        	{
				CameraManager camManager = CameraManager.getInstance(SplashscreenActivity.this);
			 	SensorFusionManager sensorManager = SensorFusionManager.getInstance(SplashscreenActivity.this);
                boolean loaded = false;
		        do
		        {
		        	if(!sensorManager.isReady())
		        		sensorManager.start();
		        	
		        	if(!camManager.isReady())
		        	{
			            camManager.open();
			            camManager.startPreview();
		        	}

		        	loaded = camManager.isReady() && sensorManager.isReady();
		        	try {
						Thread.sleep(SPLASH_TIME_OUT);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		        	
		        }while(!loaded);
        	}
        }.start();
        Intent intent = new Intent(SplashscreenActivity.this, MainActivity.class);
        startActivity(intent);

        // Finish this activity.
        finish();
        /*
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
               
            }
        }, SPLASH_TIME_OUT);*/
    }
}
