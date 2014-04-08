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

import java.util.HashMap;
import java.util.LinkedList;

import junit.framework.Assert;
import android.annotation.SuppressLint;
import android.util.Log;
import fr.ensicaen.panandroid.snapshot.Snapshot;

/**
 * StitcherWrapper is a stitcher engine based on openCV
 * StitcherWrapper class provides a wrapper between Java and JNI class.
 * @version 0.0.1 - Fri Mar 21 2014
 * @author Nicolas THIERION.
 * @author Jean MARGUERITE.
 */
public class StitcherWrapper
{
	
	private static final String TAG = StitcherWrapper.class.getSimpleName();
	/* *********
     * ATTRIBUTES
     * *********/
	private static StitcherWrapper mInstance;
	public static enum Status{
		OK, ERR, DONE
	}
	
	
	private Status mStatus = Status.ERR;
	private String mMessage = "-1";
	private String mFilenames[];


	private float[][] mOrientations;

	private String mPanoFile;
	private LinkedList<LinkedList<Snapshot>> mNeighborList;
	private HashMap<Integer, Snapshot> mSnapshotMap;
	private int mMatchingMask[][];
	
    /**
     * Load JNI library.
     */
    static {
        System.loadLibrary("jniwrapper");
    }

    /* *********
     * CONSTRUCTOR
     * *********/    
    private StitcherWrapper()
    {}
    
    public static StitcherWrapper getInstance()
    {
    	if(mInstance==null)
    	{
    		synchronized(StitcherWrapper.class)
    		{
    			if(mInstance==null)
    			{
    				mInstance = new StitcherWrapper();
    			}
    		}
    	}
    	return mInstance;
    }
    
	@SuppressLint("UseSparseArrays")
	public void setSnapshotList(LinkedList<LinkedList<Snapshot>> neighborsList)
    {
    	mNeighborList =neighborsList;
    	mSnapshotMap = new HashMap<Integer, Snapshot>();
    	
    	int i=0;
    	for(LinkedList<Snapshot> list : neighborsList)
    	{
    		for(Snapshot s : list)
    		{
    			mSnapshotMap.put(s.getId(), s);
    		}
    	}
    	int size = mSnapshotMap.size();
    	mFilenames = new String[size];
    	mOrientations = new float[size][3];
    	//convert hashmap into array
    	for(i=0; i<mSnapshotMap.size(); ++i)
    	{
    		System.out.println(i);
    		
    		Snapshot s = mSnapshotMap.get(i);
    		Assert.assertTrue(s!=null);
    		mFilenames[i] = s.getFilename();
			Log.i(TAG, "loading "+s.getFilename());
    		mOrientations[i][0] = s.getPitch();
    		mOrientations[i][1] = s.getYaw();
    		mOrientations[i][2] = s.getRoll();
    	}
    	
    	
    	mMatchingMask = new int[size][size];
    	for(LinkedList<Snapshot>list : neighborsList)
    	{
    		int currId = list.get(0).getId();
    		for(Snapshot s : list)
    		{
    			mMatchingMask[currId][s.getId()]=1;
    		}
    	}
    	
    	
    	mStatus = Status.OK;
    }

    public Status stitch(String resultFile)
    {
    	int status = 0;
    	mStatus = Status.OK;
    	mPanoFile = resultFile;
    	
    	
    	
    	status = newStitcher(mPanoFile, mFilenames, mMatchingMask);
    	if(status!=0)
    	{
    		mMessage = "stitcher creation failed";
    		mStatus = Status.ERR;
    		return Status.ERR;
    	}
    	
    	
    	status = composePanorama();
    	if(status!=0)
    	{
    		mMessage = "composePanorama failed";
    		Log.e(TAG, mMessage );
    		mStatus = Status.ERR;
    		return Status.ERR;
    	}
    	Log.i(TAG, "Stitching done!!");
    	
    	//float bounds[][] = getBoundingAngles();
    	
    	
		mStatus = Status.DONE;
		return Status.DONE;
    }	
    

  

   
    /* **********
	 * PUBLIC METHODS
	 * *********/
   
    /**
     * Return the status of the last executed stitching operation. zero if all is ok.
     * @return the status of the operation
     */
    public Status getStatus()
    {
    	return mStatus;
    }
    
    public float[][] getBoundingAngles()
    {
    	//get used indices of images in the panorama
    	int[] indices = getUsedIndices();
    	
    	//seek orientations at these indices to find min and max (bounds)
    	float minPitch, maxPitch, minYaw, maxYaw, p, y;
    	minPitch = minYaw = Float.MAX_VALUE;
    	maxPitch = maxYaw = Float.MIN_VALUE;
    	for(int i : indices)
    	{
    		p = mOrientations[i][0];
    		y = mOrientations[i][1];
    		
    		minPitch = Math.min(minPitch,  p);
    		minYaw = Math.min(minYaw,  y);
    		maxPitch = Math.max(maxPitch,  p);
    		maxYaw = Math.max(maxYaw,  y);
    		
    	}
    	
    	float[][] bounds = new float[2][2];
    	bounds[0][0] = minPitch;
    	bounds[1][0] = maxPitch;
    	bounds[0][1] = minYaw;
    	bounds[1][1] = maxYaw;
    	
    	Log.i(TAG, "Bounding angles : mPitch = "+minPitch +"\nMPitch = "+maxPitch + "\nmYaw = "+minYaw+"\nMYaw = "+maxYaw);
    	
    	
    	return bounds;
    }

    /**
     * Get average progress (in percent) of all the stitching operations;
     * @return
     */
	public native int getProgress();
	
	
	/* **********
	 * STATIC METHODS
	 * *********/
	 public static native int rotateImage(String imagePath, int angle);
	 
	 public static native void setPadding(String panoJpeg, String panoJpeg2, int paddL,
				int paddT, int paddR, int paddB);

	 public static native void resizeImg(String panoJpeg, String panoJpeg2,
				int newWidth, int newHeight);
	 
	/* **********
	 * PRIVATE NATIVE PROTOTYPES DECLARATION
	 * **********/    
    /**
     * Find features in all bunch of images.
     * @return Result of finding features.
     */
    private native int findFeatures();

    /**
     * Match features.
     * @return Result of match features.
     */
    private native int matchFeatures();

    /**
     * Adjust different kinds of parameters.
     * @return Result of adjust parameters.
     */
    private native int adjustParameters();

    /**
     * Warp images.
     * @return Result of warp images.
     */
    private native int warpImages();

    /**
     * Find seam masks.
     * @return Result of find seam masks.
     */
    private native int findSeamMasks();

    /**
     * Compose final panorama.
     * @return Result of compose panorama.
     */
    private native int composePanorama();


    /**
     * 
     * @param filename
     * @param bounds
     * @return
     */
    //private native int addPading(String srcJpg, String dstJpg, float[][] bounds);

	 
	 /**
     * Store images path for OpenCV.
     * @param files Path to all images in the current folder.
     * @return Result of images storage.
     */
	 private native int newStitcher(String panoFilename, Object[] files, int[][] matchingMask);

	 public native int[] getUsedIndices();

	 public native double getWorkingResolution();

	

	

	 

}
