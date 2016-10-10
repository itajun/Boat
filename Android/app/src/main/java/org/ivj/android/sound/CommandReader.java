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

public class CommandReader {
    public static final int LENGTH_EOC = 100;
    public static final int LENGTH_EON = 50;

    private static final byte IN_BUFFER_SIZE = 5;
    private static final String TAG = "COMMANDREADER";
    private final Handler handler;
    private AudioRecord audioRecord;
    private boolean shouldStop = false;

    private int min = Short.MAX_VALUE;
    private int max = 0;
    private boolean calibrated = false;
    private int silenceLevel;

    private int valueCount = 0;
    private int zeroCount = 0;
    private long lastTimeZero = 0;
    private long lastTimeValue = 0;
    private byte posBuffer = 0;
    private int inBuffer[] = new int[5];
    private long currentIndex = 0;
    private long total = 0;

    private int BUFFER_SIZE;

    private long threadStarted;

    public CommandReader(Handler handler) {
        //this.setDaemon(true);
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
     * <p>
     * This should be called in at start of the application in onCreate().
     */
    public AudioRecord initRecorderParameters(int[] sampleRates) {

        for (int i = 0; i < sampleRates.length; ++i) {
            try {
                Log.i(TAG, "Indexing " + sampleRates[i] + "Hz Sample Rate");
                BUFFER_SIZE = AudioRecord.getMinBufferSize(sampleRates[i],
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT) * 100;

                // Test the minimum allowed buffer size with this configuration on this device.
                if (BUFFER_SIZE != AudioRecord.ERROR_BAD_VALUE) {
                    // Seems like we have ourself the optimum AudioRecord parameter for this device.
                    AudioRecord tmpRecoder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            sampleRates[i],
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            BUFFER_SIZE);
                    // Test if an AudioRecord instance can be initialized with the given parameters.
                    if (tmpRecoder.getState() == AudioRecord.STATE_INITIALIZED) {
                        String configResume = "initRecorderParameters(sRates) has found recorder settings supported by the device:"
                                + "\nSource   = MICROPHONE"
                                + "\nsRate    = " + sampleRates[i] + "Hz"
                                + "\nChannel  = MONO"
                                + "\nEncoding = 16BIT";
                        Log.i(TAG, configResume);

                        return tmpRecoder;
                    }
                } else {
                    Log.w(TAG, "Incorrect buffer size. Continue sweeping Sampling Rate...");
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "The " + sampleRates[i] + "Hz Sampling Rate is not supported on this device");
            }
        }
        return null;
    }


    void clearBuffer() {
        for (posBuffer = IN_BUFFER_SIZE - 1; posBuffer > 0; posBuffer--) {
            inBuffer[posBuffer] = 0;
        }
    }


    public void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                threadStarted = System.currentTimeMillis();
                CommandReader.this.run();
            }
        }).start();
    }

    public void init() {
        audioRecord = initRecorderParameters(new int[]{44100});

        audioRecord.startRecording();
    }

    int map(int x, int in_min, int in_max, int out_min, int out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public void run() {
        shouldStop = false;
        int accumulatedValue = 0;

        long x = 0, y = 0;
        long lastProcessedMs = 0;

        while (!shouldStop) {
            final short[] buffer = new short[BUFFER_SIZE];
            int offset = 0;
            while (offset < BUFFER_SIZE) {
                if (shouldStop) {
                    break;
                }
                offset += audioRecord.read(buffer, offset, BUFFER_SIZE - offset);
            }

            if (!calibrated) {
                int sum = 0;
                for (int i = 0; i < buffer.length; i++) {
                    int value = Math.abs(buffer[i]);
                    sum += value;
                    if (value < min) min = value;
                    if (value > max) max = value;
                }
                silenceLevel = sum / buffer.length / 10;
                if (max - min > 100) {
                    Log.i("Reader", "Calibrated!" + min + " - " + max + " - " + silenceLevel);
                    calibrated = true;
                } else {
                    Log.i("Reader", "Failed to calibrate");
                    min = 0;
                }
                continue;
            }

            StringBuffer ms = new StringBuffer();
            byte itemsRead = 0;
            for (int i = 0; i < buffer.length; i++) {
                currentIndex++;
                itemsRead++;

                int value = Math.abs(buffer[i]);
                if (value > max) max = value;
                if (value < min) min = value;
                //value = map(value, min, max, 0, 255);

                accumulatedValue += value;

                long idxMs = toMs(currentIndex);

                if (idxMs <= lastProcessedMs) {
                    continue;
                }

                int sensorValue = accumulatedValue / itemsRead;
                sensorValue = map(sensorValue, min, max, 0, 2);
                //if (sensorValue < 200) sensorValue = 0;
                accumulatedValue = 0;
                itemsRead = 0;

                ms.append("," + sensorValue);

                if (ms.length() > 40) {
                    //Log.i(TAG, ms.toString());
                    ms = new StringBuffer();
                }


                //Log.i("XXX", " " + idxMs + " - " + sensorValue);


                if (sensorValue == 0) {
                    if ((zeroCount > 0) && (lastProcessedMs - lastTimeZero >= LENGTH_EON)) {
                        if (total > 0) {
                                Log.i("Reader", "\t length = " + total);
                            inBuffer[posBuffer++] = (int) total;
                            total = 0;
                        }

                        if (lastProcessedMs - lastTimeZero >= LENGTH_EOC) {
                            if (posBuffer > 0) {
                                Log.i("Reader", "inBuffer = ");
                                Log.i("Reader", "" + inBuffer[0] + ",");
                                Log.i("Reader", "" + inBuffer[1] + ",");
                                Log.i("Reader", "" + inBuffer[2] + ",");
                                Log.i("Reader", "" + inBuffer[3] + ",");
                                Log.i("Reader", "" + inBuffer[4] + ",");
                                Log.i("Reader", "\t prev_time_zero = ");
                                Log.i("Reader", "" + lastTimeZero);
                                Log.i("Reader", "\t prev_time_value = ");
                                Log.i("Reader", "" + lastTimeValue);
                                Log.i("Reader", "\t valueCount = ");
                                Log.i("Reader", "" + valueCount);
                                Log.i("Reader", "\t sensorMin = ");
                                Log.i("Reader", "" + min);
                                Log.i("Reader", "\t sensorMax = ");
                                Log.i("Reader", "" + max);
                                Log.i("Reader", "\t millis = ");
                                Log.i("Reader", "" + currentIndex);

                                Message message = new Message();
                                message.what = 1;
                                Bundle bundle = new Bundle();
                                bundle.putString("value", "" + inBuffer[0] + ","+ inBuffer[1] + "," + inBuffer[2] + "," + inBuffer[3] + "," + inBuffer[4] + ",");
                                message.setData(bundle);
                                handler.sendMessage(message);

                                clearBuffer();
                            }
                        }

                        valueCount = 0;
                    }

                    if (zeroCount == 0) {
                        lastTimeZero = lastProcessedMs;
                    }

                    zeroCount++;
                } else {
                    if (valueCount == 0) {
                        lastTimeValue = lastProcessedMs;
                    }
                    zeroCount = 0;
                    total = lastProcessedMs - lastTimeValue;
                    valueCount++;
                    lastTimeZero = lastProcessedMs+1;
                }

                lastProcessedMs = idxMs;
            }
        }
    }

    public long toMs(long index) {
        return (long) (index / 44.1);
    }

    public void stopReading() {
        shouldStop = true;
    }
}
