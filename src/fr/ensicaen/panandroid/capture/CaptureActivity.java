/*
 * ENSICAEN
 * 6 Boulevard Marechal Juin
 * F-14050 Caen Cedex
 *
 * This file is owned by ENSICAEN students.
 * No portion of this code may be reproduced, copied
 * or revised without written permission of the authors.
 */

package fr.ensicaen.panandroid.capture;

import fr.ensicaen.panandroid.sphere.Sphere;
import fr.ensicaen.panandroid.sphere.SphereView;
import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

/**
 * Activity that display a blank 3D sphere, and allow to take snapshots.
 * @author Nicolas Thierion <nicolas.thierion@ecole.ensicaen.fr>
 * @version 0.0.1 - Sat Jan 04 2014
 *
 * @todo Take snapshots.
 * @todo Put dots all around sphere
 * @todo Use sensor to activate camera.
 * @todo Set snapshots as texture of the sphere.
 * @todo Build a JSON file with position information of each snapshots.
 */
public class CaptureActivity extends Activity {
    /** Size of the sphere. */
    private static final float SPHERE_RADIUS = 0.15f;

    /** Resolution of the sphere. */
    private static final int SPHERE_RESOLUTION = 4;

    /** The OpenGL view. */
    private SphereView mSphereView;

    /**
     * Called when the activity is first created or re-initialized.
     * @param savedInstanceState The instance state.
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize the sphere.
        Sphere sphere = new Sphere(SPHERE_RESOLUTION, SPHERE_RADIUS);

        // Set view in full-screen.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Set GL view and its renderer.
        mSphereView = new SphereView(this, sphere);

        mSphereView.enableInertialRotation(false);
        mSphereView.enableTouchRotation(false);
        mSphereView.setInertiaFriction(0.0f);
        mSphereView.enableSensorialRotation(true);

        setContentView(mSphereView);
    }

    /**
     * Remember to resume the GLSurface.
     */
    @Override
    protected void onResume() {
        super.onResume();
        mSphereView.onResume();
    }

    /**
     * Also pause the GLSurface.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mSphereView.onPause();
    }
}
