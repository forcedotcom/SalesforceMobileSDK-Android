package com.salesforce.androidsdk.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.config.LoginServerManager;
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer;

import java.util.List;

public class ServerPickerAdapter extends ArrayAdapter<LoginServer> {

    private final int resourceId;
    private final LoginServerManager loginServerManager;

    public ServerPickerAdapter(Context context, int resource, List<LoginServer> objects, LoginServerManager loginServerManager) {
        super(context, resource, objects);
        this.resourceId = resource;
        this.loginServerManager = loginServerManager;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        LoginServer loginServer = getItem(position);
        View view;
        ViewHolder viewHolder;
        if (convertView == null) {
            view = LayoutInflater.from(getContext()).inflate(resourceId, null);
            viewHolder = new ViewHolder();
            viewHolder.serverNameTextView = view.findViewById(R.id.text_server_name);
            viewHolder.serverUrlTextView = view.findViewById(R.id.text_server_url);
            viewHolder.radioButton = view.findViewById(R.id.radio_btn);
            view.setTag(viewHolder);
        } else {
            view = convertView;
            viewHolder = (ViewHolder) view.getTag();
        }
        viewHolder.serverNameTextView.setText(loginServer.name);
        viewHolder.serverUrlTextView.setText(loginServer.url);
        final LoginServer selectedServer = loginServerManager.getSelectedLoginServer();
        if (null != selectedServer.url && selectedServer.url.equals(loginServer.url)) {
            viewHolder.radioButton.setVisibility(View.VISIBLE);
            viewHolder.radioButton.setChecked(true);
        } else {
            viewHolder.radioButton.setVisibility(View.GONE);
        }
        return view;

    }

    class ViewHolder {
        TextView serverNameTextView;
        TextView serverUrlTextView;
        RadioButton radioButton;
    }
}
