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
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.View.OnSystemUiVisibilityChangeListener;

/**
*
* Fragment through what the user can capture snapshots in order to make his panorama.
* The fragment opens a 3D and draws a cubic skybox seen from inside.
* The view rotates with the device orientation. Dots are put all around the view,
* and a snapshot is taken automatically when viewfinder and dot are alligned.
*
* @author Nicolas THIERION.
*
*/
public class CaptureFragment extends Fragment implements OnSystemUiVisibilityChangeListener, SnapshotEventListener {
    /* *******************
     * DEBUG PARAMETERS  *
     * *******************/
    public static final String TAG = CaptureActivity.class.getSimpleName();

    private static final float DEFAULT_PITCH_STEP = 360.0f/12.0f;;
    private static final float DEFAULT_YAW_STEP = 360.0f/12.0f;

    /* *************
     * PARAMETERS *
     * *************/

    /** Directory where pictures will be saved **/
    private static final String APP_DIRECTORY = Environment.getExternalStorageDirectory()
            + File.separator + "Panandroid";
    private static final String TEMP_PREFIX = "temp";

    /** Immersive mode will hide status bar and navigation bar **/
    private static boolean IMMERSIVE_ENABLE = true;

    /** Delay for hiding bars **/
    private static final int IMERSIVE_DELAY = 4000; //[s]
    private static final int NAVIGATION_HIDE_TYPE = View.SYSTEM_UI_FLAG_LOW_PROFILE;

    public final static String FOLDER = "fr.ensicaen.panandroid.FOLDER";

    /* *************
     * ATTRIBUTES  *
     * *************/

    /** The OpenGL view where to draw the sphere. */
    private CaptureView mCaptureView;

    /** The Camera manager **/
    private CameraManager mCameraManager;

    /** Instance of SnapshotManager, observer of onSnapshotTaken()**/
    //TODO : remove
    private SnapshotManager mSnapshotManager;

    /** directory where images and JSon file are stored **/
    private String mWorkingDir = APP_DIRECTORY;
    private String mPanoName;

    private PowerManager mPowerManager;

    private WakeLock mWakeLock;

	private ShutterButton mShutterButton;

	private View mStartingProgressSpinner;

    public CaptureFragment() {
        // View in full screen, and don't turn screen off.
        /*
        getActivity().requestWindowFeature(Window.FEATURE_NO_TITLE);
        getActivity().getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        */
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mPowerManager = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");

        // Bind activity to its layout
        View root = inflater.inflate(R.layout.capture_activity, container, false);

        // Hide both the navigation bar and the status bar.
        View decorView = getActivity().getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(this);

        if (IMMERSIVE_ENABLE) {
            getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            toggleImmersive();
        }

        // Set up panorama name and working directory.
        mPanoName = genPanoName(TEMP_PREFIX);
        mWorkingDir = genWorkingDir(mPanoName);

        // Setup camera manager.
        mCameraManager = CameraManager.getInstance(getActivity());

        try {
            mCameraManager.setTargetDir(mWorkingDir);
            mCameraManager.open();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //TODO : remove
        // Setup JSON and snapshot manager.
        /*
        mSnapshotManager = new SnapshotManager();
        mCameraManager.addSnapshotEventListener(mSnapshotManager);*/

        // Setup GL view & its renderer.
        mCaptureView = new CaptureView(getActivity(), mCameraManager);

        // Populate the layout with GL view.
        ViewGroup parent = (ViewGroup) (mCaptureView.getParent());
        if (parent != null)
            parent.removeView(mCaptureView);

        ViewGroup container1 = ((ViewGroup) root.findViewById(R.id.gl_renderer_container));
        container1.addView(mCaptureView);

        mCaptureView.setPitchStep(DEFAULT_PITCH_STEP);
        mCaptureView.setYawStep(DEFAULT_YAW_STEP);

    	// hide shutter button for the first shoot.
 		mShutterButton = (ShutterButton) root.findViewById(R.id.btn_shutter);
		mShutterButton.setVisibility(View.GONE);
		
		//wait for the first snapshot to be taken.
		mCameraManager.addSnapshotEventListener(this);

        // do not set the view as content cause it is bind to layout.
        // this.setContentView(this.mCaptureView);
        // mCameraManager.startPreview();

        return root;
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility)
    {
        if ((visibility & NAVIGATION_HIDE_TYPE) == 0) {
            Handler h = new Handler();

            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    toggleImmersive();
                }
            }, IMERSIVE_DELAY);
        }
    }

    @SuppressLint("InlinedApi")
    public void toggleImmersive()
    {
        // Navigation bar hiding: Backwards compatible to ICS.
        if (Build.VERSION.SDK_INT >= 14) {
            int uiOptions = getActivity().getWindow().getDecorView().getSystemUiVisibility();
            int newUiOptions = uiOptions;
            boolean isImmersiveModeEnabled = ((uiOptions | NAVIGATION_HIDE_TYPE) == uiOptions);
            if (isImmersiveModeEnabled) {
                Log.i(TAG, "Turning immersive mode mode off. ");
            } else {
                Log.i(TAG, "Turning immersive mode mode on.");
            }

            newUiOptions ^= NAVIGATION_HIDE_TYPE;
            getActivity().getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
        } else {
            getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 0);
        }
    }

    
    /**
	 * Remember to resume the glSurface.
	 */
	@Override
	public void onResume()
	{
		Assert.assertTrue(mCaptureView!=null);
		mCaptureView.onResume();
		mWakeLock.acquire();
		super.onResume();

	}

	/**
	 * Also pause the glSurface.
	 */
	@Override
	public void onPause()
	{
		Log.i(TAG, "onPause()");
		mWakeLock.release();
		Assert.assertTrue(mCaptureView!=null);
		//pause camera, GL context, etc.
		mCaptureView.onPause();
		mCameraManager.onPause();
		//save project to json
		if(mSnapshotManager.getSnapshotsList().size()>0)
		{
			String res = mSnapshotManager.toJSON(SnapshotManager.DEFAULT_JSON_FILENAME);
			Log.i(TAG,  "saving project to "+res);
		}
		//call parent
		super.onPause();
	}

	@Override
	public void onDestroy()
	{
		mCaptureView.onDestroy();
		super.onDestroy();
	}
	
	/**
	 * Show a confirmation dialog before exit. Exit to stitcher activity.
	 */
	public void onBackPressed()
	{
		final Activity thisActivity = getActivity();
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(thisActivity);
		alertDialog.setMessage(getString(R.string.exit_confrm)).setCancelable(false);

		alertDialog.setPositiveButton(getString(R.string.exit_yes), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				//if we have at least 2 snapshots to stitch, switch to stitcher activity
				if(mSnapshotManager.getSnapshotsList().size()>1)
				{
				    Intent intent = new Intent(thisActivity,
				            StitcherActivity.class);
				    intent.putExtra("projectFile",mWorkingDir+File.separator+SnapshotManager.DEFAULT_JSON_FILENAME);
				    intent.putExtra("commingFrom",CaptureActivity.class.getSimpleName());
				    //intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
				    thisActivity.startActivity(intent);
				    //finish this activity in order to free maximum of memory
				    thisActivity.finish();
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
    
    private String genWorkingDir(String panoName)
    {
        File dir = new File(APP_DIRECTORY + File.separator + panoName);
        dir.mkdirs();
        return APP_DIRECTORY + File.separator + panoName;
    }

    private String genPanoName(String prefix)
    {
        int id = 1;
        String path = APP_DIRECTORY + File.separator + prefix;
        File dir = new File(path);

        if (dir.exists()) {
            do {
                path = APP_DIRECTORY + File.separator + prefix + (id++);
                dir = new File(path);

            } while (dir.exists());
            return prefix + (--id);
        }

        return prefix;
    }
}
