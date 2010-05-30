package org.tamanegi.quicksharemail.content;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class SendSetting
{
    public static final long DEFAULT_SMTP_PORT = 587;
    public static final long DEFAULT_SMTP_SSL_PORT = 465;
    public static final String DEFAULT_SMTP_SEC = "none";
    public static final boolean DEFAULT_SENDTO_ALWAYS_SHOW = false;

    private SharedPreferences prefs;

    private static final String KEY_MAIL_FROM = "mail_from";
    private static final String KEY_SMTP_SERVER = "smtp_server";
    private static final String KEY_SMTP_PORT = "smtp_port";
    private static final String KEY_SMTP_SEC = "smtp_sec";
    private static final String KEY_SMTP_AUTH = "smtp_auth";
    private static final String KEY_SMTP_USER = "smtp_user";
    private static final String KEY_SMTP_PASS = "smtp_pass";
    private static final String KEY_SENDTO_CONFIGURED = "sendto_configured";
    private static final String KEY_SENDTO_ALWAYS_SHOW = "sendto_always_show";

    private static final String KEY_SHOW_PROGRESS = "show_progress";
    private static final String KEY_EXPAND_URL = "expand_url";
    private static final String KEY_EXPAND_TITLE = "expand_title";

    public SendSetting(Context context)
    {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public boolean checkValid()
    {
        if(! (prefs.contains(KEY_MAIL_FROM) &&
              prefs.contains(KEY_SMTP_SERVER) &&
              prefs.contains(KEY_SMTP_PORT) &&
              prefs.contains(KEY_SMTP_SEC))) {
            return false;
        }

        try {
            if(getMailFrom().length() == 0 ||
               getSmtpServer().length() == 0 ||
               getSmtpSec().length() == 0) {
                return false;
            }

            getSmtpPort();

            if(getSmtpAuth()) {
                if(! (prefs.contains(KEY_SMTP_USER) &&
                      prefs.contains(KEY_SMTP_PASS))) {
                    return false;
                }
            }

            if(! isSendToConfigured()) {
                return false;
            }
            isSendToAlwaysShow();
        }
        catch(Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public String getMailFrom()
    {
        return prefs.getString(KEY_MAIL_FROM, "");
    }

    public String getSmtpServer()
    {
        return prefs.getString(KEY_SMTP_SERVER, "");
    }

    public long getSmtpPort()
    {
        return Long.parseLong(
            prefs.getString(KEY_SMTP_PORT, String.valueOf(DEFAULT_SMTP_PORT)));
    }

    public String getSmtpSec()
    {
        return prefs.getString(KEY_SMTP_SEC, DEFAULT_SMTP_SEC);
    }

    public boolean getSmtpAuth()
    {
        return prefs.getBoolean(KEY_SMTP_AUTH, false);
    }

    public String getSmtpUser()
    {
        return prefs.getString(KEY_SMTP_USER, "");
    }

    public String getSmtpPass()
    {
        return prefs.getString(KEY_SMTP_PASS, "");
    }

    public boolean isSendToAlwaysShow()
    {
        return prefs.getBoolean(KEY_SENDTO_ALWAYS_SHOW,
                                DEFAULT_SENDTO_ALWAYS_SHOW);
    }

    public void setSendToAlwaysShow(boolean show)
    {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(KEY_SENDTO_ALWAYS_SHOW, show);
        edit.commit();
    }

    public boolean isSendToConfigured()
    {
        return prefs.getBoolean(KEY_SENDTO_CONFIGURED, false);
    }

    public void setSendToConfigured(boolean configured)
    {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(KEY_SENDTO_CONFIGURED, configured);
        edit.commit();
    }

    public boolean isShowProgressNotification()
    {
        return prefs.getBoolean(KEY_SHOW_PROGRESS, true);
    }

    public void setShowProgressNotification(boolean show)
    {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(KEY_SHOW_PROGRESS, show);
        edit.commit();
    }

    public boolean isExpandUrl()
    {
        return prefs.getBoolean(KEY_EXPAND_URL, false);
    }

    public void setExpandUrl(boolean expand_url)
    {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(KEY_EXPAND_URL, expand_url);
        edit.commit();
    }

    public boolean isRetrieveTitle()
    {
        return prefs.getBoolean(KEY_EXPAND_TITLE, false);
    }

    public void setRetrieveTitle(boolean expand_title)
    {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(KEY_EXPAND_TITLE, expand_title);
        edit.commit();
    }
}
