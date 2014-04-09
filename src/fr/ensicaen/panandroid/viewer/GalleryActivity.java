package fr.ensicaen.panandroid.viewer;
 
import java.util.ArrayList;
import java.util.HashMap;
 

 


import fr.ensicaen.panandroid.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.OnItemClickListener;
 
public class GalleryActivity extends Activity {
 
	private ListView mFolderList;
	private String mCurrentFolderName;
	String SD_CARD_PATH = Environment.getExternalStorageDirectory().toString();
	new File(SD_CARD_PATH + "/" + "splah2.jpg");
 
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
 
        //Récupération de la listview créée dans le fichier main.xml
        mFolderList = (ListView) findViewById(R.id.folders_liste);
 
        //Création de la ArrayList qui nous permettra de remplir la listView
        ArrayList<HashMap<String, String>> listItem = new ArrayList<HashMap<String, String>>();
 
        //On déclare la HashMap qui contiendra les informations pour un item
        HashMap<String, String> map = new HashMap<String, String>();
        
        for(int i = 0; i < ; i++) {	        
        	//recuperer le nom du folder
        	mCurrentFolderName = ;
        	
        	//on insère un élément titre que l'on récupérera dans le textView titre créé dans le fichier affichageitem.xml
        	map.put("titre", );
        
        //on insère la référence à l'image (convertit en String car normalement c'est un int) que l'on récupérera dans l'imageView créé dans le fichier affichageitem.xml
        map.put("img", String.valueOf(R.drawable.folder_icon));
        //enfin on ajoute cette hashMap dans la arrayList
        listItem.add(map);
 
        //On refait la manip plusieurs fois avec des données différentes pour former les items de notre ListView
 
        map = new HashMap<String, String>();
        map.put("titre", "Excel");
        map.put("description", "Tableur");
        map.put("img", String.valueOf(R.drawable.folder_icon));
        listItem.add(map);
 
        map = new HashMap<String, String>();
        map.put("titre", "Power Point");
        map.put("description", "Logiciel de présentation");
        map.put("img", String.valueOf(R.drawable.folder_icon));
        listItem.add(map);
 
        map = new HashMap<String, String>();
        map.put("titre", "Outlook");
        map.put("description", "Client de courrier électronique");
        map.put("img", String.valueOf(R.drawable.folder_icon));
        listItem.add(map);
 
        //Création d'un SimpleAdapter qui se chargera de mettre les items présent dans notre list (listItem) dans la vue affichageitem
        SimpleAdapter mSchedule = new SimpleAdapter (this.getBaseContext(), listItem, R.layout.affiche_item,
               new String[] {"img", "titre", "description"}, new int[] {R.id.img, R.id.titre, R.id.description});
 
        //On attribut à notre listView l'adapter que l'on vient de créer
        maListViewPerso.setAdapter(mSchedule);
 
        //Enfin on met un écouteur d'évènement sur notre listView
        maListViewPerso.setOnItemClickListener(new OnItemClickListener() {
			@Override
        	@SuppressWarnings("unchecked")
         	public void onItemClick(AdapterView<?> a, View v, int position, long id) {
				//on récupère la HashMap contenant les infos de notre item (titre, description, img)
        		HashMap<String, String> map = (HashMap<String, String>) maListViewPerso.getItemAtPosition(position);
        		//on créer une boite de dialogue
        		AlertDialog.Builder adb = new AlertDialog.Builder(GalleryActivity.this);
        		//on attribut un titre à notre boite de dialogue
        		adb.setTitle("Sélection Item");
        		//on insère un message à notre boite de dialogue, et ici on affiche le titre de l'item cliqué
        		adb.setMessage("Votre choix : "+map.get("titre"));
        		//on indique que l'on veut le bouton ok à notre boite de dialogue
        		adb.setPositiveButton("Ok", null);
        		//on affiche la boite de dialogue
        		adb.show();
        	}
         });
 
    }
}