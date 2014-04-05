/*
 * Copyright (C) 2013 Nicolas THIERION, Saloua BENSEDDIK, Jean Marguerite.

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
package fr.ensicaen.panandroid.snapshot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/** 
 * creates a list of snapshots
 */
public class SnapshotManager implements SnapshotEventListener
{
	public static final String DEFAULT_JSON_FILENAME = "PanoData.json";

	private static final String TAG= SnapshotManager.class.getSimpleName();
	/* ******
	 * PARAMETERS
	 * *****/	

	private static final float NEIGHBOR_DISTANCE = 44.0f;
	
	
	/* ******
	 * ATTRIBUTES
	 * *****/
	private LinkedList<Snapshot> mSnapshots;
	private JSONArray mJsonArray;
	private String mProjectName="";
	private String mWorkingDir;
	
	
	
	
	/* ******
	 * CONSTRUCTOR
	 * ******/
	/**
	 *  creates an array of snapshots
	 */
	public SnapshotManager()
	{
		mSnapshots = new LinkedList<Snapshot>();
		mJsonArray = new JSONArray();
		mWorkingDir="";
		mProjectName = "";
		
	}
	
	/* *******
	 * METHODS
	 * *******/
	/**
	 * add a snapshot to the list of snapshots.
	 * @param snapshot
	 */
	public void addSnapshot(Snapshot snapshot)
	{
		
		String filename = snapshot.getFilename();
		String workingDir = filename.substring(0, filename.lastIndexOf(File.separator));
		
		if(mWorkingDir=="" && mSnapshots.size()==0)
		{
			mWorkingDir = workingDir;
			if(mProjectName=="")
				mProjectName = mWorkingDir.substring(mWorkingDir.lastIndexOf(File.separator));
			Log.i(TAG, "setting working dir : " +mWorkingDir);
		}
		else
		{
			Assert.assertTrue(workingDir.equals(mWorkingDir));
		}
		snapshot.setId(mSnapshots.size());
		mSnapshots.add(snapshot);
		
	}
	public String toJSON(String filename)
	{
		if(mProjectName == "")
			mProjectName = filename;
		return toJSON(filename, mProjectName);
	}

	
	/**
	 * save the snapshot collection to a JSON file, at the current working dir.
	 * @param directory Directory to save the JSON file. Directories will be created if don't exists.
	 * @param filename name of the JSON file.
	 * @return absolute path + filename of the created JSon file.
	 */
	public String toJSON(String filename, String projectName)
	{
		JSONObject snapshots = new JSONObject();
		JSONArray jsonArray = new JSONArray();

		String absoluteFilename = null;
		
		mProjectName = projectName;
		try {
			snapshots.put("panoName", mProjectName);	
			
			for(Snapshot s : mSnapshots)
			{

				JSONObject jso = new JSONObject();
				try 
				{
					jso.put("roll", s.getRoll());
					jso.put("yaw", s.getYaw());
					jso.put("pitch", s.getPitch());	
					jso.put("filename", s.getFilename().substring(mWorkingDir.length()+1, s.getFilename().length()));
					jso.put("snapshotId", s.getId());
					jsonArray.put(jso);
				}
				catch (JSONException e)
				{
					e.printStackTrace();
				}
			}
			
			snapshots.put("panoData", jsonArray);

			try
			{
				absoluteFilename =  mWorkingDir+File.separator+filename;
				FileWriter file = new FileWriter(absoluteFilename);
				file.write(snapshots.toString());
				file.flush();
				file.close();
		 
			}
			catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		} catch (JSONException e1) {
			e1.printStackTrace();
			return null;
		}	
		return absoluteFilename; 
	}
	
	


	@Override
	public void onSnapshotTaken(byte[] pictureData, Snapshot snapshot)
	{
		addSnapshot(snapshot);
	}
	
	public void setWorkingDir(String workingDir)
	{
		mWorkingDir = workingDir;
	}
	
	public String getWorkingDir()
	{
		return mWorkingDir;
	}
	
	public void setProjectName(String name)
	{
		mProjectName = name;
	}
	
	public String getProjectName()
	{
		return mProjectName;
	}
	


	/**
	 * Group snapshots by list of neighbors
	 * @return
	 */
	public LinkedList<LinkedList<Snapshot>> getNeighborsList()
	{
		LinkedList<LinkedList<Snapshot>> neighborsList = new LinkedList<LinkedList<Snapshot>>();
	
		for( int i = 0; i < mSnapshots.size(); i++ )
		{
			LinkedList<Snapshot> currentList = new LinkedList<Snapshot>();
			neighborsList.add(currentList);		
			
			Snapshot currentSnapshot = mSnapshots.get(i);
			currentList.add(currentSnapshot);	
			for (int j = i+1; j < mSnapshots.size(); j++)
			{
				Snapshot currentNeighbor = mSnapshots.get(j);
				if(currentSnapshot.getDistance(currentNeighbor) < NEIGHBOR_DISTANCE )
				{
					currentList.add(currentNeighbor);
				}
			}
		}	
		return neighborsList;
	}
	
	/**
	 * Group snapshots by list of neighbors, given by their ID.
	 * The id is the order the snapshot has been added to the manager.
	 * @return
	 */
	public LinkedList<LinkedList<Integer>> getNeighborsId()
	{
		LinkedList<LinkedList<Integer>> neighborsList = new LinkedList<LinkedList<Integer>>();
		
		for( int i = 0; i < mSnapshots.size(); i++ )
		{
			LinkedList<Integer> currentList = new LinkedList<Integer>();
			neighborsList.add(currentList);		
			
			Snapshot currentSnapshot = mSnapshots.get(i);
			currentList.add(i);	
			for (int j = i+1; j < mSnapshots.size(); j++)
			{
				Snapshot currentNeighbor = mSnapshots.get(j);
				if(currentSnapshot.getDistance(currentNeighbor) < NEIGHBOR_DISTANCE )
				{
					currentList.add(j);
				}
			}
		}	
		return neighborsList;
	}
	
	/**
	 * load a project from the given JSON config file.
	 * Loads snapshots list, pano name, etc...
	 * @param filename - project filename
	 */
	public boolean loadJson(String filename)
	{
		
		//read json file
	    try {
			FileInputStream inputStream = new FileInputStream (filename);
			byte[] buffer = new byte[inputStream.available()];
			inputStream.read(buffer, 0, buffer.length );
			String jsonStr = new String(buffer);
			inputStream.close();
			//create JSon object
			JSONObject jsonSnapshots = new JSONObject(jsonStr);
			
			//parse JSON and build list
			mSnapshots = new LinkedList<Snapshot>();
			mProjectName = jsonSnapshots.getString("panoName");			
			mWorkingDir = filename.substring(0, filename.lastIndexOf(File.separator));;
			Log.i(TAG, "setting working dir : " +mWorkingDir);
			
			JSONArray panoDataArray = jsonSnapshots.getJSONArray("panoData");

			Log.i(TAG, "Loading "+panoDataArray.length()+" snapshots from JSON");
			for(int i = 0; i < panoDataArray.length(); i++)
			{
				JSONObject currentjso = panoDataArray.getJSONObject(i);
				
				//String snapshotId = currentjso.getString("snapshotId");
				float pitch = Float.parseFloat(currentjso.getString("pitch"));
				float yaw = Float.parseFloat(currentjso.getString("yaw"));
				float roll = Float.parseFloat(currentjso.getString("roll"));

				String snapshotUrl = currentjso.getString("filename");
				int snapshotId = currentjso.getInt("snapshotId");
				snapshotUrl = mWorkingDir.concat(File.separator).concat(snapshotUrl); 
				Snapshot currentSnapshot = new Snapshot(pitch, yaw, roll);
				currentSnapshot.setFileName(snapshotUrl);
				currentSnapshot.setId(snapshotId);
				mSnapshots.add(currentSnapshot);			
			}
			
			return true;

	    }
	    catch (Exception e) 
	    {
	    	e.printStackTrace();
	    	return false;
	    }
	}

	public LinkedList<Snapshot> getSnapshotsList() 
	{
		return mSnapshots;
	}
	    
	   
	

}

