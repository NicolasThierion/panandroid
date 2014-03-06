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

import android.os.Environment;




//TODO Where is the JAVADOC??!??! done.
/** 
 * creates a list of snapshots
 */
public class SnapshotManager implements SnapshotEventListener
{
	private List<Snapshot> mSnapshots;
	 
	
	/**
	 *  creates an array of snapshots
	 */
	public SnapshotManager()
	{
		mSnapshots = new ArrayList<Snapshot>();
	}
	
	
	/**
	 * add a snapshot to the list of snapshots  
	 * @param snapshot
	 */
	public void addSnapshot(Snapshot snapshot)
	{
		mSnapshots.add(snapshot);
	}
	
	/**
	 * creates a JSON file named filename from the existing snapshots
	 * @param filename name of the JSON file 
	 * @return
	 */
	public boolean createJSONFile(String filename)
	{
		String panoName = "panoname";
		
		JSONObject snapshots = new JSONObject();
		
		try {
			snapshots.put("panoName", panoName);
	
		
			JSONArray list = new JSONArray();
			
			for( int i = 0; i < mSnapshots.size(); i++)
			{
				JSONObject snapshot = new JSONObject();
				Snapshot currentSnapshot = mSnapshots.get(i);
				snapshot.put("snapshotId", currentSnapshot.getId());
				
				snapshot.put("urlName", currentSnapshot.getFileName());
				snapshot.put("pitch", currentSnapshot.getPitch());	
				snapshot.put("yaw", currentSnapshot.getYaw());
				
				list.put(snapshot);
			}
	 
			snapshots.put("panoData", list);
		 
			File directory = new File(Environment.getExternalStorageDirectory()
		                + File.separator + "Panandroid");
	
			if (!directory.exists()) {
				directory.mkdirs();
			}
			try
			{
		 
				FileWriter file = new FileWriter(directory.getAbsolutePath()+File.separator+panoName+".json");
				file.write(snapshots.toString());
				file.flush();
				file.close();
		 
			}
			catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		} catch (JSONException e1) {
			e1.printStackTrace();
			return false;
		}
		
		return true; 
	
	
	}
	
	/**
	 * Checks if the JSONFile have been correctly created
	 * @return true if the JSONFile have been created, false if not.
	 */
	public boolean createJSONFile()
	{
		return this.createJSONFile("panoName");
	}


	@Override
	public void onSnapshotTaken(byte[] pictureData, Snapshot snapshot)
	{
		
		addSnapshot(snapshot);
	}
		
}


	
	
	



	
	
	

