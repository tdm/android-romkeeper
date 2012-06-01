package tdm.romkeeper;

import android.app.AlarmManager;
import android.app.PendingIntent;

import android.util.Log;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class StartupReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Log.i("StartupReceiver", "onReceive called");
        SharedPreferences sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        boolean autocheck = sp.getBoolean("autocheck", false);
        if (autocheck) {
            Intent i = new Intent(ctx, ManifestCheckerService.class);
            ctx.startService(i);
        }
    }
}
