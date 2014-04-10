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

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.View.OnSystemUiVisibilityChangeListener;

import fr.ensicaen.panandroid.PanandroidApplication;
import fr.ensicaen.panandroid.R;
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
public class CaptureFragment extends Fragment implements OnSystemUiVisibilityChangeListener {
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

    /**
     * Called to have the fragment instantiate its user interface view.
     * @return Created view.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        // Bind activity to its layout.
        View root = inflater.inflate(R.layout.activity_capture, container, false);

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

        ViewGroup renderer_container = ((ViewGroup) root.findViewById(R.id.renderer_container));
        renderer_container.addView(mCaptureView);

        mCaptureView.setPitchStep(DEFAULT_PITCH_STEP);
        mCaptureView.setYawStep(DEFAULT_YAW_STEP);

        return root;
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
     * Called when the Fragment is no longer resumed.
     * This is generally tied to Activity.onPause of the containing Activity's lifecycle.
     */
    @Override
    public void onPause()
    {
        super.onPause();

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
