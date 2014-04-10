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
import java.io.IOException;
import java.util.LinkedList;

import org.json.JSONException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.snapshot.SnapshotManager;
import fr.ensicaen.panandroid.viewer.SphereViewerActivity;



/**
 * StitcherActivity class provides the stitcher activity of the application.
 * @author Jean Marguerite <jean.marguerite@ecole.ensicaen.fr>
 * @version 0.0.1 - Sat Feb 01 2014
 */
public class StitcherActivity extends Activity
{

	private static final String TAG = StitcherActivity.class.getSimpleName();

	private static final int MAX_PANO_WIDTH = 4096;


    //private File mFolder;
    private SnapshotManager mSnapshotManager;
    private StitcherWrapper mStitcher ;
    private Button mStitchButton;
	private String mProjectFile;
	private String mProjectFilename;
	private LinkedList<String> mTempFilenames = new LinkedList<String>();

    /**
     * Called when StitcherActivity is starting.
     * @param savedInstanceState Contains the data it most recently supplied in
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		//bind activity to its layout
		setContentView(R.layout.activity_stitcher);
        Intent intent = getIntent();

        mProjectFile = intent.getStringExtra("PROJECT_FILE");
       // mProjectFile = "/sdcard/sampleCafet/PanoData.json";

        Log.i(TAG , "loading file "+mProjectFile);
        try {
			mSnapshotManager = new SnapshotManager(mProjectFile);
		}
        catch (JSONException e)
		{
        	e.printStackTrace();
        	return;
		}
        catch (IOException e)
		{
			e.printStackTrace();
			return;
		}

        mProjectFilename = mProjectFile.substring(mProjectFile.lastIndexOf(File.separator));
        mStitchButton = (Button) findViewById(R.id.btn_stitch);

        final EditText panonameET = ((EditText)StitcherActivity.super.findViewById(R.id.edittext_choose_panoname));
        panonameET.requestFocus();
        mStitchButton.setOnClickListener(new View.OnClickListener() {
			@Override
            public void onClick(View v)
			{
				String panoname = panonameET.getText().toString();
				mSnapshotManager.setProjectName(panoname);
				mSnapshotManager.toJSON(mProjectFilename);

				new StitcherTask().execute();
				//mStitchButton.setEnabled(false);
            }
        });


    }
/*
    @Override
	public void onBackPressed()
    {
    	super.onPause();
    }
*/

    /**
     * StitcherTask class provides treatments on the set of images.
     */
    class StitcherTask extends AsyncTask<Void, Integer, Integer>
    {
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
            mProgress.setMessage(getString(R.string.stitching_in_progress).toString());
            mProgress.setMax(100);
            mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgress.setCancelable(false);
            mProgress.show();

            //stitching takes a lot of ram... Prepare system for that.
            System.gc();
        }

        /**
         * Shows a dialog to the user corresponding to the result of
         * the stitching.
         * It runs on the UI thread after doInBackground.
         */
        @Override
        protected void onPostExecute(Integer result)
        {
    		AlertDialog.Builder dialog = new AlertDialog.Builder(StitcherActivity.this);
            mProgress.dismiss();

            Log.i(TAG, "Stitching finished : result = "+result);

            //if stictching succeed => switch to viewer
            if (result == SUCCESS)
            {


                dialog.setTitle(StitcherActivity.this.getString(R.string.stitch_success).toString());
                dialog.setNeutralButton(R.string.show_pano_in_viewer,
                						new DialogInterface.OnClickListener()
        		{
        			public void onClick(DialogInterface dialog, int id)
        			{
        			    Intent intent = new Intent(StitcherActivity.this,
        			    		SphereViewerActivity.class);
        			    intent.putExtra("projectFile", mProjectFile);

        			    startActivity(intent);
        			}
        		});

            }
            else
            {
                dialog.setTitle(StitcherActivity.this.getString(R.string.stitch_failure).toString());
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

        	try
        	{
        	new Thread(new Runnable(){
        		public void run()
        		{

                    String panoJpeg = mSnapshotManager.getPanoramaJpgPath();
                    mTempFilenames.add(genTempFilename("__tmp", panoJpeg));
                    //launch stitcher
                	mStitcher.stitch(mTempFilenames.getLast());
        		}
        	}).start();
        	}catch(Exception e)
        	{
        		return -1;
        	}

        	//poll status and update progress bar
        	double bp, sp, diff;
			while(mStitcher.getStatus() == StitcherWrapper.Status.OK && mStitcher.getProgress()<100)
			{
				/*
				//smooth progression a little bit ;-)
				sp = mStitcher.getProgress();
				bp = mProgress.getProgress();
				diff = (sp-bp)/(Math.max((100-sp), 1)/10);
				bp += diff;


                mProgress.setProgress((int)bp);*/
				mProgress.setProgress(mStitcher.getProgress());
                try
                {
					Thread.sleep(1000);
				}
                catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
            mProgress.setProgress(100);


            //pano succeed?
        	if (mStitcher.getStatus() == StitcherWrapper.Status.DONE || mStitcher.getStatus() == StitcherWrapper.Status.OK)
        	{
                String panoJpeg = mSnapshotManager.getPanoramaJpgPath();
               	String tempFilename = mTempFilenames.getLast();

        		//get actual size of created image
           	 	BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;

                //wait for the file to be well written
                File f;
                do
                {
                	 f = new File(tempFilename);
                	try {
    					Thread.sleep(50);
    				} catch (InterruptedException e) {
    					e.printStackTrace();
    				}
                }while(!f.exists() || !f.canRead());


                BitmapFactory.decodeFile(tempFilename, opts);
                int croppedWidth = opts.outWidth;
                int croppedHeight = opts.outHeight;

                //set new data into JSon...

                //Get range
            	float bounds[][] = mStitcher.getBoundingAngles();
            	float heading = mSnapshotManager.getSnapshotsList().get(0).getYaw();
            	float minPitch = bounds[0][0], minYaw = bounds[0][1], maxPitch= bounds[1][0], maxYaw= bounds[1][1];

            	heading = Math.max(minYaw, heading);
            	heading = Math.min(maxYaw, heading);

            	Log.i(TAG, "panorama bounds : pitch@["+minPitch+","+maxPitch+"], yaw@["+minYaw+","+maxYaw+"]");

                //add padding to cover 360�
            	float hfov = mSnapshotManager.getCameraHFov();
            	float vfov = mSnapshotManager.getCameraVFov();

            	minPitch -= vfov/2;
            	maxPitch +=  vfov/2;
            	minYaw -=  hfov/2;
            	maxYaw += hfov/2;

            	//get actual area coverage of the pano
               	Log.i(TAG, "minPitch = "+minPitch+"�, maxPitch ="+maxPitch+"�, minYaw = "+ minYaw+"�, maxYaw="+maxYaw+"�");

            	float xrange = maxYaw - minYaw;
            	float yrange = maxPitch - minPitch;

               	//round this area
               	xrange = (xrange>365? 360:xrange);
               	yrange = (yrange>185? 180:yrange);

               	Log.i(TAG, "covered area : pitch = "+yrange+"�, yaw = "+ xrange+"�");


               	//compute full pano resolution
               	float rx = 360.0f / xrange;
               	float ry = 180.0f / yrange;

               	int fullResY = (int) (croppedHeight*ry);
               	int fullResX = (int) (croppedWidth*rx);

                //make resolution multiple of 8
               	fullResX = (fullResX>>3)<<3;
               	fullResY = (fullResY>>3)<<3;

               	Log.i(TAG, "estimated full pano resolution before rescale : "+fullResX+"x"+fullResY);
               	Log.i(TAG, "cropped pano area resolution before rescale : "+croppedWidth+"x"+croppedHeight);

                mSnapshotManager.setNbUsedImages(mStitcher.getUsedIndices().length);

            	if(fullResX>MAX_PANO_WIDTH)
               	{
               		double r = ((double)fullResX)/((double)MAX_PANO_WIDTH);

               		fullResX/=r;
               		fullResY/=r;

               		croppedHeight = (int) Math.ceil(croppedHeight/r);
               		croppedWidth = (int) Math.ceil(croppedWidth/r);

               		croppedHeight = (croppedHeight>>3)<<3;
               		croppedWidth= (croppedWidth>>3)<<3;
               		//tmpJpeg = genTempFilename("__tmp",panoJpeg);
               		//for an unknow reason, resize fail the first time.
               		mTempFilenames.add(genTempFilename("__tmpRSZ1", panoJpeg));
               		StitcherWrapper.resizeImg(tempFilename, mTempFilenames.getLast(), croppedWidth,croppedHeight);
               		StitcherWrapper.resizeImg(tempFilename, tempFilename, croppedWidth,croppedHeight);

               		//ensure resolution is still multiple of 4
	               	fullResX = (fullResX>>3)<<3;
	               	fullResY = (fullResY>>3)<<3;

               	}

            	int paddX = (fullResX - croppedWidth)/2;
               	int paddY = (fullResY - croppedHeight)/2;

               	Log.i(TAG, "Cropped pano resolution : " +croppedWidth + "x"+ croppedHeight);

               	Log.i(TAG, "Full pano resolution : " +fullResX + "x"+fullResY);
               	Log.i(TAG, "Adding top padding : " +paddY);
               	Log.i(TAG, "Adding left padding : " +paddX);
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
            }
        	else
        	{
                return -1;
            }
        }

		private String genTempFilename(String prefix, String filename)
		{
			String dir = filename.substring(0, filename.lastIndexOf(File.separator)+1);
			filename = filename.substring(dir.length());
			filename = prefix + System.currentTimeMillis() + filename;
			filename = dir + filename;
			return filename;
		}
    }
}
