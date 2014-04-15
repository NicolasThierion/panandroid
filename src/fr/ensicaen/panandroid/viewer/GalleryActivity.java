package fr.ensicaen.panandroid.viewer;
 
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
 

 









import org.json.JSONException;

import fr.ensicaen.panandroid.PanandroidApplication;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.snapshot.SnapshotManager;
import fr.ensicaen.panandroid.stitcher.StitcherActivity;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.OnItemClickListener;
 
public class GalleryActivity extends Activity {
 
	private ListView mPanoListView;
	private String mCurrentFolderName;
	private static final String WORKING_DIR = PanandroidApplication.APP_DIRECTORY;
	private static final String TAG = GalleryActivity.class.getSimpleName();
 
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
 
        //Récupération de la listview créée dans le fichier main.xml
        mPanoListView = (ListView) findViewById(R.id.pano_list);
 
        //Création de la ArrayList qui nous permettra de remplir la listView
        ArrayList<HashMap<String, String>> listItem = new ArrayList<HashMap<String, String>>();
 
        //On déclare la HashMap qui contiendra les informations pour un item
        HashMap<String, String> map ;
        
        
        File workingDir = new File(this.WORKING_DIR);
        if(!workingDir.exists() || !workingDir.isDirectory())
        {
        	Log.e(TAG, WORKING_DIR+" not a valid directory");
        	return;
        }
        
        File[] files = workingDir.listFiles();
        
        
        SnapshotManager manager;
        for(File dir : files)
        {
        	if(dir.isDirectory())
        	{
        		File jsonFile = new File(dir.getAbsolutePath()+File.separator+SnapshotManager.DEFAULT_JSON_FILENAME);
        		if(jsonFile.exists())
        		{
        			map = new HashMap<String, String>();
        			try {
						manager = new SnapshotManager(jsonFile.getAbsolutePath());
						//lecture du jsonfile
	        			map.put("name", manager.getProjectName());
	        			map.put("preview", manager.getSnapshotsList().get(0).getFilename());
	        			map.put("projectFilename", jsonFile.getAbsolutePath());
	        		    listItem.add(map);
					} catch (JSONException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
        			map.put("directory",dir.getAbsolutePath());
        			
        			
        		}
        			
        		
        	}
        }
        
 
 
        //Création d'un SimpleAdapter qui se chargera de mettre les items présent dans notre list (listItem) dans la vue affichageitem
        SimpleAdapter adapter = new SimpleAdapter (this.getBaseContext(),
        		listItem, R.layout.gallery_item,
                new String[] {"preview", "name"}, 
                new int[] {R.id.preview, R.id.name});
 
        //On attribut à notre listView l'adapter que l'on vient de créer
        mPanoListView.setAdapter(adapter);
 
        //Enfin on met un écouteur d'évènement sur notre listView
        mPanoListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
        	@SuppressWarnings("unchecked")
         	public void onItemClick(AdapterView<?> a, View v, int position, long id) {
				//on récupère la HashMap contenant les infos de notre item (titre, description, img)
        		HashMap<String, String> map = (HashMap<String, String>) mPanoListView.getItemAtPosition(position);
        		Intent intent = new Intent(GalleryActivity.this,
			    		SphereViewerActivity.class);
			    intent.putExtra("projectFile", map.get("projectFilename"));
			    
			    startActivity(intent);
        	}
         });
 
    }
}