package org.tamanegi.quicksharemail.ui;

import java.util.Date;

import org.tamanegi.quicksharemail.R;
import org.tamanegi.quicksharemail.content.MessageContent;
import org.tamanegi.quicksharemail.content.MessageDB;
import org.tamanegi.quicksharemail.content.SendSetting;
import org.tamanegi.quicksharemail.content.SendToContent;
import org.tamanegi.quicksharemail.content.SendToDB;
import org.tamanegi.quicksharemail.service.SenderService;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

public class SendChooserActivity extends Activity
{
    @Override
    protected void onResume()
    {
        super.onResume();

        SendSetting setting = new SendSetting(this);

        // check settings
        if(! setting.checkValid()) {
            askAndStartConfig(R.string.msg_need_setup);
            return;
        }

        // get send-to address for specified mime-type
        Intent intent = getIntent();
        String type = intent.getType();
        SendToContent[] sendto = null;

        SendToDB sendto_db = new SendToDB(this);
        try {
            // get typed sendto
            sendto = sendto_db.getSendinfo(type, false);

            if(sendto == null) {
                // get alternate
                sendto = sendto_db.getSendinfo(type, true);
            }
        }
        catch(Exception e) {
            e.printStackTrace();
            showErrorAndFinish(R.string.msg_fail_read_sendinfo);
            return;
        }
        finally {
            sendto_db.close();
        }

        // send-to address not found?
        if(sendto == null) {
            // notify sendto not found
            askAndStartConfig(getString(R.string.msg_sendto_notfound, type));
            return;
        }

        // select send-to address, if multiple send-to or always show flag
        if(sendto.length > 1 || setting.isSendToAlwaysShow()) {
            // show send-to selector
            selectAndStartAndFirish(sendto);
            return;
        }

        // save send data, start service and finish
        startAndFinish(sendto[0]);
    }

    private void askAndStartConfig(int msg_id)
    {
        askAndStartConfig(getString(msg_id));
    }

    private void askAndStartConfig(String msg)
    {
        new AlertDialog.Builder(this)
            .setCancelable(false)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.app_name)
            .setMessage(msg)
            .setPositiveButton(
                android.R.string.yes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                        startActivity(new Intent(getApplicationContext(),
                                                 ConfigSendActivity.class));
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
            .show();
    }

    private void selectAndStartAndFirish(final SendToContent[] sendto)
    {
        CharSequence[] items = new CharSequence[sendto.length];

        for(int i = 0; i < sendto.length; i++) {
            items[i] = sendto[i].getLabel();
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.title_select_sendto)
            .setItems(
                items,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        startAndFinish(sendto[which]);
                    }
                })
            .setNeutralButton(
                R.string.msg_config_send_to,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                        startActivity(new Intent(getApplicationContext(),
                                                 ConfigSendActivity.class));
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

    private void startAndFinish(SendToContent sendto)
    {
        if(! pushMessage(getIntent(), sendto)) {
            showErrorAndFinish(R.string.msg_unsupported_intent);
            return;
        }

        startService(new Intent(SenderService.ACTION_ENQUEUE, null,
                                getApplicationContext(),
                                SenderService.class));
        finish();
    }

    private boolean pushMessage(Intent intent, SendToContent sendto)
    {
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        Uri stream = (Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if(text == null && stream == null) {
            return false;
        }

        MessageContent msg = new MessageContent();
        msg.setType(intent.getType());
        msg.setSubjectFormat(sendto.getSubjectFormat());
        msg.setBodyFormat(sendto.getBodyFormat());
        msg.setAddress(sendto.getAddress());
        msg.setDate(new Date());
        if(text != null) {
            msg.setText(text.toString());
        }
        if(stream != null) {
            msg.setStream(stream.toString());
        }

        MessageDB message_db = new MessageDB(this);
        try {
            message_db.pushBack(msg);
        }
        finally {
            message_db.close();
        }

        return true;
    }

    private void showErrorAndFinish(int msg_id)
    {
        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.app_name)
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
