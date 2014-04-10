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

package fr.ensicaen.panandroid.stitcher;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

import org.json.JSONException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.snapshot.SnapshotManager;
import fr.ensicaen.panandroid.viewer.SphereViewerActivity;

/**
 * StitcherActivity class provides the stitcher activity of the application.
 * @author Jean Marguerite <jean.marguerite@ecole.ensicaen.fr>
 * @author Nicolas Thierion <nicolas.thierion@ecole.ensicaen.fr>
 */
public class StitcherActivity extends Activity {
    /********************
     * DEBUG PARAMETERS *
     ********************/
    private static final String TAG = StitcherActivity.class.getSimpleName();
    private static final int MAX_PANO_WIDTH = 4096;

    /**************
     * ATTRIBUTES *
     **************/
    /** Snapshot manager */
    private SnapshotManager mSnapshotManager;

    /** Stitcher wrapper */
    private StitcherWrapper mStitcher;

    /** Stitch button */
    private Button mStitchButton;

    /** JSON project file */
    private String mProjectFile;

    /** Project filename */
    private String mProjectFilename;

    /** Temporary filenames */
    private LinkedList<String> mTempFilenames = new LinkedList<String>();

    /**
     * Called when StitcherActivity is starting.
     * @param savedInstanceState Contains the data it most recently supplied in
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Bind activity to its layout.
        setContentView(R.layout.activity_stitcher);

        // Receive intent.
        Intent intent = getIntent();

        mProjectFile = intent.getStringExtra("PROJECT_FILE");
        Log.i(TAG, "Loading file : " + mProjectFile);

        try {
            mSnapshotManager = new SnapshotManager(mProjectFile);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        mProjectFilename = mProjectFile.substring(mProjectFile.lastIndexOf(File.separator));
        mStitchButton = (Button) findViewById(R.id.stitch);

        final EditText panorama = ((EditText)StitcherActivity.super.findViewById(R.id.panorama_name));
        panorama.requestFocus();

        mStitchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String panoramaName = panorama.getText().toString();
                mSnapshotManager.setProjectName(panoramaName);
                mSnapshotManager.toJSON(mProjectFilename);

                // Launch stitching task
                new StitcherTask().execute();
            }
        });

        // Hide keyboard if we touch the screen
        findViewById(R.id.stitcher_layout).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(panorama.getWindowToken(), 0);
            }
        });
    }

    /**
     * StitcherTask class provides treatments on the set of images.
     */
    class StitcherTask extends AsyncTask<Void, Integer, Integer> {
        public final int SUCCESS = 0;
        private ProgressDialog mProgress;

        /**
         * Displays a Progress Dialog to the user.
         * It runs on the UI thread before doInBackground.
         */
        @Override
        protected void onPreExecute()
        {
            mProgress = new ProgressDialog(StitcherActivity.this);
            mProgress.setMessage("Création du diaporama");
            mProgress.setMax(100);
            mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgress.setCancelable(false);
            mProgress.show();

            // Stitching takes a lot of ram... Prepare system for that.
            System.gc();
        }

        /**
         * Shows a dialog to the user corresponding to the result of
         * the stitching.
         * It runs on the UI thread after doInBackground.
         */
        @Override
        protected void onPostExecute(Integer result) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(StitcherActivity.this);
            mProgress.dismiss();

            Log.i(TAG, "Stitching finished : " + result);

            if (result == SUCCESS) {
                dialog.setTitle("Construction terminée");
                dialog.setNeutralButton("Visualiser le panorama",
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Intent intent = new Intent(StitcherActivity.this,
                                SphereViewerActivity.class);

                        intent.putExtra("PROJECT_FILE", mProjectFile);
                        startActivity(intent);
                    }
                });
            } else {
                dialog.setTitle("Erreur lors de la construction du panorama");
            }

            dialog.show();
        }

        /**
         * Stitches images with OpenCV features through JNI.
         */
        @Override
        protected Integer doInBackground(Void... params) {
            mStitcher = StitcherWrapper.getInstance();
            mStitcher.setSnapshotList(mSnapshotManager.getNeighborsList());

            try {
                new Thread(new Runnable() {
                    public void run() {
                        String panoJpeg = mSnapshotManager.getPanoramaJpgPath();
                        mTempFilenames.add(genTempFilename("__tmp", panoJpeg));

                        // Run stitcher Run !
                	mStitcher.stitch(mTempFilenames.getLast());
                    }
                }).start();
            } catch (Exception e) {
                return -1;
            }

            // Poll status and update progress bar
            while (mStitcher.getStatus() == StitcherWrapper.Status.OK
                    && mStitcher.getProgress() < 100) {
                mProgress.setProgress(mStitcher.getProgress());

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            mProgress.setProgress(100);

            // Panorama succeed?
            if (mStitcher.getStatus() == StitcherWrapper.Status.DONE
                    || mStitcher.getStatus() == StitcherWrapper.Status.OK) {
                String panoJpeg = mSnapshotManager.getPanoramaJpgPath();
                String tempFilename = mTempFilenames.getLast();

                // Get actual size of created image
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;

                // Wait for the file to be well written
                File f;

                do {
                    f = new File(tempFilename);

                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } while (!f.exists() || !f.canRead());

                BitmapFactory.decodeFile(tempFilename, opts);
                int croppedWidth = opts.outWidth;
                int croppedHeight = opts.outHeight;

                // Get range
            	float bounds[][] = mStitcher.getBoundingAngles();
            	float heading = mSnapshotManager.getSnapshotsList().get(0).getYaw();
            	float minPitch = bounds[0][0], minYaw = bounds[0][1],
            	        maxPitch= bounds[1][0], maxYaw= bounds[1][1];

            	heading = Math.max(minYaw, heading);
            	heading = Math.min(maxYaw, heading);

            	Log.i(TAG, "panorama bounds : pitch@["+minPitch+","+maxPitch+"],"
            	        + "yaw@["+minYaw+","+maxYaw+"]");

                // Add padding to cover 360°
            	float hfov = mSnapshotManager.getCameraHFov();
            	float vfov = mSnapshotManager.getCameraVFov();

            	minPitch -= vfov / 2;
            	maxPitch +=  vfov / 2;
            	minYaw -=  hfov / 2;
            	maxYaw += hfov / 2;

            	// Get actual area coverage of the panorama
               	Log.i(TAG, "minPitch : " + minPitch + "°, "
               	        + "maxPitch : " + maxPitch + "°, "
               	        + "minYaw : " + minYaw + "°, maxYaw : " + maxYaw + "°");

            	float xrange = maxYaw - minYaw;
            	float yrange = maxPitch - minPitch;

               	// Round this area
               	xrange = (xrange > 365 ? 360 : xrange);
               	yrange = (yrange > 185 ? 180 : yrange);

               	Log.i(TAG, "Covered area : pitch = " + yrange + "°, "
               	        + "yaw = " + xrange + "°");

               	// Compute full panorama resolution
               	float rx = 360.0f / xrange;
               	float ry = 180.0f / yrange;

               	int fullResY = (int) (croppedHeight * ry);
               	int fullResX = (int) (croppedWidth * rx);

                // Make resolution multiple of 8
               	fullResX = (fullResX >> 3) << 3;
               	fullResY = (fullResY >> 3) << 3;

               	Log.i(TAG, "Estimated full panorama resolution before rescale : "
               	        + fullResX + "x" + fullResY);
               	Log.i(TAG, "Cropped panorama area resolution before rescale : "
               	        + croppedWidth + "x" + croppedHeight);

                mSnapshotManager.setNbUsedImages(mStitcher.getUsedIndices().length);

            	if (fullResX > MAX_PANO_WIDTH) {
            	    double r = ((double)fullResX) / ((double)MAX_PANO_WIDTH);

            	    fullResX /= r;
            	    fullResY /= r;

            	    croppedHeight = (int) Math.ceil(croppedHeight / r);
            	    croppedWidth = (int) Math.ceil(croppedWidth / r);

            	    croppedHeight = (croppedHeight >> 3) << 3;
            	    croppedWidth = (croppedWidth >> 3) << 3;

            	    // For an unknown reason, resize fail the first time.
            	    mTempFilenames.add(genTempFilename("__tmpRSZ1", panoJpeg));
            	    StitcherWrapper.resizeImg(tempFilename, mTempFilenames.getLast(), croppedWidth,croppedHeight);
            	    StitcherWrapper.resizeImg(tempFilename, tempFilename, croppedWidth,croppedHeight);

            	    // Ensure resolution is still multiple of 4
            	    fullResX = (fullResX >> 3) << 3;
            	    fullResY = (fullResY >> 3) << 3;
               	}

            	int paddX = (fullResX - croppedWidth) / 2;
               	int paddY = (fullResY - croppedHeight) / 2;

               	Log.i(TAG, "Cropped panorama resolution : " + croppedWidth + "x"
               	        + croppedHeight);
               	Log.i(TAG, "Full panorama resolution : " + fullResX + "x" + fullResY);
               	Log.i(TAG, "Adding top padding : " + paddY);
               	Log.i(TAG, "Adding left padding : " + paddX);

            	mSnapshotManager.setFullPanoHeight(fullResY);
               	mSnapshotManager.setFullPanoWidth(fullResX);
               	mSnapshotManager.setCropPanoWidth(croppedWidth);
               	mSnapshotManager.setCropPanoHeight(croppedHeight);
               	mSnapshotManager.setTopPadding(paddY);
               	mSnapshotManager.setLeftPadding(paddX);
               	mSnapshotManager.setBounds(bounds);
            	mSnapshotManager.setHeading(heading);

               	StitcherWrapper.setPadding(tempFilename, tempFilename, paddX, paddY, paddX, paddY );

               	StitcherWrapper.resizeImg(tempFilename, panoJpeg, fullResX, fullResY);
               	while(mTempFilenames.size()>0)
               		new File(mTempFilenames.removeFirst()).delete();

            	mSnapshotManager.doPhotoSphereTagging();
            	mSnapshotManager.toJSON(mProjectFilename);

            	return SUCCESS;
            } else {
                return -1;
            }
        }

        private String genTempFilename(String prefix, String filename) {
            String dir = filename.substring(0, filename.lastIndexOf(File.separator)+1);
            filename = filename.substring(dir.length());
            filename = prefix + System.currentTimeMillis() + filename;
            filename = dir + filename;
            return filename;
	}
    }
}
