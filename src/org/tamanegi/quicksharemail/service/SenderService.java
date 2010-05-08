package org.tamanegi.quicksharemail.service;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import javax.mail.MessagingException;
import javax.mail.util.ByteArrayDataSource;

import org.tamanegi.quicksharemail.R;
import org.tamanegi.quicksharemail.content.MessageContent;
import org.tamanegi.quicksharemail.content.MessageDB;
import org.tamanegi.quicksharemail.content.SendSetting;
import org.tamanegi.quicksharemail.mail.MailComposer;
import org.tamanegi.quicksharemail.mail.UriDataSource;
import org.tamanegi.quicksharemail.ui.ConfigSendActivity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

public class SenderService extends Service
{
    private static final String TAG = "QuickShareMail";

    public static final String ACTION_ENQUEUE =
        "org.tamanegi.quicksharemail.intent.action.ENQUEUE";
    public static final String ACTION_NOTIFY =
        "org.tamanegi.quicksharemail.intent.action.NOTIFY";
    public static final String ACTION_COMPLETE =
        "org.tamanegi.quicksharemail.intent.action.COMPLETE";

    public static final String EXTRA_ACTION = "action";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_CATEGORIES = "categories";
    public static final String EXTRA_EXTRAS = "extras";
    public static final String EXTRA_SUBJECT_FORMAT = "subjectFormat";
    public static final String EXTRA_ADDRESS = "address";

    public static final String EXTRA_NOTIFY_MSG = "notifyMsg";
    public static final String EXTRA_NOTIFY_DURATION = "notifyDuration";

    private static final int NOTIFY_ID = 1;

    private static final int SNIP_LENGTH = 40;

    private int req_cnt = 0;

    private volatile boolean is_running = false;
    private Thread main_thread = null;
    private LinkedBlockingQueue<Object> queue;

    private MessageDB message_db;

    private PowerManager.WakeLock wakelock;

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        is_running = true;
        main_thread = new Thread(new Runnable() {
                public void run() {
                    mainLoop();
                }
            });
        queue = new LinkedBlockingQueue<Object>();

        message_db = new MessageDB(this);

        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                  "QuickShareMail");

        main_thread.start();
    }

    @Override
    public void onDestroy()
    {
        try {
            is_running = false;
            queue.put(new Object());

            main_thread.join();
        }
        catch(InterruptedException e) {
            e.printStackTrace();
        }

        message_db.close();

        super.onDestroy();
    }

    @Override
    public void onStart(Intent intent, int startId)
    {
        req_cnt += processRequest(intent);

        int rest_cnt = message_db.getRestCount();
        if(rest_cnt > 0) {
            int icon = R.drawable.icon;
            CharSequence txt = getString(R.string.notif_start);
            long when = System.currentTimeMillis();
            Notification notify = new Notification(icon, txt, when);

            Intent config_intent = new Intent(getApplicationContext(),
                                              ConfigSendActivity.class);
            config_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent content_intent =
                PendingIntent.getActivity(this, 0, config_intent, 0);
            notify.setLatestEventInfo(getApplicationContext(),
                                      getString(R.string.notif_title),
                                      String.format(
                                          getString(R.string.notif_text),
                                          rest_cnt),
                                      content_intent);
            notify.flags |= Notification.FLAG_AUTO_CANCEL;
            notify.number = rest_cnt;

            NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(NOTIFY_ID, notify);
        }
        else {
            NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIFY_ID);
        }

        if(req_cnt == 0) {
            stopSelfResult(startId);
        }
    }

    private void mainLoop()
    {
        Log.v(TAG, "mainLoop: start");
        try {
            while(is_running) {
                Log.v(TAG, "mainLoop: take");
                queue.take();
                if(! is_running) {
                    Log.v(TAG, "mainLoop: stop");
                    break;
                }

                wakelock.acquire();
                try {
                    while(is_running) {
                        // send message, until message exists
                        Log.v(TAG, "mainLoop: send");
                        if(! sendMessage()) {
                            break;
                        }
                    }
                    Log.v(TAG, "mainLoop: finish send");
                    finishSendMessage();
                }
                finally {
                    wakelock.release();
                }

                Log.v(TAG, "mainLoop: complete");
                // send complete count
                startService(new Intent(ACTION_COMPLETE, null,
                                        getApplicationContext(),
                                        SenderService.class));
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    private int processRequest(Intent intent)
    {
        if(intent == null) {
            Log.v(TAG, "SenderService#processRequest: null intent");
            return 0;
        }

        String action = intent.getAction();
        Log.v(TAG, "SenderService#processRequest: " + action);

        if(ACTION_ENQUEUE.equals(action)) {
            Log.v(TAG, "SenderService#processRequest: enqueue");
            try {
                queue.put(new Object());
                return 1;
            }
            catch(InterruptedException e) {
                e.printStackTrace();

                // todo: notify err
                String msg = "Failed to send";
                int duration = Toast.LENGTH_LONG;
                Toast.makeText(getApplicationContext(), msg, duration).show();
                return 0;
            }
        }
        else if(ACTION_NOTIFY.equals(action)) {
            Log.v(TAG, "SenderService#processRequest: notify");
            String msg = intent.getStringExtra(EXTRA_NOTIFY_MSG);
            int duration = intent.getIntExtra(EXTRA_NOTIFY_DURATION,
                                              Toast.LENGTH_SHORT);
            Toast.makeText(getApplicationContext(), msg, duration).show();
            return 0;
        }
        else if(ACTION_COMPLETE.equals(action)) {
            Log.v(TAG, "SenderService#processRequest: complete");
            return -1;
        }
        else {
            return 0;
        }
    }

    private boolean sendMessage()
    {
        // get message
        MessageContent msg = message_db.popFront();
        if(msg == null) {
            return false;
        }

        // send mail
        if(msg.getAddressCount() > 0) {
            send(msg);
        }

        // todo: check invalid address

        // delete processed field
        message_db.delete(msg);

        return true;
    }

    private void finishSendMessage()
    {
        // clear retry-later flag
        message_db.clearRetryFlag();
    }

    private void send(MessageContent msg)
    {
        try {
            SendSetting setting = new SendSetting(this);

            String type = msg.getType();
            String body = msg.getText();
            String stream = msg.getStream();
            Uri uri = null;
            UriDataSource attach_src = null;

            // for attachment
            if(stream != null) {
                uri = Uri.parse(stream);
                attach_src = new UriDataSource(getContentResolver(), uri);

                if(body == null) {
                    body = attach_src.getName();
                    type = "text/plain";
                }
            }

            // subject
            String subject_add = snipBody(body);
            String subject = String.format(msg.getSubjectFormat(),
                                           String.valueOf(msg.getId()),
                                           subject_add); // todo: subject

            // smtp settings
            String server = setting.getSmtpServer();
            String port = String.valueOf(setting.getSmtpPort());
            String sec_str = setting.getSmtpSec();
            int sec_type;
            if(sec_str.equals("ssl")) {
                sec_type = MailComposer.SmtpConfig.SECURITY_TYPE_SSL;
            }
            else if(sec_str.equals("starttls")) {
                sec_type = MailComposer.SmtpConfig.SECURITY_TYPE_STARTTLS;
            }
            else {
                sec_type = MailComposer.SmtpConfig.SECURITY_TYPE_NONE;
            }

            MailComposer.SmtpConfig smtp =
                new MailComposer.SmtpConfig(server, port, sec_type);

            if(setting.getSmtpAuth()) {
                smtp.setAuth(setting.getSmtpUser(), setting.getSmtpPass());
            }

            // mail content
            MailComposer.MailConfig mail =
                new MailComposer.MailConfig(setting.getMailFrom(),
                                            msg.getAddressInfo(),
                                            subject,
                                            msg.getDate());

            mail.setBody(new ByteArrayDataSource(body, type));
            if(uri != null) {
                mail.addAttachFile(attach_src);
            }

            // send mail
            MailComposer composer = new MailComposer(smtp, mail);
            composer.send();
        }
        catch(MessagingException e) {
            // todo: err
            e.printStackTrace();
            showToast("QuickShareMail: failed: " + e, Toast.LENGTH_LONG);
            return;
        }
        catch(IOException e) {
            // todo: err
            e.printStackTrace();
            showToast("QuickShareMail: failed: " + e, Toast.LENGTH_LONG);
            return;
        }

        // todo: notify success
        showToast("QuickShareMail: successed to send", Toast.LENGTH_SHORT);
    }

    private String snipBody(String body)
    {
        String nospbody = body.replaceAll("\\s+", " ");
        return (nospbody.length() <= SNIP_LENGTH ?
                nospbody : nospbody.substring(0, SNIP_LENGTH - 3) + "...");
    }

    // todo: how to show warn, etc
    private void showToast(String msg, int duration)
    {
        Intent notify = new Intent(ACTION_NOTIFY, null,
                                   getApplicationContext(),
                                   SenderService.class);

        notify.putExtra(EXTRA_NOTIFY_MSG, msg);
        notify.putExtra(EXTRA_NOTIFY_DURATION, duration);

        startService(notify);
    }
}
