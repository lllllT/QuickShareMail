package org.tamanegi.quicksharemail.ui;

import java.util.ArrayList;
import java.util.Date;

import org.tamanegi.quicksharemail.R;
import org.tamanegi.quicksharemail.content.MessageContent;
import org.tamanegi.quicksharemail.content.MessageDB;
import org.tamanegi.quicksharemail.content.SendSetting;
import org.tamanegi.quicksharemail.content.SendToContent;
import org.tamanegi.quicksharemail.content.SendToDB;
import org.tamanegi.quicksharemail.service.SenderService;
import org.tamanegi.util.StringCustomFormatter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Parcelable;
import android.view.View;
import android.widget.EditText;

public class SendChooserActivity extends Activity
{
    private boolean is_first = true;

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
        if(sendto.length > 1 || setting.isSendToAlwaysShow() || (! is_first)) {
            // show send-to selector
            selectAndStartAndFirish(sendto);
            return;
        }

        // save send data, start service and finish
        startAndFinish(sendto[0]);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        is_first = false;
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
        CharSequence[] items = new CharSequence[sendto.length + 1];

        for(int i = 0; i < sendto.length; i++) {
            items[i] = sendto[i].getLabel();
        }
        items[sendto.length] = getString(R.string.msg_config_send_to);

        new AlertDialog.Builder(this)
            .setTitle(R.string.title_select_sendto)
            .setItems(
                items,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if(which < sendto.length) {
                            startAndFinish(sendto[which]);
                        }
                        else {
                            dialog.dismiss();
                            startActivity(new Intent(getApplicationContext(),
                                                     ConfigSendActivity.class));
                        }
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

    private void enterTextAndStartAndFinish(final SendToContent sendto)
    {
        View view = getLayoutInflater().inflate(R.layout.dialog_edittext, null);
        final EditText text_view =
            (EditText)view.findViewById(R.id.dialog_edittext);
        new AlertDialog.Builder(this)
            .setTitle(R.string.title_enter_runtime_text)
            .setView(view)
            .setPositiveButton(
                android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                        fillTextAndStartAndFinish(sendto, text_view.getText());
                    }
                })
            .setNegativeButton(
                android.R.string.cancel,
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

    private void fillTextAndStartAndFinish(SendToContent sendto,
                                           CharSequence text)
    {
        String escaped_text = text.toString().replaceAll("%", "%%");
        StringCustomFormatter formatter = new StringCustomFormatter(
            new StringCustomFormatter.IdValue[] {
                new StringCustomFormatter.IdValue('r', escaped_text),
                new StringCustomFormatter.IdValue('%', "%%")
            });

        String subject = sendto.getSubjectFormat();
        subject = formatter.format(subject);

        String body = sendto.getBodyFormat();
        body = formatter.format(body);

        sendto.setSubjectFormat(subject);
        sendto.setBodyFormat(body);

        startAndFinish(sendto);
    }

    private void startAndFinish(SendToContent sendto)
    {
        Intent intent = getIntent();

        // run-time text entry
        String subject = sendto.getSubjectFormat();
        String body = sendto.getBodyFormat();
        if(subject.matches("(^|.*[^%])%r.*") ||
           (intent.hasExtra(Intent.EXTRA_STREAM) &&
            body.matches("(^|.*[^%])%r.*"))) {
            enterTextAndStartAndFinish(sendto);
            return;
        }

        // store data
        if(! pushMessage(intent, sendto)) {
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
        String action = intent.getAction();
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);

        ArrayList<Parcelable> uris = null;
        if(Intent.ACTION_SEND.equals(action) &&
           intent.hasExtra(Intent.EXTRA_STREAM)) {
            Parcelable stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if(stream != null) {
                uris = new ArrayList<Parcelable>();
                uris.add(stream);
            }
        }
        if(Intent.ACTION_SEND_MULTIPLE.equals(action) &&
           intent.hasExtra(Intent.EXTRA_STREAM)) {
            ArrayList<Parcelable> stream =
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if(stream != null) {
                uris = stream;
            }
        }

        if(text == null && uris == null) {
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

        MessageDB message_db = new MessageDB(this);
        try {
            if(uris == null) {
                message_db.pushBack(msg);
            }
            else {
                for(Parcelable stream : uris) {
                    msg.setStream(stream.toString());
                    message_db.pushBack(msg);
                }
            }
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
