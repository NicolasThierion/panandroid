/*
 * ENSICAEN
 * 6 Boulevard Marechal Juin
 * F-14050 Caen Cedex
 *
 * This file is owned by ENSICAEN students.
 * No portion of this code may be reproduced, copied
 * or revised without written permission of the authors.
 */

package fr.ensicaen.panandroid.stitcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import fr.ensicaen.panandroid.R;

/**
 * StitcherActivity class provides the stitcher of the application.
 * @author Jean Marguerite <jean.marguerite@ecole.ensicaen.fr>
 * @version 0.0.1 - Sat Feb 01 2014
 */
public class StitcherActivity extends Activity implements OnClickListener {
    private static final String TAG = StitcherActivity.class.getSimpleName();
    private File mFolderSnapshot;

    /**
     * Called when StitcherActivity is starting.
     * @param savedInstanceState Contains the data it most recently supplied in
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Button stitch;

        mFolderSnapshot = new File(Environment.getExternalStorageDirectory()
                + File.separator + "Sample");
        setContentView(R.layout.stitcher);

        // Magic grey button !
        //TODO : uncomment
        //stitch = (Button) findViewById(R.id.stitch);
        //stitch.setOnClickListener(this);
    }

    /**
     * Called when the stitch button has been clicked.
     */
    @Override
    public void onClick(View v) {
        new StitcherTask().execute();
    }

    /**
     * StitcherTask class provides the treatment on the set of images.
     */
    class StitcherTask extends AsyncTask<Void, Void, Integer> {
        public final int SUCCESS = 0;
        private ProgressDialog mProgress;

        /**
         * Displays a Progress Dialog to the user.
         * It runs on the UI thread before doInBackground.
         */
        @Override
        protected void onPreExecute() {
            mProgress = ProgressDialog.show(StitcherActivity.this,
                    "Stitching in progress", "Please wait", true, false);
        }

        /**
         * Stitches images with OpenCV features in a JNI.
         */
        @Override
        protected Integer doInBackground(Void... params) {
            String[] paths = mFolderSnapshot.list();
            List<String> snapshots = new ArrayList<String>();

            for (int i = 0; i < paths.length; ++i) {
                snapshots.add(paths[i]);
                Log.i(TAG, mFolderSnapshot.getAbsolutePath()
                        + File.separator + paths[i]);
            }

            return OpenCVStitcher(mFolderSnapshot.getAbsoluteFile()
                    + File.separator, snapshots.toArray());
        }

        /**
         * Shows a dialog to the user corresponding to the result of
         * the stitching.
         * It runs on the UI thread after doInBackground.
         */
        @Override
        protected void onPostExecute(Integer result) {
            Dialog dialog = new Dialog(StitcherActivity.this);

            mProgress.dismiss();

            if (result == SUCCESS) {
                dialog.setTitle("Success");
            } else {
                dialog.setTitle("Failure");
            }

            dialog.show();
        }
    }

    /**
     * Load JNI library.
     */
    static {
        System.loadLibrary("ocvstitcher");
    }

    /**
     * Declaration of the native function.
     * @param snapshots Paths to the set of pictures.
     * @return Result of the stitching
     */
    public native int OpenCVStitcher(String folder, Object[] snapshots);
}
