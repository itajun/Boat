package com.ivj.androidremotecontroller;

import android.app.Activity;

import android.os.Bundle;
import android.view.View;

import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import com.ivj.androidremotecontroller.socket.Client;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private ListView logList;
    private ArrayAdapter<String> logListAdapter;
    private EditText ipText;
    private Client client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logListAdapter = new ArrayAdapter<>(this, R.layout.log_entry, new ArrayList<String>());
        logList = (ListView) findViewById(R.id.logEntry);
        logList.setAdapter(logListAdapter);

        ipText = (EditText) findViewById(R.id.ipText);

        client = new Client();
    }

    public void pingButtonClick(View v) {
        client.setIp(ipText.getText().toString());
        logListAdapter.add("Ping");
        client.send("ping");
    }
}
