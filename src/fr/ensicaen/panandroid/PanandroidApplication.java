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

import java.io.File;

import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import fr.ensicaen.panandroid.capture.CameraManager;

/**
 * Based on Guillaume Lesniak's Focal application.
 * Manages the application itself (on top of the activity), mainly to force Camera getting
 * closed in case of crash.
 */
public class PanandroidApplication extends Application
{
    /**************
     * PARAMETERS *
     **************/
    public static final String APP_DIRECTORY = Environment.getExternalStorageDirectory()
            + File.separator + "Panandroid";
    private final static String TAG = PanandroidApplication.class.getSimpleName();

    /**************
     * ATTRIBUTES *
     **************/
    /** Default exception handler */
    private Thread.UncaughtExceptionHandler mDefaultHandler;

    /** Camera manager */
    private CameraManager mCameraManager;

    /** Exception handler */
    private Thread.UncaughtExceptionHandler mExceptionHandler =
            new Thread.UncaughtExceptionHandler() {
        public void uncaughtException(Thread thread, Throwable e) {
            if (mCameraManager != null) {
                Log.e(TAG, "Uncaught exception! Closing down camera safely firsthand...");
                e.printStackTrace();
                mCameraManager.close();
            }

            mDefaultHandler.uncaughtException(thread, e);
        }
    };

    /******************
     * PUBLIC METHODS *
     ******************/
    /**
     * Called when the application is starting, before any activity, service,
     * or receiver objects (excluding content providers) have been created.
     */
    @Override
    public void onCreate() {
        super.onCreate();

        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(mExceptionHandler);
        mCameraManager = CameraManager.getInstance(this);
    }

    /**********
     * GETTER *
     **********/
    /**
     * Getter of the context of the application.
     * @return context of the application.
     */
    public static Context getContext() {
    	return PanandroidApplication.getContext();
    }
}
