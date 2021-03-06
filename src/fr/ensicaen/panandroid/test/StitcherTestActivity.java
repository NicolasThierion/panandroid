package fr.ensicaen.panandroid.test;


import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

import org.json.JSONException;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import junit.framework.Assert;
import fr.ensicaen.panandroid.snapshot.Snapshot;
import fr.ensicaen.panandroid.snapshot.SnapshotManager;
import fr.ensicaen.panandroid.stitcher.StitcherWrapper;


public class StitcherTestActivity extends Activity
{
	StitcherWrapper mStitcher;
	SnapshotManager mManager;
	
    private static final String PANORAMA_FILENAME = "result.jpg";

    private static final String TEST_SAMPLES = "sample15ensi2";	

	@Override
	public void onCreate(final Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		
    	//get context ressources
    	String filename = Environment.getExternalStorageDirectory() + File.separator + TEST_SAMPLES+File.separator+"PanoData.json";
	
		//and init snapshot manager
		//load testing sample configuration
		try {
			mManager= new SnapshotManager(filename);
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
				
		//load snapshots url into the stitcher
		LinkedList<LinkedList<Snapshot>> snapshots = mManager.getNeighborsList();

    	//init stitcher wrapper
		mStitcher = StitcherWrapper.getInstance();
		
		mStitcher.setSnapshotList(snapshots);
		mStitcher.stitch(mManager.getWorkingDir()+File.separator + PANORAMA_FILENAME);
		
		Assert.assertTrue(mStitcher.getStatus()==StitcherWrapper.Status.DONE);
		
		
	
		
		super.onDestroy();
    }
}
