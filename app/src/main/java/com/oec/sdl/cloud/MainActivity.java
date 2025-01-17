package com.oec.sdl.cloud;

import android.content.Intent;
//import android.support.v7.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //If we are connected to a module we want to start our SdlService
        if(BuildConfig.TRANSPORT.equals("MULTI") || BuildConfig.TRANSPORT.equals("MULTI_HB")) {
            //SdlReceiver.queryForConnectedService(this);
            Intent sdlServiceIntent = new Intent(this, SdlService.class); // used for TCP
            startService(sdlServiceIntent);
        }else if(BuildConfig.TRANSPORT.equals("TCP")) {
            Intent proxyIntent = new Intent(this, SdlService.class);
            startService(proxyIntent);
        }
    }
}
