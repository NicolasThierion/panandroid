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

package fr.ensicaen.panandroid;

import fr.ensicaen.panandroid.capture.CameraManager;
import android.app.Application;
import android.util.Log;

/**
 * Based on Guillaume Lesniak's Focal application.
 * Manages the application itself (on top of the activity), mainly to force Camera getting
 * closed in case of crash.
 */
public class PanandroidApplication extends Application
{
	/* *******
	 * PARAMETERS
	 * ******/
    private final static String TAG = PanandroidApplication.class.getSimpleName();
    
	/* *******
	 * ATTRIBUTES
	 * ******/
    /** Exception handler **/
    private Thread.UncaughtExceptionHandler mDefaultExHandler;
    private CameraManager mCamManager;

    private Thread.UncaughtExceptionHandler mExHandler = new Thread.UncaughtExceptionHandler()
    {
        public void uncaughtException(Thread thread, Throwable ex)
        {
            if (mCamManager != null)
            {
                Log.e(TAG, "Uncaught exception! Closing down camera safely firsthand");
                mCamManager.close();
            }
            mDefaultExHandler.uncaughtException(thread, ex);
        }
    };


    @Override
    public void onCreate()
    {
        super.onCreate();
        mDefaultExHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(mExHandler);
        mCamManager = CameraManager.getInstance(this);
    }

}
