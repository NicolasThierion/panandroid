package fr.ensicaen.panandroid.test;


import java.io.File;
import java.util.LinkedList;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.test.ActivityTestCase;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import junit.framework.Assert;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.snapshot.Snapshot;
import fr.ensicaen.panandroid.snapshot.SnapshotManager;
import fr.ensicaen.panandroid.stitcher.StitcherWrapper;
import fr.ensicaen.panandroid.capture.CaptureActivity;


public class StitcherTestActivity extends Activity
{
	StitcherWrapper mStitcher;
	SnapshotManager mManager;
	
	@Override
	public void onCreate(final Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
    	boolean success;
		
    	//get context ressources
    	String filename = Environment.getExternalStorageDirectory() + File.separator + "sample/PanoData.json";
    	
		
	
		//and init snapshot manager
		mManager= new SnapshotManager();
		
		//load testing sample configuration
		success = mManager.loadJson(filename);
		Assert.assertTrue(success);
				
		//load snapshots url into the stitcher
		LinkedList<Snapshot> snapshots = mManager.getSnapshotsList();

		System.out.println("Loading snapshots : ");
    	String snapshotsUrl[]= new String[snapshots.size()];
    	float orientations[][] = new float[snapshots.size()][3];
    	
    	int i=0;
    	for(Snapshot s : snapshots)
    	{
    		System.out.println(s.getFileName());
    		snapshotsUrl[i] = s.getFileName();
    		orientations[i][0] = s.getPitch();
    		orientations[i][1] = s.getYaw();
    		orientations[i][2] = s.getRoll();
    		i++;
    	}
    	//init stitcher wrapper
		mStitcher = new StitcherWrapper(mManager.getWorkingDir(), snapshotsUrl, orientations);
		Assert.assertTrue(mStitcher.getStatus()==0);
		
		//group snapshots by neighbors
		//TODO
		LinkedList<LinkedList<Integer>> neighbors = mManager.getNeighborsId();
	
		//find features
		mStitcher.findFeatures();
		
		//matchFeatures
		mStitcher.matchFeatures();
		
		//adjustParameters
		mStitcher.adjustParameters();
		
		//warpImages
		mStitcher.warpImages();
		
		//findSeamMasks
		mStitcher.findSeamMasks();
		
		//composePanorama
		mStitcher.composePanorama();
		
		super.onDestroy();
    }
}
