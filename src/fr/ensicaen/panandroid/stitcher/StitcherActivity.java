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

package fr.ensicaen.panandroid.stitcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.capture.CaptureActivity;

/**
 * StitcherActivity class provides the stitcher of the application.
 * @author Jean Marguerite <jean.marguerite@ecole.ensicaen.fr>
 * @version 0.0.1 - Sat Feb 01 2014
 */
public class StitcherActivity extends Activity {
    private static final String TAG = StitcherActivity.class.getSimpleName();
    private File mFolderSnapshot;

    /**
     * Called when StitcherActivity is starting.
     * @param savedInstanceState Contains the data it most recently supplied in
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mFolderSnapshot = new File(intent.getStringExtra(CaptureActivity.FOLDER));
        setContentView(R.layout.stitcher);
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
            List<String> arguments = new ArrayList<String>();

            for (int i = 0; i < paths.length; ++i) {
                if (!paths[i].endsWith(".json")) {
                    arguments.add(mFolderSnapshot.getAbsolutePath() + File.separator
                            + paths[i]);
                    Log.i(TAG, mFolderSnapshot.getAbsolutePath()
                            + File.separator + paths[i]);
                } else {
                    try {
                        JSONObject jso = new JSONObject(mFolderSnapshot.getAbsolutePath()
                                + File.separator + "PanoData.json");
                        JSONArray pictureData = jso.getJSONArray("panoData");

                        for (int j = 0; i < pictureData.length(); ++i) {
                            JSONObject data = pictureData.getJSONObject(i);
                            Log.i(TAG, data.getString("snapshotId"));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            arguments.add("--result");
            arguments.add(mFolderSnapshot.getAbsolutePath() + File.separator
                    + "panorama.jpg");

            return openCVStitcher(arguments.toArray());
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
     * @return Result of the stitching.
     */
    public native int openCVStitcher(Object[] arguments);
}
