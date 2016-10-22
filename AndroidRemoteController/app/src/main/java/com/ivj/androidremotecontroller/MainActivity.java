package com.ivj.androidremotecontroller;

import android.app.Activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
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

    private static final String LOG_TAG = MainActivity.class.getName();

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(final Message message) {
            if (message.what > 0) {
                if (message.getData() != null) {
                    String value = message.getData().getString("value");
                    Log.d(LOG_TAG, "Message received: " + value);
                    logListAdapter.add(value);
                    logList.setSelection(logList.getCount() - 1);
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logListAdapter = new ArrayAdapter<>(this, R.layout.log_entry, new ArrayList<String>());
        logList = (ListView) findViewById(R.id.logEntry);
        logList.setAdapter(logListAdapter);

        ipText = (EditText) findViewById(R.id.ipText);

        client = new Client(handler);
    }

    public void pingButtonClick(View v) {
        client.setIp(ipText.getText().toString());
        logListAdapter.add("Ping");
        client.send("command.ping");
    }

    public void runButtonClick(View v) {
        client.setIp(ipText.getText().toString());
        logListAdapter.add("Run");
        client.send("command.arduino.run");
    }


    public void stopButtonClick(View v) {
        client.setIp(ipText.getText().toString());
        logListAdapter.add("Stop");
        client.send("command.arduino.stop");
    }

}
