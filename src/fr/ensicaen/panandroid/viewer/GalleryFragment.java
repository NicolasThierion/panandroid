/*
 * Copyright (C) 2013 Saloua BENSEDDIK, Jean MARGUERITE, Nicolas THIERION
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */

package fr.ensicaen.panandroid.viewer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;















import org.json.JSONException;

import fr.ensicaen.panandroid.PanandroidApplication;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.capture.CaptureFragment;
import fr.ensicaen.panandroid.snapshot.SnapshotManager;
import fr.ensicaen.panandroid.stitcher.StitcherActivity;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Fragment through what the user car browser gallery of diaporama.
 * @author Saloua Benseddik <saloua.benseddik@ecole.ensicaen.fr>
 * @author Nicolas Thierion <nicolas.thierion@ecole.ensicaen.fr>
 */
public class GalleryFragment extends Fragment {
    /**************
     * ATTRIBUTES *
     **************/
    /** View of the fragment */
    private View mRoot;


        private ListView mPanoListView;
        private String mCurrentFolderName;
        private static final String WORKING_DIR = PanandroidApplication.APP_DIRECTORY;
        public static final String TAG = GalleryActivity.class.getSimpleName();

    /** Called when the activity is first created. */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        mRoot = inflater.inflate(R.layout.activity_gallery, container, false);

        //Récupération de la listview créée dans le fichier main.xml
        mPanoListView = (ListView) mRoot.findViewById(R.id.pano_list);

        //Création de la ArrayList qui nous permettra de remplir la listView
        ArrayList<HashMap<String, String>> listItem = new ArrayList<HashMap<String, String>>();

        //On déclare la HashMap qui contiendra les informations pour un item
        HashMap<String, String> map ;


        File workingDir = new File(this.WORKING_DIR);
        if(!workingDir.exists() || !workingDir.isDirectory())
        {
                Log.e(TAG, WORKING_DIR+" not a valid directory");
                return null;
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
        SimpleAdapter adapter = new SimpleAdapter (getActivity().getBaseContext(),
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
                        Intent intent = new Intent(getActivity(),
                                        SphereViewerActivity.class);
                            intent.putExtra("projectFile", map.get("projectFilename"));

                            startActivity(intent);
                }
         });

        return mRoot;
    }

        @Override
        public void onResume() {
            super.onResume();
            mRoot.requestLayout();
        }

}
