package com.agp.uhf.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.agp.uhf.R;

public class MainScreen extends Activity {

    private Button btnBaseApp, btnDevelopApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_screen); // Enlazamos con el layout correspondiente

        // Inicializamos los botones
        btnBaseApp = findViewById(R.id.btnBaseApp);
        btnDevelopApp = findViewById(R.id.btnDevelopApp);

        // Configuración para el primer botón (Acceder a la app base)
        btnBaseApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Al hacer clic, lanzamos la actividad de la app base
                Intent intent = new Intent(MainScreen.this, UHFMainActivity.class); // Cambia AGPActivity por la actividad que desees
                startActivity(intent); // Inicia la nueva actividad
            }
        });

        // Configuración para el segundo botón (Acceder a lo que estamos desarrollando)
        btnDevelopApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Al hacer clic, lanzamos la actividad que estás desarrollando
                Intent intent = new Intent(MainScreen.this, AGPActivity.class); // Cambia DevelopingActivity por la actividad correspondiente
                startActivity(intent); // Inicia la nueva actividad
            }
        });
    }
}
