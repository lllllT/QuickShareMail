package org.tamanegi.quicksharemail.receiver;

import org.tamanegi.quicksharemail.service.SenderService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Parcelable;

public class NetworkStateChangeReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        Parcelable info = intent.getParcelableExtra(
            ConnectivityManager.EXTRA_NETWORK_INFO);
        if(info == null || (! (info instanceof NetworkInfo))) {
            return;
        }

        NetworkInfo networkinfo = (NetworkInfo)info;
        if(networkinfo.isConnected()) {
            context.startService(
                new Intent(SenderService.ACTION_RETRY, null,
                           context, SenderService.class));
        }
    }
}
