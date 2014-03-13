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
     * @return String representing content of PanoData.json
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
