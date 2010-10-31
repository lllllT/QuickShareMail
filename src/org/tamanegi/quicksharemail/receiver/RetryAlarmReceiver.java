package org.tamanegi.quicksharemail.receiver;

import org.tamanegi.quicksharemail.service.SenderService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.ResultReceiver;

public class RetryAlarmReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        final PowerManager.WakeLock wakelock =
            ((PowerManager)context.getSystemService(Context.POWER_SERVICE)).
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "QuickShareMail.RetryAlarmReceiver");
        wakelock.acquire();

        Intent req = new Intent(SenderService.ACTION_RETRY, null,
                                context, SenderService.class);
        req.putExtra(
            SenderService.EXTRA_CALLBACK,
            new ResultReceiver(null) {
                @Override
                protected void onReceiveResult(int code, Bundle data) {
                    wakelock.release();
                }
            });
        context.startService(req);
    }
}
