package fr.ensicaen.panandroid;

import fr.ensicaen.panandroid.capture.CaptureActivity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;

public class MenuActivity extends Activity {
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ImageView capture = (ImageView) findViewById(R.id.btn_capture);

        setContentView(R.layout.menu_activity);
        capture.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MenuActivity.this,
                        CaptureActivity.class);
                startActivity(intent);
            }
        });
    }
}
