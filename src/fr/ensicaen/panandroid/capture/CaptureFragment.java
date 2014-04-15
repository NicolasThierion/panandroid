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

package fr.ensicaen.panandroid.capture;

import java.io.File;
import java.io.IOException;

import junit.framework.Assert;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.snapshot.Snapshot;
import fr.ensicaen.panandroid.snapshot.SnapshotEventListener;
import fr.ensicaen.panandroid.snapshot.SnapshotManager;
import fr.ensicaen.panandroid.stitcher.StitcherActivity;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.SensorEventListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.View.OnSystemUiVisibilityChangeListener;
import fr.ensicaen.panandroid.PanandroidApplication;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.snapshot.Snapshot;
import fr.ensicaen.panandroid.snapshot.SnapshotEventListener;
import fr.ensicaen.panandroid.snapshot.SnapshotManager;

/**
 * Fragment through what the user can capture snapshots in order to make his panorama.
 * The fragment opens a 3D and draws a cubic skybox seen from inside.
 * The view rotates with the device orientation. Dots are put all around the view,
 * and a snapshot is taken automatically when view finder and dot are aligned.
 * @author Nicolas Thierion <nicolas.thierion@ecole.ensicaen.fr>
 * @TODO Manually set field of view values of known devices.
 * @TODO Remove SnapshotManager.
 */
public class CaptureFragment extends Fragment implements OnSystemUiVisibilityChangeListener, SnapshotEventListener {
    /********************
     * DEBUG PARAMETERS *
     ********************/
    private static final String TAG = CaptureFragment.class.getSimpleName();

    private static final float DEFAULT_PITCH_STEP = 360.0f / 12.0f;
    private static final float DEFAULT_YAW_STEP = 360.0f / 12.0f;

    /**************
     * PARAMETERS *
     **************/
    /** Directory where pictures will be saved */
    private static final String TEMP_PREFIX = "temp";

    /** Immersive mode will hide status and navigation bar */
    private static boolean IMMERSIVE_ENABLE = true;

    /** Delay for hiding bars in seconds */
    private static final int IMMERSIVE_DELAY = 4000;

    /** Hide both navigation and status bar. */
    private static final int NAVIGATION_HIDE_TYPE = View.SYSTEM_UI_FLAG_LOW_PROFILE;

    /**
     * Some devices don't return good field of view values.
     * In these cases, DEFAULT_FOV will be used
     */
    private static final float DEFAULT_HFOV = 59.62f; // Values Nexus 5
    private static final float DEFAULT_VFOV = 46.6f;

    /**************
     * ATTRIBUTES *
     **************/
    /** OpenGL view where to draw the sphere */
    private CaptureView mCaptureView;

    /** Camera manager */
    private CameraManager mCameraManager;

    /** Instance of SnapshotManager, observer of onSnapshotTaken() */
    private SnapshotManager mSnapshotManager;

    /** Directory where images and JSON file are stored */
    private String mWorkingDirectory = PanandroidApplication.APP_DIRECTORY;

    /** Name of the panorama */
    private String mPanoramaName;

    private WakeLock mWakeLock;
    
    /** Shutter button */
	private ShutterButton mShutterButton;

	private View mStartingProgressSpinner;
	
    /** View of the fragment */
    private View mRoot;

    
    /**
     * Called to have the fragment instantiate its user interface view.
     * @return Created view.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        // Bind activity to its layout.
        mRoot = inflater.inflate(R.layout.activity_capture, container, false);

        View decorView = getActivity().getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(this);

        if (IMMERSIVE_ENABLE) {
            getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            toggleImmersive();
        }

        // Set up panorama name and working directory.
        mPanoramaName = generatePanoramaName();
        mWorkingDirectory = generateWorkingDirectory(mPanoramaName);

        // Setup camera manager.
        mCameraManager = CameraManager.getInstance(getActivity());

        try {
            mCameraManager.setTargetDirectory(mWorkingDirectory);
            mCameraManager.open();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Setup JSON and snapshot manager.
        int resX = mCameraManager.getCameraResX();
        int resY = mCameraManager.getCameraResY();
        float hfov = mCameraManager.getHorizontalViewAngle();
        float vfov = mCameraManager.getVerticalViewAngle();

        if (hfov < 1) {
            Log.e(TAG, "Invalid hfov : " + hfov + "\nSetting hfov : " + DEFAULT_HFOV);
            hfov = DEFAULT_HFOV;
        }

        if (vfov < 1) {
            Log.e(TAG, "Invalid vfov : " + vfov + "\nSetting hfov : " + DEFAULT_VFOV);
            hfov = DEFAULT_VFOV;
        }

        mSnapshotManager = new SnapshotManager(SnapshotManager.DEFAULT_JSON_FILENAME,
                resX, resY ,hfov, vfov,
                DEFAULT_PITCH_STEP, DEFAULT_YAW_STEP);
        mCameraManager.addSnapshotEventListener(mSnapshotManager);

        // Setup OpenGL view & its renderer.
        mCaptureView = new CaptureView(getActivity(), mCameraManager);

        // Populate layout with OpenGL view.
        ViewGroup parent = (ViewGroup) (mCaptureView.getParent());

        if (parent != null)
            parent.removeView(mCaptureView);

        ViewGroup renderer_container = ((ViewGroup) mRoot.findViewById(R.id.renderer_container));
        renderer_container.addView(mCaptureView);

        mCaptureView.setPitchStep(DEFAULT_PITCH_STEP);
        mCaptureView.setYawStep(DEFAULT_YAW_STEP);

    	// hide shutter button for the first shoot.
 		mShutterButton = (ShutterButton) mRoot.findViewById(R.id.btn_shutter);
		mShutterButton.setVisibility(View.GONE);
		
		//wait for the first snapshot to be taken.
		mCameraManager.addSnapshotEventListener(this);

        // do not set the view as content cause it is bind to layout.
        // this.setContentView(this.mCaptureView);
        // mCameraManager.startPreview();

        return mRoot;
    }

    /**
     * Called when the status bar changes visibility because of a call to setSystemUiVisibility().
     */
    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        if ((visibility & NAVIGATION_HIDE_TYPE) == 0) {
            Handler h = new Handler();

            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    toggleImmersive();
                }
            }, IMMERSIVE_DELAY);
        }
    }

    /**
     * Called when the fragment is no longer in use.
     * This is called after onStop() and before onDetach().
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mCaptureView.onDestroy();
    }

    /**
     * Called when the fragment is visible to the user and actively running.
     * This is generally tied to Activity.onResume of the containing Activity's lifecycle.
     */
    @Override
    public void onResume() {
        super.onResume();

        if (mCaptureView != null) {
            mCaptureView.onResume();
        }

        mRoot.requestLayout();
    }

    /**
     * Called when the Fragment is no longer resumed.
     * This is generally tied to Activity.onPause of the containing Activity's lifecycle.
     */
    @Override
    public void onPause()
    {
        super.onPause();

        // Pause camera and OpenGL context.
        mCaptureView.onPause();
        mCameraManager.onPause();

        // Save project to JSON file
        if(mSnapshotManager.getSnapshotsList().size() > 0) {
            String result = mSnapshotManager.toJSON(SnapshotManager.DEFAULT_JSON_FILENAME);
            Log.i(TAG, "Saving project to " + result);
        }
    }

    /**
     * Called when user goes in immersive mode.
     */
    @SuppressLint("InlinedApi")
    public void toggleImmersive() {
        // Navigation bar hiding: Backwards compatible to ICS.
        if (Build.VERSION.SDK_INT >= 14) {
            try {
                int uiOptions = getActivity().getWindow().getDecorView().getSystemUiVisibility();
                int newUiOptions = uiOptions;
                boolean isImmersiveModeEnabled = ((uiOptions | NAVIGATION_HIDE_TYPE) == uiOptions);

                if (isImmersiveModeEnabled) {
                    Log.i(TAG, "Turning immersive mode on.");
                } else {
                    Log.i(TAG, "Turning immersive mode off.");
                }

                newUiOptions ^= NAVIGATION_HIDE_TYPE;
                getActivity().getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
            } catch (NullPointerException e) {}
        } else {
            getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 0);
        }
    }

	/**
	 * Show a confirmation dialog before exit. Exit to stitcher activity.
	 */
	public void onBackPressed()
	{
		final Activity thisActivity = getActivity();
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(thisActivity);
		alertDialog.setMessage(getString(R.string.exit_capture)).setCancelable(false);

		alertDialog.setPositiveButton(getString(R.string.exit_yes), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				//if we have at least 2 snapshots to stitch, switch to stitcher activity
				if(mSnapshotManager.getSnapshotsList().size()>1)
				{
				    Intent intent = new Intent(thisActivity,
				            StitcherActivity.class);
				    Log.i(TAG, "saving project file at "+ mWorkingDirectory+File.separator+SnapshotManager.DEFAULT_JSON_FILENAME);
				    intent.putExtra("PROJECT_FILE",mWorkingDirectory+File.separator+SnapshotManager.DEFAULT_JSON_FILENAME);
				    //intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
				    thisActivity.startActivity(intent);
				    //finish this activity in order to free maximum of memory
				    thisActivity.finish();
				    CaptureFragment.this.onDestroy();
				    
				}
				else
				{
					thisActivity.onBackPressed();
				}
			}
		});
		alertDialog.setNegativeButton(R.string.exit_no, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				dialog.cancel();
			}
		});

		AlertDialog alert = alertDialog.create();
		alert.show();
		

	}

    /* ******************
     * PRIVATE METHODS  *
     * ******************/
    @Override
	public void onSnapshotTaken(byte[] pictureData, Snapshot snapshot)
	{
		mCameraManager.removeSnapshotEventListener(this);
		mShutterButton.setVisibility(View.VISIBLE);
		
	}
    

    /*******************
     * PRIVATE METHODS *
     *******************/
    /**
     * Generates a new working directory.
     * @param panoramaName Given name for the panorama.
     * @return Current working directory.
     */
    private String generateWorkingDirectory(String panoramaName) {
        File directory = new File(mWorkingDirectory + File.separator + panoramaName);
        directory.mkdirs();

        return mWorkingDirectory + File.separator + panoramaName;
    }

    /**
     * Generate panorama name.
     * @param prefix
     * @return
     */
    private String generatePanoramaName() {
        int id = 1;
        String path = mWorkingDirectory + File.separator + TEMP_PREFIX;
        File directory = new File(path);

        if (directory.exists()) {
            do {
                path = mWorkingDirectory + File.separator + TEMP_PREFIX + (id++);
                directory = new File(path);
            } while (directory.exists());

            return TEMP_PREFIX + (--id);
        }

        return TEMP_PREFIX;
    }

    /**********
     * GETTER *
     **********/
    public SnapshotManager getSnapshotManager() {
        return mSnapshotManager;
    }

    public String getWorkingDirectory() {
        return mWorkingDirectory;
    }

}
