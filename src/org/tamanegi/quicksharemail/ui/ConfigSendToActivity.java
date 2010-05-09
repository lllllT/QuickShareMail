package org.tamanegi.quicksharemail.ui;

import org.tamanegi.quicksharemail.R;
import org.tamanegi.quicksharemail.content.SendSetting;
import org.tamanegi.quicksharemail.content.SendToContent;
import org.tamanegi.quicksharemail.content.SendToDB;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

public class ConfigSendToActivity extends ListActivity
{
    private SendSetting setting;
    private SendToDB sendto_db;

    private SendinfoAdapter adapter;
    private SendToContent[] sendinfo;

    private LayoutInflater inflater;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setting = new SendSetting(this);
        sendto_db = new SendToDB(this);

        // setup ListView
        ListView list = getListView();
        list.setItemsCanFocus(true);

        inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);

        // always show checkbox
        View always_show = inflater.inflate(R.layout.list_checkitem, null);
        always_show.setOnClickListener(new AlwaysShowOnClickListener());

        ((TextView)always_show.findViewById(R.id.list_item_title)).
            setText(R.string.title_pref_send_to_always_show);
        ((TextView)always_show.findViewById(R.id.list_item_summary)).
            setText(R.string.summary_pref_send_to_always_show);
        CheckBox always_show_check =
            (CheckBox)always_show.findViewById(R.id.list_item_checkbox);
        always_show_check.setChecked(setting.isSendToAlwaysShow());

        list.addHeaderView(always_show, null, true);

        // separator
        View list_sep = inflater.inflate(R.layout.list_separator, null);
        ((TextView)list_sep).setText(R.string.title_pref_send_to_list);
        list.addHeaderView(list_sep, null, false);

        // add-new footer
        View list_add = inflater.inflate(R.layout.list_additem, null);
        ((TextView)list_add).setText(R.string.title_pref_send_to_add);
        list_add.setOnClickListener(new AddOnClickListener());
        list.addFooterView(list_add, null, true);

        // initialize data, if not yet configured
        if(! setting.isSendToConfigured()) {
            fillInitSendTo();
            setting.setSendToConfigured(true);
        }

        // adapter
        adapter = new SendinfoAdapter();
        setListAdapter(adapter);

        // for context menu
        registerForContextMenu(list);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        updateData();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        sendto_db.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.config_send_to_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId()) {
        case R.id.menu_add:
            // add new
            startEdit(-1);
            return true;

        case R.id.menu_init:
            // initialize
            showInitializeConfirm();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuinfo)
    {
        AdapterView.AdapterContextMenuInfo minfo =
            (AdapterView.AdapterContextMenuInfo)menuinfo;
        getMenuInflater().inflate(R.menu.config_send_to_context, menu);
        menu.setHeaderTitle(sendinfo[(int)minfo.id].getLabel());
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        AdapterView.AdapterContextMenuInfo minfo =
            (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();

        switch(item.getItemId()) {
        case R.id.menu_edit:
            // start edit
            startEdit((int)minfo.id);
            return true;

        case R.id.menu_delete:
            // confirm to delete
            showDeleteConfirm((int)minfo.id);
            return true;
        }

        return super.onContextItemSelected(item);
    }

    private void fillInitSendTo()
    {
        sendto_db.deleteAll();

        SendToContent info = new SendToContent();
        info.setSubjectFormat(getString(R.string.initial_subject_format));
        info.setBodyFormat(getString(R.string.initial_body_format));
        info.setAddress(null);

        // text
        info.setLabel(getString(R.string.initial_label_text));
        info.setAllowType("text/*");
        info.setPriority(100);
        info.setEnable(false);
        info.setAlternate(false);
        sendto_db.addSendinfo(info);

        // image
        info.setLabel(getString(R.string.initial_label_image));
        info.setAllowType("image/*");
        info.setPriority(80);
        info.setEnable(false);
        info.setAlternate(false);
        sendto_db.addSendinfo(info);

        // video
        info.setLabel(getString(R.string.initial_label_movie));
        info.setAllowType("video/*");
        info.setPriority(80);
        info.setEnable(false);
        info.setAlternate(false);
        sendto_db.addSendinfo(info);

        // all
        info.setLabel(getString(R.string.initial_label_any));
        info.setAllowType("*/*");
        info.setPriority(50);
        info.setEnable(false);
        info.setAlternate(true);
        sendto_db.addSendinfo(info);
    }

    private void updateData()
    {
        retrieveData();
        adapter.notifyDataSetChanged();
    }

    private void retrieveData()
    {
        sendinfo = sendto_db.getAllSendinfo();
    }

    private void startEdit(int position)
    {
        Intent detail_edit = new Intent(getApplicationContext(),
                                        ConfigSendToDetailActivity.class);
        long id = (position >= 0 ? sendinfo[position].getId() : -1);
        detail_edit.putExtra(ConfigSendToDetailActivity.EXTRA_INFO_ID, id);

        startActivity(detail_edit);
    }

    private void showDeleteConfirm(final int position)
    {
        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.title_pref_send_to_delete)
            .setMessage(
                String.format(getString(R.string.msg_send_to_delete),
                              sendinfo[position].getLabel()))
            .setPositiveButton(
                android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                        sendto_db.deleteSendinfo(sendinfo[position]);
                        updateData();
                    }
                })
            .setNegativeButton(
                android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                    }
                })
            .setOnCancelListener(
                new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        dialog.dismiss();
                    }
                })
            .show();
    }

    private void showInitializeConfirm()
    {
        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.title_pref_send_to)
            .setMessage(R.string.msg_send_to_init)
            .setPositiveButton(
                android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                        fillInitSendTo();
                        updateData();
                    }
                })
            .setNegativeButton(
                android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                    }
                })
            .setOnCancelListener(
                new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        dialog.dismiss();
                    }
                })
            .show();
    }

    private void showWarnMessage(int msg_id)
    {
        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.title_pref_send_to)
            .setMessage(msg_id)
            .setPositiveButton(
                android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.dismiss();
                    }
                })
            .setOnCancelListener(
                new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        dialog.dismiss();
                    }
                })
            .show();
    }

    private class AlwaysShowOnClickListener implements View.OnClickListener
    {
        public void onClick(View v)
        {
            CheckBox check = (CheckBox)
                v.findViewById(R.id.list_item_checkbox);

            // invert check, and always-show flag
            check.setChecked(! check.isChecked());
            setting.setSendToAlwaysShow(check.isChecked());
        }
    }

    private class AddOnClickListener implements View.OnClickListener
    {
        public void onClick(View v)
        {
            // add new
            startEdit(-1);
        }
    }

    private class SendtoItemOnClickListener
        implements View.OnClickListener, View.OnLongClickListener
    {
        private int position;

        private SendtoItemOnClickListener(int position)
        {
            this.position = position;
        }

        public void onClick(View v)
        {
            // start edit
            startEdit(position);
        }

        public boolean onLongClick(View v)
        {
            // show context menu
            v.showContextMenu();

            return true;
        }
    }

    private class SendtoCheckOnClickListener implements View.OnClickListener
    {
        private int position;

        private SendtoCheckOnClickListener(int position)
        {
            this.position = position;
        }

        public void onClick(View v)
        {
            CheckBox check = (CheckBox)v;
            boolean val = check.isChecked();

            if(sendinfo[position].getAddressCount() < 1 && val) {
                check.setChecked(false);
                showWarnMessage(R.string.msg_send_to_no_address);
                return;
            }

            // update enable flag
            sendinfo[position].setEnable(val);
            sendto_db.updateSendinfo(sendinfo[position]);
        }
    }

    private class SendinfoAdapter extends BaseAdapter
    {
        public int getCount()
        {
            return (sendinfo != null ? sendinfo.length : 0);
        }

        public Object getItem(int position)
        {
            return sendinfo[position];
        }

        public long getItemId(int position)
        {
            return position;
        }

        public View getView(int position, View conv_view, ViewGroup parent)
        {
            View view;
            if(conv_view == null) {
                view = inflater.inflate(
                    R.layout.list_checkitem2, parent, false);
            }
            else {
                view = conv_view;
            }

            View texts = view.findViewById(R.id.list_item_texts);
            SendtoItemOnClickListener listener =
                new SendtoItemOnClickListener(position);
            texts.setOnClickListener(listener);
            texts.setOnLongClickListener(listener);

            ((TextView)view.findViewById(R.id.list_item_title)).
                setText(getTitle(position));
            ((TextView)view.findViewById(R.id.list_item_summary)).
                setText(getSummary(position));

            CheckBox check =
                (CheckBox)view.findViewById(R.id.list_item_checkbox);
            check.setChecked(sendinfo[position].isEnable());
            check.setOnClickListener(new SendtoCheckOnClickListener(position));

            return view;
        }

        private CharSequence getTitle(int position)
        {
            return sendinfo[position].getLabel();
        }

        private CharSequence getSummary(int position)
        {
            StringBuilder addrs = new StringBuilder();

            int addr_cnt = sendinfo[position].getAddressCount();
            for(int i = 0; i < addr_cnt; i++) {
                if(i != 0) {
                    addrs.append(
                        getString(R.string.address_separator));
                }

                addrs.append(sendinfo[position].getAddress(i));
            }

            return String.format(
                getString(R.string.summary_pref_send_to_item_format),
                sendinfo[position].getAllowType(),
                addrs);
        }
    }
}
