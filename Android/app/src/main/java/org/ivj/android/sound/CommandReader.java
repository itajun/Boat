package org.ivj.android.sound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Created by Itamar on 5/10/2016.
 */

public class CommandReader extends Thread {

    private static final String TAG = "COMMANDREADER";
    private final Handler handler;
    private AudioRecord audioRecord;

    private short min = Short.MAX_VALUE;
    private short max = 0;

    public CommandReader(Handler handler) {
        this.setDaemon(true);
        this.handler = handler;
    }

    /**
     * Scan for the best configuration parameter for AudioRecord object on this device.
     * Constants value are the audio source, the encoding and the number of channels.
     * That means were are actually looking for the fitting sample rate and the minimum
     * buffer size. Once both values have been determined, the corresponding program
     * variable are initialized (audioSource, sampleRate, channelConfig, audioFormat)
     * For each tested sample rate we request the minimum allowed buffer size. Testing the
     * return value let us know if the configuration parameter are good to go on this
     * device or not.
     *
     * This should be called in at start of the application in onCreate().
     *
     * */
    public AudioRecord initRecorderParameters(int[] sampleRates){

        for (int i = 0; i < sampleRates.length; ++i){
            try {
                Log.i(TAG, "Indexing "+sampleRates[i]+"Hz Sample Rate");
                int tmpBufferSize = AudioRecord.getMinBufferSize(sampleRates[i],
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);

                // Test the minimum allowed buffer size with this configuration on this device.
                if(tmpBufferSize != AudioRecord.ERROR_BAD_VALUE){
                    // Seems like we have ourself the optimum AudioRecord parameter for this device.
                    AudioRecord tmpRecoder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            sampleRates[i],
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            tmpBufferSize);
                    // Test if an AudioRecord instance can be initialized with the given parameters.
                    if(tmpRecoder.getState() == AudioRecord.STATE_INITIALIZED){
                        String configResume = "initRecorderParameters(sRates) has found recorder settings supported by the device:"
                                + "\nSource   = MICROPHONE"
                                + "\nsRate    = "+sampleRates[i]+"Hz"
                                + "\nChannel  = MONO"
                                + "\nEncoding = 16BIT";
                        Log.i(TAG, configResume);

                        return tmpRecoder;
                    }
                }else{
                    Log.w(TAG, "Incorrect buffer size. Continue sweeping Sampling Rate...");
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "The "+sampleRates[i]+"Hz Sampling Rate is not supported on this device");
            }
        }
        return null;
    }

    public void init() {
        audioRecord = initRecorderParameters(new int[] {44100});

        audioRecord.startRecording();
    }

    @Override
    public void run() {
        int SAMPLES = 4410;
        final short[] buffer = new short[SAMPLES];
        while (!Thread.interrupted()) {
            int offset = 0;
            while (offset < SAMPLES) {
                offset += audioRecord.read(buffer, 0, SAMPLES - offset);
            }
            long value = 0;
            for (int i = 0; i < buffer.length; i++) {
                value += Math.abs(buffer[i]);
            }
            value /= buffer.length;
            Message message = new Message();
            message.what = buffer.length;
            Bundle bundle = new Bundle();
            bundle.putString("value", value + "");
            message.setData(bundle);
            handler.sendMessage(message);
        }
    }
}
