package fr.ensicaen.panandroid.test;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

import org.json.JSONException;

import fr.ensicaen.panandroid.snapshot.Snapshot;
import fr.ensicaen.panandroid.snapshot.SnapshotManager;
import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

@SuppressWarnings("unused")
public class SnapshotManagerTestActivity extends Activity
{

	private SnapshotManager mSnapshotManager;
	private View mView;

	/**
	 * Called when the activity is first created.
	 * @param savedInstanceState - The instance state.
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
	    
	    //view in fullscreen
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
	
		//load JSON file
		try {
			mSnapshotManager = new SnapshotManager(Environment.getExternalStorageDirectory() + File.separator + "Panandroid/temp/PanoData.json");
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		

		
		
		LinkedList<LinkedList<Integer>> neighbors = mSnapshotManager.getNeighborsId();
		int i=0;
		for(LinkedList<Integer> currentNeighbors : neighbors)
		{
			System.out.println("Voisinage "+i +" :");
			++i;
			for(Integer currentSnapshot : currentNeighbors)
			{
				System.out.println(currentSnapshot);
			}
		}
		
	}
	
	  
	/**
	 * Remember to resume the glSurface.
	 */
	@Override
	protected void onResume()
	{
		super.onResume();
	}
	
	/**
	 * Also pause the glSurface.
	 */
	@Override
	protected void onPause()
	{
		super.onPause();
	}
}
