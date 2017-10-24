package com.example.bernardo.evma_client;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

/**
 * Created by Bernardo Graça
 * Rui Furtado
 * Bernardo Rodrigues
 */

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        ImageButton imageButton = (ImageButton) findViewById(R.id.imageButton);
        //Button to go to Sign In activity
        ImageButton sign_in = (ImageButton) findViewById(R.id.imageButton);
        assert sign_in != null;
        sign_in.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent goSign_In = new Intent(v.getContext(), Evma_Client.class);
                startActivity(goSign_In);
            }
        });
    }
}
