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
	private static final String TAG= SnapshotManager.class.getSimpleName();
	/* ******
	 * PARAMETERS
	 * *****/
	public static final String DEFAULT_JSON_FILENAME = "PanoData.json";
	

	private static final float NEIGHBOR_DISTANCE = 44.0f;
	
	
	/* ******
	 * ATTRIBUTES
	 * *****/
	private LinkedList<Snapshot> mSnapshots;
	private JSONArray mJsonArray;
	private String mProjectName;
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
		
		String filename = snapshot.getFileName();
		String workingDir = filename.substring(0, filename.lastIndexOf(File.separator));
		
		if(mWorkingDir=="" && mSnapshots.size()==0)
		{
			mWorkingDir = workingDir;
			mProjectName = mWorkingDir.substring(mWorkingDir.lastIndexOf(File.separator));
			Log.i(TAG, "setting working dir : " +mWorkingDir);
		}
		else
		{
			Assert.assertTrue(workingDir.equals(mWorkingDir));
		}
		mSnapshots.add(snapshot);
		
		JSONObject jso = new JSONObject();
		try 
		{
			jso.put("roll", snapshot.getRoll());
			jso.put("yaw", snapshot.getYaw());
			jso.put("pitch", snapshot.getPitch());	
			jso.put("filename", filename.substring(mWorkingDir.length()+1, filename.length()));
			jso.put("snapshotId", mSnapshots.size());
			mJsonArray.put(jso);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * save the snapshot collection to a JSON file
	 * @param directory Directory to save the JSON file. Directories will be created if don't exists.
	 * @param filename name of the JSON file.
	 * @return absolute path + filename of the created JSon file.
	 */
	public String toJSON(String directory, String filename)
	{
		JSONObject snapshots = new JSONObject();
		String absoluteFilename = null;
		
		
		try {
			snapshots.put("panoName", filename);	
			snapshots.put("panoData", mJsonArray);
			File dir = new File(directory);
	
			if (!dir.exists()) 
			{
				dir.mkdirs();
			}
			try
			{
				absoluteFilename = genPathName(dir.getAbsolutePath(), filename);
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
	
	/**
	 * save the snapshot collection to a JSON file
	 * @param directory Directory to save the JSON file. Directories will be created if don't exists.
	 * @return absolute path + filename of the created JSon file.
	 */
	public String toJSON(String directory)
	{
		return toJSON(directory, DEFAULT_JSON_FILENAME);
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
	
	/* ******
	 * PRIVATE METHODS
	 * ******/
	/**
	 * Generate a complete pathname given the provided filename and path.
	 */
	private String genPathName(String path, String filename)
	{				
		String absoluteFilename = path+File.separator+filename;
		return absoluteFilename;
	}

	/**
	 * Group snapshots by list of neighbors
	 * @return
	 */
	public LinkedList<LinkedList<Snapshot>> getNeighbors()
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
				snapshotUrl = mWorkingDir.concat(File.separator).concat(snapshotUrl); 
				Snapshot currentSnapshot = new Snapshot(pitch, yaw, roll);
				currentSnapshot.setFileName(snapshotUrl);
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

