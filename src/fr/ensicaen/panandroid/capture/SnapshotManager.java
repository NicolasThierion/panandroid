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

public class SnapshotManager 
{
	private List<Snapshot> mSnapshots;
	
	
	
	public SnapshotManager()
	{
		mSnapshots = new ArrayList<Snapshot>();
	
	}
	
	
	/**
	 * 
	 * @param snapshot
	 */
	public void addSnapshot(Snapshot snapshot)
	{
		
		mSnapshots.add(snapshot);
		
	}
	
	public boolean createJSONFile()
	{
		String panoName = "panoname";
		String panoData;
		
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
	
}

	
	
	

