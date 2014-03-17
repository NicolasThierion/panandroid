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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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
import android.util.Log;
import fr.ensicaen.panandroid.capture.CaptureActivity;

/**
 * StitcherActivity class provides the stitcher of the application.
 * @author Jean Marguerite <jean.marguerite@ecole.ensicaen.fr>
 * @version 0.0.1 - Sat Feb 01 2014
 */
public class StitcherActivity extends Activity {
    private static final String TAG = StitcherActivity.class.getSimpleName();
    private File mFolder;

    /**
     * Called when StitcherActivity is starting.
     * @param savedInstanceState Contains the data it most recently supplied in
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mFolder = new File(intent.getStringExtra(
                CaptureActivity.FOLDER));

        new StitcherTask().execute();
    }

    /**
     * Reads content of PanoData.json file.
     * @return String with content of PanoData.json
     */
    public String readPanoData() {
        BufferedReader br = null;
        String content = null;

        try {
            br = new BufferedReader(new FileReader(
                    mFolder.getAbsoluteFile() + File.separator
                    + "PanoData.json"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }

            content = sb.toString();
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return content;
    }

    /**
     * Gets images from current folder.
     * @return List of images path.
     */
    public List<String> getImagesPath() {
        List<String> imagesPath = new ArrayList<String>();
        String[] filesPath = mFolder.list();

        for (int i = 0; i < filesPath.length; ++i) {
            if (!filesPath[i].endsWith(".json")) {
                imagesPath.add(mFolder.getAbsolutePath() + File.separator
                        + filesPath[i]);
            }
        }

        return imagesPath;
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
        protected void onPreExecute() {
            mProgress = new ProgressDialog(StitcherActivity.this);
            mProgress.setMessage("Stitching en cours");
            mProgress.setIndeterminate(false);
            mProgress.setMax(100);
            mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgress.setCancelable(true);
            mProgress.show();
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

        /**
         * Stitches images with OpenCV features through JNI.
         */
        @Override
        protected Integer doInBackground(Void... params) {
            if (storeImagesPath(getImagesPath().toArray()) == SUCCESS) {
                mProgress.setProgress(10);
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            mProgress.setProgress(50);

            return SUCCESS;
            /*
            String[] paths = mFolder.list();
            List<String> arguments = new ArrayList<String>();

            for (int i = 0; i < paths.length; ++i) {
                if (paths[i].endsWith(".json")) {
                    try {
                        JSONObject file = new JSONObject(readPanoData());

                        JSONArray pictureData = file.getJSONArray("panoData");

                        for (int j = 0; j < pictureData.length(); ++j) {
                            JSONObject data = pictureData.getJSONObject(j);
                            Log.i(TAG, data.getString("snapshotId"));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    arguments.add(mFolder.getAbsolutePath() + File.separator
                            + paths[i]);
                    Log.i(TAG, mFolder.getAbsolutePath()
                            + File.separator + paths[i]);
                }
            }

            arguments.add("--result");
            arguments.add(mFolder.getAbsolutePath() + File.separator
                    + "panorama.jpg");

            return openCVStitcher(arguments.toArray());
            */
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

    /**
     * Store images path for OpenCV.
     * @param files Path to all images in the current folder.
     * @return Result of images storage.
     */
    public native int storeImagesPath(Object[] files);
}
