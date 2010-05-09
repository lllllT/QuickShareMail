package org.tamanegi.quicksharemail.service;

import java.util.Date;
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
import org.tamanegi.quicksharemail.ui.RetrySendActivity;
import org.tamanegi.util.StringCustomFormatter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.Toast;

public class SenderService extends Service
{
    public static final String ACTION_ENQUEUE =
        "org.tamanegi.quicksharemail.intent.action.ENQUEUE";
    public static final String ACTION_RETRY =
        "org.tamanegi.quicksharemail.intent.action.RETRY";
    public static final String ACTION_DELETE_ALL =
        "org.tamanegi.quicksharemail.intent.action.DELETE_ALL";
    public static final String ACTION_COMPLETE =
        "org.tamanegi.quicksharemail.intent.action.COMPLETE";
    public static final String ACTION_SHOW_TOAST =
        "org.tamanegi.quicksharemail.intent.action.SHOW_TOAST";

    public static final String EXTRA_ACTION = "action";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_CATEGORIES = "categories";
    public static final String EXTRA_EXTRAS = "extras";
    public static final String EXTRA_SUBJECT_FORMAT = "subjectFormat";
    public static final String EXTRA_ADDRESS = "address";

    public static final String EXTRA_MSG_STRING = "notifyMsg";
    public static final String EXTRA_MSG_DURATION = "notifyDuration";

    private static final int NOTIFY_ID_REMAIN = 0;
    private static final int NOTIFY_ID_RETRY = 1;

    private static final int REQUEST_TYPE_STOP = 1;
    private static final int REQUEST_TYPE_ENQUEUE = 2;
    private static final int REQUEST_TYPE_RETRY = 3;
    private static final int REQUEST_TYPE_DELETE_ALL = 4;

    private static final int SNIP_LENGTH = 40;

    private int req_cnt = 0;

    private volatile boolean is_running = false;
    private Thread main_thread = null;
    private LinkedBlockingQueue<Object> queue;

    private SendSetting setting;
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

        setting = new SendSetting(this);
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
            pushRequest(REQUEST_TYPE_STOP);

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

        if(req_cnt == 0) {
            stopSelfResult(startId);
        }
    }

    private int pushRequest(int req_type)
    {
        try {
            queue.put(new Integer(req_type));
            return 1;
        }
        catch(InterruptedException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(),
                           R.string.msg_fail_request,
                           Toast.LENGTH_LONG)
                .show();

            return 0;
        }
    }

    private int popRequest() throws InterruptedException
    {
        return ((Integer)queue.take()).intValue();
    }

    private void mainLoop()
    {
        try {
            while(is_running) {
                int req = popRequest();
                if(! is_running) {
                    return;
                }

                switch(req) {
                case REQUEST_TYPE_STOP:
                    return;

                case REQUEST_TYPE_RETRY:
                    clearRetryFlag();
                    break;

                case REQUEST_TYPE_DELETE_ALL:
                    deleteAllMessage();
                    break;
                }

                wakelock.acquire();
                try {
                    while(is_running) {
                        // show remaining
                        updateRemainNotification();

                        // send message, until message exists
                        if(! sendMessage()) {
                            break;
                        }
                    }
                }
                finally {
                    wakelock.release();
                }

                if(! is_running) {
                    return;
                }

                // show remaining
                updateRemainNotification();

                // complete
                startService(
                    new Intent(SenderService.ACTION_COMPLETE, null,
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
            return 0;
        }

        String action = intent.getAction();

        if(ACTION_ENQUEUE.equals(action)) {
            return pushRequest(REQUEST_TYPE_ENQUEUE);
        }
        else if(ACTION_RETRY.equals(action)) {
            return pushRequest(REQUEST_TYPE_RETRY);
        }
        else if(ACTION_DELETE_ALL.equals(action)) {
            return pushRequest(REQUEST_TYPE_DELETE_ALL);
        }
        else if(ACTION_SHOW_TOAST.equals(action)) {
            Toast.makeText(getApplicationContext(),
                           intent.getStringExtra(EXTRA_MSG_STRING),
                           intent.getIntExtra(EXTRA_MSG_DURATION,
                                              Toast.LENGTH_LONG)).
                show();
            return 0;
        }
        else if(ACTION_COMPLETE.equals(action)) {
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

        // delete processed field
        message_db.delete(msg);

        // check invalid address
        checkInvalidAddress(msg);

        return true;
    }

    private void clearRetryFlag()
    {
        // clear retry-later flag
        message_db.clearRetryFlag();
    }

    private void deleteAllMessage()
    {
        message_db.deleteAllMessage();
    }

    private void send(MessageContent msg)
    {
        try {
            String type = msg.getType();
            String body = msg.getText();
            String snip_body = snipBody(body);
            String stream = msg.getStream();
            UriDataSource attach_src = null;

            // for attachment
            if(stream != null) {
                Uri uri = Uri.parse(stream);
                attach_src = new UriDataSource(getContentResolver(), uri);
            }
            String filename =
                (attach_src != null ? attach_src.getName() : null);

            // subject
            StringCustomFormatter formatter =
                createFormatter(msg.getId(),
                                snip_body, filename,
                                msg.getDate());
            String subject = formatter.format(msg.getSubjectFormat());

            // body
            if(body == null) {
                body = (attach_src != null ?
                        formatter.format(msg.getBodyFormat()) : "");
                type = "text/plain";
            }

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
            if(attach_src != null) {
                mail.addAttachFile(attach_src);
            }

            // send mail
            MailComposer composer = new MailComposer(smtp, mail);
            composer.send();
        }
        catch(MessagingException e) {
            e.printStackTrace();
            showWarnToast(getString(R.string.msg_fail_send, e.getMessage()));
            return;
        }
        catch(SecurityException e) {
            e.printStackTrace();
            showWarnToast(
                getString(R.string.msg_fail_send,
                          getString(R.string.msg_fail_send_security)));
            return;
        }
        catch(Exception e) {
            e.printStackTrace();
            showWarnToast(getString(R.string.msg_fail_send, e.getMessage()));
            return;
        }
    }

    private StringCustomFormatter createFormatter(long id,
                                                  String snip_body,
                                                  String filename,
                                                  Date date)
    {
        return new StringCustomFormatter(
            new StringCustomFormatter.IdValue[] {
                new StringCustomFormatter.IdValue('i', String.valueOf(id)),
                new StringCustomFormatter.IdValue('s', snip_body),
                new StringCustomFormatter.IdValue('f', filename),
                new StringCustomFormatter.IdValue(
                    't', (snip_body != null ? snip_body :
                          filename != null ? filename : "")),
                new StringCustomFormatter.IdValue(
                    'T', String.format("%tT", date)),
                new StringCustomFormatter.IdValue(
                    'F', String.format("%tF", date)),
            });
    }

    private void checkInvalidAddress(MessageContent msg)
    {
        StringBuilder invalid = new StringBuilder();
        String sep = getString(R.string.address_separator);

        int invalid_cnt = 0;
        for(int i = 0; i < msg.getAddressCount(); i++) {
            MessageContent.AddressInfo addr = msg.getAddressInfo(i);

            if(addr.isProcessed() && (! addr.isValid())) {
                if(invalid_cnt > 0) {
                    invalid.append(sep);
                }
                invalid.append(addr.getAddress());
                invalid_cnt += 1;
            }
        }

        if(invalid_cnt > 0) {
            showWarnToast(getString(R.string.notify_invalid_addr, invalid));
        }
    }

    private String snipBody(String body)
    {
        if(body == null) {
            return null;
        }

        String nospbody = body.replaceAll("\\s+", " ");
        return (nospbody.length() <= SNIP_LENGTH ?
                nospbody : nospbody.substring(0, SNIP_LENGTH - 3) + "...");
    }

    private void updateRemainNotification()
    {
        // remaining count
        if(setting.isShowProgressNotification()) {
            int rest_cnt = message_db.getRestCount();
            if(rest_cnt > 0) {
                showNotification(NOTIFY_ID_REMAIN,
                                 R.drawable.status,
                                 null,
                                 getString(R.string.notify_sending),
                                 getString(R.string.notify_remain, rest_cnt),
                                 ConfigSendActivity.class,
                                 Notification.FLAG_ONGOING_EVENT,
                                 rest_cnt);
            }
            else {
                cancelNotification(NOTIFY_ID_REMAIN);
            }
        }
        else {
            cancelNotification(NOTIFY_ID_REMAIN);
        }

        // retry-later count
        int retry_cnt = message_db.getRetryCount();
        if(retry_cnt > 0) {
            String text = getString(R.string.notify_retry, retry_cnt);
            showNotification(NOTIFY_ID_RETRY,
                             R.drawable.status,
                             text,
                             getString(R.string.notify_not_processed),
                             text,
                             RetrySendActivity.class,
                             Notification.FLAG_AUTO_CANCEL,
                             retry_cnt);
        }
        else {
            cancelNotification(NOTIFY_ID_RETRY);
        }
    }

    private void showNotification(int id, int icon,
                                  CharSequence ticker_text,
                                  CharSequence content_title,
                                  CharSequence content_text,
                                  Class<?> activity_class,
                                  int flags, int number)
    {
        long when = System.currentTimeMillis();
        Notification notify = new Notification(icon, ticker_text, when);

        Intent intent = new Intent(getApplicationContext(), activity_class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent content_intent =
            PendingIntent.getActivity(this, 0, intent, 0);
        notify.setLatestEventInfo(getApplicationContext(),
                                  content_title,
                                  content_text,
                                  content_intent);
        notify.flags = flags;
        notify.number = number;

        NotificationManager nm = (NotificationManager)
            getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(id, notify);
    }

    private void cancelNotification(int id)
    {
        NotificationManager nm = (NotificationManager)
            getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(id);
    }

    private void showWarnToast(String msg)
    {
        Intent intent = new Intent(SenderService.ACTION_SHOW_TOAST, null,
                                   getApplicationContext(),
                                   SenderService.class);
        intent.putExtra(EXTRA_MSG_STRING, msg);
        intent.putExtra(EXTRA_MSG_DURATION, Toast.LENGTH_LONG);
        startService(intent);
    }
}
