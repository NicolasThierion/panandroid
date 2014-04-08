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

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;
import fr.ensicaen.panandroid.PanandroidApplication;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.tools.XmpUtil;


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
	
	
	/* ******
	 * ATTRIBUTES
	 * *****/
	/* ***
	 * project settings
	 * ***/
	/** list of snapshots that compose the project **/
	private LinkedList<Snapshot> mSnapshots;
	/** project name **/
	private String mProjectName="";
	/** Folder that contain the json project file and all the jpegs. **/
	private String mWorkingDir;
	/** initial yaw angle where panorama is pointing **/
	private float mHeading=0;	//[deg]
	/** step angle between each snapshot in pitch axis **/
	private float mPitchStep; 	//[deg]
	/** step angle between each snapshot in yaw axis **/
	private float mYawStep;		//[deg]
	/** count of images that composes the panorama **/
	private int mImageCount;
	/** where the result jpeg panorama is stored **/
	private String mPanoFilePath;
	/** angle range covered by the panorama **/
	private float mMinPitch = -91;
	private float mMinYaw = -181;
	private float mMaxPitch = 91;
	private float mMaxYaw = 181;
	
	/* ***
	 * camera properties
	 * ***/
	/** camera resolution **/
	private int mCameraW, mCameraH;		//[px]
	/** camera field of view angles **/
	private float mHFov;
	private float mVFov;

	/* ***
	 * resulting panorama properties
	 * ***/
	/** left padding size needed in final full panorama **/
	private int mPaddingL = 0;			//[px]
	/** top padding size needed in final full panorama **/
	private int mPaddingT = 0;			//[px]
	/** width of the usefull part of the panorama **/
	private int mCropPanoW = 0;			//[px]
	/** height of the usefull part of the panorama **/
	private int mCropPanoH = 0;			//[px]
	/** width of the final full panorama (crop + padding) **/
	private int mFullPanoW = 0;			//[px]
	/** height of the final full panorama (crop + padding) **/
	private int mFullPanoH = 0;			//[px]

	
	
	/* ******
	 * CONSTRUCTOR
	 * ******/
	/**
	 *  creates an array of snapshots
	 */
	public SnapshotManager(String projectName, int cameraWidth, int cameraHeight, float cameraHFov, float cameraVFov, float pitchStep, float yawStep)
	{
		mSnapshots = new LinkedList<Snapshot>();
		mWorkingDir="";
		mProjectName = projectName;
		mPitchStep  = pitchStep;
		mYawStep = yawStep;
		mCameraW =cameraWidth;
		mCameraH = cameraHeight;	
		mHFov = cameraHFov;
		mVFov = cameraVFov;

	}
	
	public SnapshotManager(String jsonFilename) throws JSONException, IOException
	{
		loadJson(jsonFilename);
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
			mPanoFilePath = mWorkingDir+mProjectName+".jpg";
			
			Log.i(TAG, "setting working dir : " +mWorkingDir);
		}
		else
		{
			Assert.assertTrue(workingDir.equals(mWorkingDir));
		}
		snapshot.setId(mSnapshots.size());
		mSnapshots.add(snapshot);
	}


	
	/**
	 * save the snapshot collection to a JSON file, at the current working dir.
	 * @param directory Directory to save the JSON file. Directories will be created if don't exists.
	 * @param filename name of the JSON file.
	 * @return absolute path + filename of the created JSon file.
	 */
	public String toJSON(String filename)
	{
		JSONObject jsonDoc = new JSONObject();
		JSONArray jsonArray = new JSONArray();

		String absoluteFilename = null;
		
		try {
			jsonDoc.put("panoName", mProjectName);	
			jsonDoc.put("heading", mHeading);

			jsonDoc.put("pitchStep", mPitchStep);
			jsonDoc.put("yawStep", mYawStep);
			
			jsonDoc.put("cameraWidth", mCameraW);
			jsonDoc.put("cameraHeight", mCameraH);
			
			jsonDoc.put("cropPanoW", mCropPanoW);
			jsonDoc.put("cropPanoH", mCropPanoH);
        
			jsonDoc.put("fullPanoW", mFullPanoW);
			jsonDoc.put("fullPanoH", mFullPanoH);
			
			jsonDoc.put("minPitch", mMinPitch);
			jsonDoc.put("minYaw", mMinYaw);
			jsonDoc.put("maxPitch", mMaxPitch);
			jsonDoc.put("maxYaw", mMaxYaw);
			
			jsonDoc.put("HFov", mHFov);
			jsonDoc.put("VFov", mVFov);
			
			jsonDoc.put("paddingL", mPaddingL);
			jsonDoc.put("paddingT", mPaddingT);
			
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
			
			jsonDoc.put("panoData", jsonArray);

			try
			{
				absoluteFilename =  mWorkingDir+File.separator+filename;
				FileWriter file = new FileWriter(absoluteFilename);
				file.write(jsonDoc.toString());
				file.flush();
				file.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
				return null;
			}
		} 
		catch (JSONException e1) 
		{
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
	
	
	


	/**
	 * Group snapshots by list of neighbors
	 * @return
	 */
	public LinkedList<LinkedList<Snapshot>> getNeighborsList()
	{
		LinkedList<LinkedList<Snapshot>> neighborsList = new LinkedList<LinkedList<Snapshot>>();
	
		float maxDistance = mPitchStep + mYawStep;
		maxDistance+=maxDistance/2;
		
		for( int i = 0; i < mSnapshots.size(); i++ )
		{
			LinkedList<Snapshot> currentList = new LinkedList<Snapshot>();
			neighborsList.add(currentList);		
			
			Snapshot currentSnapshot = mSnapshots.get(i);
			currentList.add(currentSnapshot);	
			for (int j = i+1; j < mSnapshots.size(); j++)
			{
				Snapshot currentNeighbor = mSnapshots.get(j);
				if(currentSnapshot.getDistance(currentNeighbor) < maxDistance )
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
		
		float maxDistance = mPitchStep + mYawStep;
		maxDistance+=maxDistance/2;
		for( int i = 0; i < mSnapshots.size(); i++ )
		{
			LinkedList<Integer> currentList = new LinkedList<Integer>();
			neighborsList.add(currentList);		
			
			Snapshot currentSnapshot = mSnapshots.get(i);
			currentList.add(i);	
			for (int j = i+1; j < mSnapshots.size(); j++)
			{
				Snapshot currentNeighbor = mSnapshots.get(j);
				if(currentSnapshot.getDistance(currentNeighbor) < maxDistance )
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
	 * @throws JSONException 
	 * @throws IOException 
	 */
	public boolean loadJson(String filename) throws JSONException, IOException
	{
		//read json file
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
		mWorkingDir = filename.substring(0, filename.lastIndexOf(File.separator));
		mHeading = jsonSnapshots.getInt("heading");

		mPitchStep = (float) jsonSnapshots.getDouble("pitchStep");
		mYawStep = (float) jsonSnapshots.getDouble("yawStep");
		
		mCameraW = jsonSnapshots.getInt("cameraWidth");
		mCameraH = jsonSnapshots.getInt("cameraHeight");
		
		mHFov = (float) jsonSnapshots.getDouble("HFov");
		mVFov = (float) jsonSnapshots.getDouble("VFov");
		
		mPaddingL = jsonSnapshots.getInt("paddingL");
		mPaddingT = jsonSnapshots.getInt("paddingT");
		
		mCropPanoW =  jsonSnapshots.getInt("cropPanoW");
		mCropPanoH =  jsonSnapshots.getInt("cropPanoH");
		
		mFullPanoW = jsonSnapshots.getInt("fullPanoW");
		mFullPanoH = jsonSnapshots.getInt("fullPanoH");
		
		mMinPitch = (float) jsonSnapshots.getDouble("minPitch");
		mMinYaw = (float) jsonSnapshots.getDouble("minYaw");
		mMaxPitch = (float) jsonSnapshots.getDouble("maxPitch");
		mMaxYaw = (float) jsonSnapshots.getDouble("maxYaw");	
		
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
		mPanoFilePath = mWorkingDir+File.separator + mProjectName+".jpg";
		return true; 
	}
	
	/* ******
	 * GETTERS
	 * ******/
	public LinkedList<Snapshot> getSnapshotsList() 
	{
		return mSnapshots;
	}

	public String getWorkingDir()
	{
		return mWorkingDir;
	}
	public float getPitchStep()
	{
		return mPitchStep;
	}
	
	public float getYawStep()
	{
		return mYawStep;
	}
	
	public int getCameraWidth()
	{
		return mCameraW;
	}

	public int getCameraHeight()
	{
		return mCameraH;
	}
	
	public String getProjectName()
	{
		return mProjectName;
	}

	public float getCameraHFov()
	{
		return mHFov;
	}

	public float getCameraVFov()
	{
		return mVFov;
	}
	
	/* ******
	 * SETTERS
	 * ******/

	public void setHeading(float heading)
	{
		mHeading = heading;
	}

	
	public void setCropPanoWidth(int width)
	{
		mCropPanoW = width;
	}
	
	public void setCropPanoHeight(int height)
	{
		mCropPanoH = height;
	}
	
	public void setLeftPadding(int paddingL)
	{
		mPaddingL = paddingL;
	}
	
	public void setTopPadding(int paddingT)
	{
		mPaddingT = paddingT;
	}
	
	public void setWorkingDir(String workingDir)
	{
		mWorkingDir = workingDir;
	}
	
	public void setProjectName(String name)
	{
		mProjectName = name;
		mPanoFilePath = mWorkingDir+File.separator + mProjectName+".jpg";

	}
	
	public void setFullPanoWidth(int width)
	{
		mFullPanoW = width;
	}
	
	public void setFullPanoHeight(int height)
	{
		mFullPanoH = height;
	}
	
	public void setBounds(float bounds[][])
	{
		mMinPitch = bounds[0][0];
		mMinYaw = bounds[0][1];
		mMaxPitch = bounds[1][0];
		mMaxYaw = bounds[1][1];
	}

	public void doPhotoSphereTagging() {
		XMPMeta xmpData = generatePhotoSphereXMP();
		
		
		XmpUtil.writeXMPMeta(mWorkingDir+File.separator + mProjectName+".jpg", xmpData);
		
	}
	
	
	/**
     * Generate PhotoSphere XMP data file to apply on panorama images
     * https://developers.google.com/photo-sphere/metadata/
     *
     * @param width The width of the panorama
     * @param height The height of the panorama
     * @return RDF XML XMP metadata
     */
    private XMPMeta generatePhotoSphereXMP() {
 	    	
    	String xmpData =  "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
                "<rdf:Description rdf:about=\"\" xmlns:GPano=\"http://ns.google.com/photos/1.0/panorama/\">\n" +
                "    <GPano:UsePanoramaViewer>True</GPano:UsePanoramaViewer>\n" +
                "    <GPano:CaptureSoftware>"+ PanandroidApplication.getContext().getString(R.string.app_name)+"</GPano:CaptureSoftware>\n" +
                "    <GPano:StitchingSoftware>" + PanandroidApplication.getContext().getString(R.string.app_name) + " with OpenCV</GPano:StitchingSoftware>\n" +
                "    <GPano:ProjectionType>equirectangular</GPano:ProjectionType>\n" +
                "    <GPano:PoseHeadingDegrees>350.0</GPano:PoseHeadingDegrees>\n" +
                "    <GPano:InitialViewHeadingDegrees>"+mHeading+"</GPano:InitialViewHeadingDegrees>\n" +
                "    <GPano:InitialViewPitchDegrees>"+0+"</GPano:InitialViewPitchDegrees>\n" +
                "    <GPano:InitialViewRollDegrees>"+0+"</GPano:InitialViewRollDegrees>\n" +
                "    <GPano:InitialHorizontalFOVDegrees>"+45+"</GPano:InitialHorizontalFOVDegrees>\n" +
                "    <GPano:CroppedAreaLeftPixels>"+mPaddingL+"</GPano:CroppedAreaLeftPixels>\n" +
                "    <GPano:CroppedAreaTopPixels>"+mPaddingT+"</GPano:CroppedAreaTopPixels>\n" +
                "    <GPano:CroppedAreaImageWidthPixels>"+(mCropPanoW)+"</GPano:CroppedAreaImageWidthPixels>\n" +
                "    <GPano:CroppedAreaImageHeightPixels>"+(mCropPanoH)+"</GPano:CroppedAreaImageHeightPixels>\n" +
                "    <GPano:FullPanoWidthPixels>"+mFullPanoW+"</GPano:FullPanoWidthPixels>\n" +
                "    <GPano:FullPanoHeightPixels>"+mFullPanoH+"</GPano:FullPanoHeightPixels>\n" +
                //"    <GPano:FirstPhotoDate>2012-11-07T21:03:13.465Z</GPano:FirstPhotoDate>\n" +
                //"    <GPano:LastPhotoDate>2012-11-07T21:04:10.897Z</GPano:LastPhotoDate>\n" +
                "    <GPano:SourcePhotosCount>"+mImageCount+"</GPano:SourcePhotosCount>\n" +
                "    <GPano:ExposureLockUsed>False</GPano:ExposureLockUsed>\n" +
                "</rdf:Description></rdf:RDF>";
    	
    	XMPMeta xmpMeta;
		try {
			xmpMeta = XMPMetaFactory.parseFromString(xmpData);
	    	return xmpMeta;

		} catch (XMPException e) {
			e.printStackTrace();
		}
    	
		return null;
    }

	public void setNbUsedImages(int imagesGount) {
		mImageCount = imagesGount;
		
	}

	public float getMinPitch() {
		return mMinPitch;
	}
	
	public float getMinYaw() {
		return mMinYaw;
	}
	
	public float getMaxPitch() {
		return mMaxPitch;
	}
	
	public float getMaxYaw() {
		return mMaxYaw;
	}

	public String getPanoramaJpgPath() {
		return mPanoFilePath;
	}
	
}

