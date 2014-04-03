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
import java.util.LinkedList;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import fr.ensicaen.panandroid.snapshot.Snapshot;
import fr.ensicaen.panandroid.snapshot.SnapshotManager;

/**
 * StitcherActivity class provides the stitcher activity of the application.
 * @author Jean Marguerite <jean.marguerite@ecole.ensicaen.fr>
 * @version 0.0.1 - Sat Feb 01 2014
 */
public class StitcherActivity extends Activity {
	
    @SuppressWarnings("unused")
	private static final String TAG = StitcherActivity.class.getSimpleName();
    private static final String PANORAMA_FILENAME = "result.jpg";
    
    //private File mFolder;
    private SnapshotManager mSnapshotManager;
    private StitcherWrapper mWrapper ;

    /**
     * Called when StitcherActivity is starting.
     * @param savedInstanceState Contains the data it most recently supplied in
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        
        mSnapshotManager = new SnapshotManager();
        mSnapshotManager.loadJson(intent.getStringExtra("projectFile"));

        new StitcherTask().execute();
    }

    /**
     * Reads content of PanoData.json file.
     * @return String with content of PanoData.json
     */
   /* public String readPanoData() {
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
    }*/

    /**
     * Gets images from current folder.
     * @return List of images path.
     */
   /* public List<String> getImagesPath() {
        List<String> imagesPath = new ArrayList<String>();
        String[] filesPath = mFolder.list();

        for (int i = 0; i < filesPath.length; ++i) {
            if (!filesPath[i].endsWith(".json")) {
                imagesPath.add(mFolder.getAbsolutePath() + File.separator
                        + filesPath[i]);
            }
        }

        imagesPath.add(mFolder.getAbsolutePath() + File.separator
                + "panorama.jpg");

        return imagesPath;
    }
*/
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
            mProgress.setMax(100);
            mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgress.setCancelable(false);
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
        	
        	
        	
        	mWrapper = StitcherWrapper.getInstance();
        	mWrapper.setSnapshotList(mSnapshotManager.getNeighbors());
        	
        	new Thread(new Runnable(){
        		public void run()
        		{
                	mWrapper.stitch(mSnapshotManager.getWorkingDir()+File.separator+PANORAMA_FILENAME);

        		}
        	}).start();


			while(mWrapper.getStatus() == StitcherWrapper.Status.OK && mWrapper.getProgress()<100)
			{
                mProgress.setProgress(mWrapper.getProgress());
                try 
                {
					Thread.sleep(1000);
				}
                catch (InterruptedException e) 
				{
					e.printStackTrace();
				}
			}
 		
        	if (mWrapper.getStatus() == StitcherWrapper.Status.DONE) 
        	{
        		
        		try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {}
        		
        		return SUCCESS;
            }
        	else 
        	{
                return -1;
            }
        }
    }
}
