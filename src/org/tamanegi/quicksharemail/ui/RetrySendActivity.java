package org.tamanegi.quicksharemail.ui;

import org.tamanegi.quicksharemail.R;
import org.tamanegi.quicksharemail.content.MessageDB;
import org.tamanegi.quicksharemail.service.SenderService;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;

public class RetrySendActivity extends Activity
{
    @Override
    protected void onResume()
    {
        super.onResume();

        // check count gt 0
        MessageDB message_db = new MessageDB(this);
        int cnt = 0;
        try {
            cnt = message_db.getAllCount();
        }
        finally{
            message_db.close();
        }

        if(cnt > 0) {
            // ask retry
            askAndStartAndFinish(getString(R.string.msg_ask_retry, cnt));
        }
        else {
            // no need to retry
            showMsgAndFinish(R.string.title_ask_retry_send,
                             R.string.msg_no_retry);
        }
    }

    private void askAndStartAndFinish(String msg)
    {
        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.title_ask_retry_send)
            .setMessage(msg)
            .setPositiveButton(
                android.R.string.yes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();

                        // retry
                        startService(
                            new Intent(SenderService.ACTION_RETRY, null,
                                       getApplicationContext(),
                                       SenderService.class));
                        finish();
                    }
                })
            .setNeutralButton(
                R.string.button_delete_all,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();

                        // delete all retry
                        startService(
                            new Intent(SenderService.ACTION_DELETE_ALL, null,
                                       getApplicationContext(),
                                       SenderService.class));
                        finish();
                    }
                })
            .setNegativeButton(android.R.string.no, 
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                        finish();
                    }
                })
            .setOnCancelListener(
                new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        dialog.dismiss();
                        finish();
                    }
                })
            .show();
    }

    private void showMsgAndFinish(int title_id, int msg_id)
    {
        new AlertDialog.Builder(this)
            .setTitle(title_id)
            .setMessage(msg_id)
            .setPositiveButton(
                android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                        finish();
                    }
                })
            .setOnCancelListener(
                new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        dialog.dismiss();
                        finish();
                    }
                })
            .show();
    }
}
