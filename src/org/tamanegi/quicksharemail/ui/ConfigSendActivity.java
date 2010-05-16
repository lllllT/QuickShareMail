package org.tamanegi.quicksharemail.ui;

import org.tamanegi.quicksharemail.R;
import org.tamanegi.quicksharemail.content.SendSetting;
import org.tamanegi.quicksharemail.service.SenderService;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.widget.Toast;

public class ConfigSendActivity extends PreferenceActivity
{
    private static final long MAX_TCP_PORT_NUMBER = 65535;

    private static final String GMAIL_SERVER = "smtp.gmail.com";
    private static final String GMAIL_PORT = "587";
    private static final String GMAIL_SEC = "starttls";

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_send);

        setupSummary();
    }

    private void setupSummary()
    {
        setupMailFromSummary("mail_from", R.string.summary_def_pref_mail_from);

        setupTextSummary("smtp_server", R.string.summary_def_pref_smtp_server);
        setupNumberSummary("smtp_port",
                           SendSetting.DEFAULT_SMTP_PORT,
                           MAX_TCP_PORT_NUMBER,
                           R.string.msg_pref_smtp_port);
        setupSecListSummary("smtp_sec", SendSetting.DEFAULT_SMTP_SEC);

        setupTextSummary("smtp_user", R.string.summary_def_pref_smtp_user);

        findPreference("show_progress").setOnPreferenceChangeListener(
            new UpdateNotificationOnChangeCheck());
    }

    private void setupTextSummary(String key, int def_id)
    {
        EditTextPreference preference =
            (EditTextPreference)findPreference(key);

        preference.setOnPreferenceChangeListener(
            new UpdateSummaryOnChangeText(def_id));

        String text = preference.getText();
        if(text == null || text.length() < 1) {
            preference.setSummary(def_id);
        }
        else {
            preference.setSummary(text);
        }
    }

    private void setupMailFromSummary(String key, int def_id)
    {
        EditTextPreference preference =
            (EditTextPreference)findPreference(key);

        preference.setOnPreferenceChangeListener(
            new UpdateSummaryOnChangeMailFrom(def_id));

        String text = preference.getText();
        if(text == null || text.length() < 1) {
            preference.setSummary(def_id);
        }
        else {
            preference.setSummary(text);
        }
    }

    private void setupNumberSummary(String key, long def_val, long max,
                                    int warn_id)
    {
        EditTextPreference preference = (EditTextPreference)findPreference(key);

        preference.setOnPreferenceChangeListener(
            new UpdateSummaryOnChangeNumber(max, warn_id));

        String text = preference.getText();
        long val;
        try {
            val = Long.parseLong(text);
            if(val < 1 || val > max) {
                val = def_val;
            }
        }
        catch(NumberFormatException e) {
            val = def_val;
        }

        preference.setText(String.valueOf(val));
        preference.setSummary(String.valueOf(val));
    }

    private void setupSecListSummary(String key, String def_val)
    {
        ListPreference preference = (ListPreference)findPreference(key);

        preference.setOnPreferenceChangeListener(
            new UpdateSummaryOnChangeSecList());

        int idx = preference.findIndexOfValue(preference.getValue());
        if(idx < 0) {
            idx = preference.findIndexOfValue(def_val);
        }

        preference.setValue(preference.getEntryValues()[idx].toString());
        preference.setSummary(preference.getEntries()[idx]);
    }

    private void showGmailSetup(final String addr)
    {
        new AlertDialog.Builder(this)
            .setTitle(R.string.title_pref_smtp_ask_gmail)
            .setMessage(R.string.msg_pref_smtp_ask_gmail)
            .setPositiveButton(
                android.R.string.yes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                        setGmailDefault(addr);
                        ((EditTextRemPreference)
                         findPreference("smtp_pass")).performClick();
                    }
                })
            .setNegativeButton(
                android.R.string.no,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                    }
                })
            .show();
    }

    private void setGmailDefault(String addr)
    {
        EditTextPreference smtp_server =
            (EditTextPreference)findPreference("smtp_server");
        smtp_server.setText(GMAIL_SERVER);
        smtp_server.setSummary(GMAIL_SERVER);

        EditTextPreference smtp_port =
            (EditTextPreference)findPreference("smtp_port");
        smtp_port.setText(GMAIL_PORT);
        smtp_port.setSummary(GMAIL_PORT);

        ListPreference smtp_sec = (ListPreference)findPreference("smtp_sec");
        int idx = smtp_sec.findIndexOfValue(GMAIL_SEC);
        smtp_sec.setValue(smtp_sec.getEntryValues()[idx].toString());
        smtp_sec.setSummary(smtp_sec.getEntries()[idx]);

        CheckBoxPreference smtp_auth =
            (CheckBoxPreference)findPreference("smtp_auth");
        smtp_auth.setChecked(true);

        EditTextPreference smtp_user =
            (EditTextPreference)findPreference("smtp_user");
        smtp_user.setText(addr);
        smtp_user.setSummary(addr);
    }

    private void showPortSelect(int msg_id, long port)
    {
        final String port_str = String.valueOf(port);
        new AlertDialog.Builder(this)
            .setTitle(R.string.title_pref_smtp_ask_sec_port)
            .setMessage(msg_id)
            .setPositiveButton(
                android.R.string.yes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                        EditTextPreference smtp_port =
                            (EditTextPreference)findPreference("smtp_port");
                        smtp_port.setText(port_str);
                        smtp_port.setSummary(port_str);
                    }
                })
            .setNegativeButton(
                android.R.string.no,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                    }
                })
            .show();
    }

    private void showWarnMessage(int str_id)
    {
        Toast.makeText(this, str_id, Toast.LENGTH_LONG).show();
    }

    private class UpdateSummaryOnChangeText
        implements Preference.OnPreferenceChangeListener
    {
        private int def_id;

        private UpdateSummaryOnChangeText(int def_id)
        {
            this.def_id = def_id;
        }

        public boolean onPreferenceChange(Preference preference,
                                          Object newValue)
        {
            String str = newValue.toString();

            if(str.length() < 1) {
                preference.setSummary(def_id);
            }
            else {
                preference.setSummary(str);
            }
            return true;
        }
    }

    private class UpdateSummaryOnChangeMailFrom
        extends UpdateSummaryOnChangeText
    {
        private UpdateSummaryOnChangeMailFrom(int def_id)
        {
            super(def_id);
        }

        public boolean onPreferenceChange(Preference preference,
                                          Object newValue)
        {
            if(super.onPreferenceChange(preference, newValue)) {
                if(newValue != null &&
                   newValue.toString().indexOf("@gmail.com") >= 0) {
                    showGmailSetup(newValue.toString());
                }

                return true;
            }
            else {
                return false;
            }
        }
    }

    private class UpdateSummaryOnChangeNumber
        implements Preference.OnPreferenceChangeListener
    {
        private long max;
        private int warn_id;

        private UpdateSummaryOnChangeNumber(long max, int warn_id)
        {
            this.max = max;
            this.warn_id = warn_id;
        }

        public boolean onPreferenceChange(Preference preference,
                                          Object newValue)
        {
            long val;

            try {
                val = Long.parseLong(newValue.toString());
                if(val < 1 || val > max) {
                    showWarnMessage(warn_id);
                    return false;
                }
            }
            catch(NumberFormatException e) {
                showWarnMessage(warn_id);
                return false;
            }

            ((EditTextPreference)preference).setText(String.valueOf(val));
            preference.setSummary(String.valueOf(val));
            return false;
        }
    }

    private class UpdateSummaryOnChangeList
        implements Preference.OnPreferenceChangeListener
    {
        public boolean onPreferenceChange(Preference preference,
                                          Object newValue)
        {
            ListPreference pref = (ListPreference)preference;
            int idx = pref.findIndexOfValue(newValue.toString());
            pref.setSummary(pref.getEntries()[idx]);
            return true;
        }
    }

    private class UpdateSummaryOnChangeSecList
        extends UpdateSummaryOnChangeList
    {
        public boolean onPreferenceChange(Preference preference,
                                          Object newValue)
        {
            super.onPreferenceChange(preference, newValue);

            String cur_port =
                ((EditTextPreference)findPreference("smtp_port")).getText();
            if("ssl".equals(newValue)) {
                if(! String.valueOf(
                       SendSetting.DEFAULT_SMTP_SSL_PORT).equals(cur_port)) {
                    showPortSelect(R.string.msg_pref_smtp_ask_sec_ssl,
                                   SendSetting.DEFAULT_SMTP_SSL_PORT);
                }
            }
            else {
                if(! String.valueOf(
                       SendSetting.DEFAULT_SMTP_PORT).equals(cur_port)) {
                    showPortSelect(R.string.msg_pref_smtp_ask_sec_submission,
                                   SendSetting.DEFAULT_SMTP_PORT);
                }
            }

            return true;
        }
    }

    private class UpdateNotificationOnChangeCheck
        implements Preference.OnPreferenceChangeListener
    {
        public boolean onPreferenceChange(Preference preference,
                                          Object newValue)
        {
            startService(
                    new Intent(SenderService.ACTION_ENQUEUE, null,
                               getApplicationContext(),
                               SenderService.class));
            return true;
        }
    }
}
