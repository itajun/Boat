//Jack by Wolf Paulus is licensed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License.
package org.ivj.android;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.ivj.android.R;

import org.ivj.android.sound.CommandReader;
import org.ivj.android.sound.CommandWriter;

/**
 * Launcher Activity
 *
 * @author <a href="mailto:wolf@wolfpaulus.com">Wolf Paulus</a>
 */
public class JackActivity extends Activity {
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            if (0 >= msg.what) {
                mTextView.setText("");
            } else {
                if (msg.getData() != null) {
                    String s = msg.getData().getString("value");
                    mTextView.setText(s);
                    try {
                        mProgressBar.setProgress(Integer.parseInt(s) / 10);
                    } catch (NumberFormatException e) {
                        // intentionally empty
                    }
                }
            }
        }
    };

    private final Handler writerHandler = new Handler();

    private TextView mTextView;
    private ProgressBar mProgressBar;
    private CommandReader mReceiver;
    private CommandWriter mWriter;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mTextView = (TextView) findViewById(R.id.tv);
        mTextView.setMovementMethod(new ScrollingMovementMethod());
        mTextView.setText("");

        mProgressBar = (ProgressBar) findViewById(R.id.pb);
        mProgressBar.setProgress(0);

        mReceiver = new CommandReader(mHandler);
        mReceiver.init();

        mWriter = new CommandWriter();
    }

    @Override
    protected void onPause() {
        super.onPause();
        System.runFinalizersOnExit(true);
        System.exit(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @SuppressWarnings("UnusedParameters")
    public void onClick_Toggle(final View view) {
        ToggleButton tb = (ToggleButton) view;
        if (tb.isChecked()) {
            mReceiver.start();
            final Thread thread = new CommandWriter();
            thread.start();
        } else {
            mReceiver.stopReading();
            mWriter.stopPlaying();
        }
    }
}
