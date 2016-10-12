
package org.ivj.android;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.ivj.android.R;

import org.ivj.android.sound.CommandReader;
import org.ivj.android.sound.CommandWriter;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String LOG_TAG = MainActivity.class.getName();

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(final Message message) {
            if (message.what > 0) {
                if (message.getData() != null) {
                    String value = message.getData().getString("value");
                    Log.d(LOG_TAG, "Message received: " + value);
                    logListAdapter.add(value);
                }
            }
        }
    };

    private final Handler writerHandler = new Handler();

    private ListView logList;
    private ArrayAdapter<String> logListAdapter;

    private CommandReader commandReader;
    private CommandWriter commandWriter;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        logListAdapter = new ArrayAdapter<>(this, R.layout.logentry, new ArrayList<String>());
        logList = (ListView) findViewById(R.id.log_list);
        logList.setAdapter(logListAdapter);

        commandReader = new CommandReader(handler);
        commandWriter = new CommandWriter(handler);
    }

    public void onClick_Toggle(final View view) {
        ToggleButton tb = (ToggleButton) view;
        if (tb.isChecked()) {
            commandReader.doIt();
            commandWriter.doIt();
        } else {
            commandReader.stopReading();
            commandWriter.stopPlaying();
        }
    }
}
