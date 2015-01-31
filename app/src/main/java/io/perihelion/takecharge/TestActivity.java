package io.perihelion.takecharge;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class TestActivity extends Activity {

    @Override
    public void onCreate(Bundle sis) {
        super.onCreate(sis);
        Log.d("TestActivity", " onCreate, starting service");
        Intent serviceIntent = new Intent(this, MallWhereService.class);
        startService(serviceIntent);
    }
}
