package fr.ensicaen.panandroid.capture;

import java.util.ArrayList;
import java.util.List;

public class SnapshotManager 
{
	List<Snapshot> mSnapshots;
	
	
	
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
}
