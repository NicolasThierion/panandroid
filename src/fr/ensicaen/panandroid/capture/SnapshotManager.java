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
package fr.ensicaen.panandroid.capture;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;




/** 
 * creates a list of snapshots
 */
public class SnapshotManager implements SnapshotEventListener
{
	/* ******
	 * PARAMETERS
	 * *****/
	private static final String DEFAULT_JSON_FILENAME = "PanoData.json";
	
	
	/* ******
	 * ATTRIBUTES
	 * *****/
	private List<Snapshot> mSnapshots;
	private JSONArray mJsonArray;
	
	
	/* ******
	 * CONSTRUCTOR
	 * ******/
	/**
	 *  creates an array of snapshots
	 */
	public SnapshotManager()
	{
		mSnapshots = new ArrayList<Snapshot>();
		mJsonArray = new JSONArray();
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
		mSnapshots.add(snapshot);
		
		JSONObject jso = new JSONObject();
		try 
		{
			jso.put("snapshotId", snapshot.getId());
			jso.put("urlName", snapshot.getFileName());
			jso.put("pitch", snapshot.getPitch());	
			jso.put("yaw", snapshot.getYaw());
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
		
}


	
	
	



	
	
	

