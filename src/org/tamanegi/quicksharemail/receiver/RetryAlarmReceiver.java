package org.tamanegi.quicksharemail.receiver;

import org.tamanegi.quicksharemail.service.SenderService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RetryAlarmReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        context.startService(
            new Intent(SenderService.ACTION_RETRY, null,
                       context, SenderService.class));
    }
}
