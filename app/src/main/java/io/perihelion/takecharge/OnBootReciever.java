package io.perihelion.takecharge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class OnBootReciever extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, MallWhereService.class);
        context.startService(serviceIntent);
    }

}