package org.ivj.android.sound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class CommandReader {
    private static final String LOG_TAG = CommandReader.class.getName();

    private static final int ACCEPT_RATES[] = {8000, 44100};

    private Handler handler;
    private AudioRecord audioRecorder;

    private int sensorMin = Short.MAX_VALUE;
    private int sensorMax = 0;
    private boolean calibrated = false;

    private int valueCount = 0;
    private int zeroCount = 0;

    private long prevTimeZero = 0;
    private long prevTimeValue = 0;

    private byte idxInBuffer = 0;
    private int inBuffer[] = new int[Constants.BUFFER_SIZE];

    private long currentIndex = 0;

    private int accumulatedValueLen = 0;

    private int audioBufferSize;
    private int audioSampleRate;
    private ReadingThread readingThread;

    public CommandReader(Handler handler) {
        this.handler = handler;
    }

    public void initAudioRecorder() {
        for (int i = 0; i < ACCEPT_RATES.length; ++i) {
            try {
                int minAudioBufferSize = AudioRecord.getMinBufferSize(ACCEPT_RATES[i],
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);

                if (minAudioBufferSize != AudioRecord.ERROR_BAD_VALUE) {
                    audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            ACCEPT_RATES[i],
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            minAudioBufferSize);

                    if (audioRecorder.getState() == AudioRecord.STATE_INITIALIZED) {
                        audioSampleRate = ACCEPT_RATES[i];
                        audioBufferSize = ACCEPT_RATES[i] * 5; // Force 5s
                        return;
                    }
                } else {
                    Log.w(LOG_TAG, "Incorrect buffer size. Continue sweeping Sampling Rate...");
                }
            } catch (IllegalArgumentException e) {
                Log.e(LOG_TAG, "The " + ACCEPT_RATES[i] + "Hz Sampling Rate is not supported on this device");
            }
        }
    }

    private void clearBuffer() {
        for (idxInBuffer = Constants.BUFFER_SIZE - 1; idxInBuffer > 0; idxInBuffer--) {
            inBuffer[idxInBuffer] = 0;
        }
    }

    private int map(int x, int in_min, int in_max, int out_min, int out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    private boolean checkCalibration(short[] buffer) {
        if (!calibrated) {
            for (int i = 0; i < buffer.length; i++) {
                int value = Math.abs(buffer[i]);
                if (value < sensorMin) sensorMin = value;
                if (value > sensorMax) sensorMax = value;
            }
            if (sensorMax - sensorMin > 100) {
                Log.i(LOG_TAG, "Calibrated!" + sensorMin + " - " + sensorMax);
                calibrated = true;
            } else {
                Log.w(LOG_TAG, "Failed to calibrate");
                sensorMin = 0;
            }
        }
        return true;
    }

    private void processCommand() {
        Message message = new Message();
        message.what = 1;
        Bundle bundle = new Bundle();
        bundle.putString("value", "" + inBuffer[0] + "," + inBuffer[1] + "," + inBuffer[2] + "," + inBuffer[3] + "," + inBuffer[4] + ",");
        message.setData(bundle);
        handler.sendMessage(message);

        clearBuffer();
    }

    public long toMs(long index) {
        return (long) ((index / (audioSampleRate / 1000d)));
    }

    public void stopReading() {
        if (readingThread != null) {
            readingThread.stopRunning();
        };
        audioRecorder.stop();
    }

    public void doIt() {
        if (audioRecorder == null) {
            initAudioRecorder();
        }
        audioRecorder.startRecording();
        this.readingThread = new ReadingThread();
        this.readingThread.start();
    }

    class ReadingThread extends Thread {
        private boolean shouldStop = false;

        public void stopRunning() {
            this.shouldStop = true;
        }

        private boolean checkStop() {
            if (shouldStop) {
                throw new IllegalStateException("Stopped by user");
            }
            return true;
        }

        private void internalRun() {
            int sensorValue = 0;
            long lastProcessedMs = 0;

            while (checkStop()) {
                final short[] buffer = new short[audioBufferSize];
                int readingOffset = 0;

                while (readingOffset < audioBufferSize && checkStop()) {
                    readingOffset += audioRecorder.read(buffer, readingOffset, audioBufferSize - readingOffset);
                }

                checkStop();

                byte indexesRead = 0;
                for (int i = 0; i < buffer.length; i++) {
                    checkStop();
                    currentIndex++;
                    indexesRead++;

                    int value = Math.abs(buffer[i]);

                    // Unlikely, but the amplitude may have changed
                    if (value > sensorMax) sensorMax = value;
                    if (value < sensorMin) sensorMin = value;

                    sensorValue += value;

                    long idxMs = toMs(currentIndex);

                    if (idxMs <= lastProcessedMs) {
                        continue;
                    }

                    sensorValue = map(sensorValue / indexesRead, sensorMin, sensorMax, 0, 2);

                    // Start next ms
                    indexesRead = 0;

                    if (sensorValue == 0) {
                        if (zeroCount == 0) {
                            prevTimeZero = lastProcessedMs;
                        }

                        zeroCount++;

                        if ((lastProcessedMs - prevTimeZero >= Constants.LENGTH_EOI)) {
                            if (idxInBuffer < Constants.BUFFER_SIZE) {
                                if (accumulatedValueLen > 0) {
                                    inBuffer[idxInBuffer++] = accumulatedValueLen;
                                    accumulatedValueLen = 0;
                                }

                                if (lastProcessedMs - prevTimeZero >= Constants.LENGTH_EOP) {
                                    if (idxInBuffer > 0) {
                                        // Indicate that we won't read anything until it is cleared
                                        idxInBuffer = Constants.BUFFER_SIZE;
                                        processCommand();
                                    }
                                }
                            }

                            valueCount = 0;
                        }
                    } else {
                        if (valueCount == 0) {
                            prevTimeValue = lastProcessedMs;
                        }
                        valueCount++;
                        zeroCount = 0;

                        accumulatedValueLen = (int) (lastProcessedMs - prevTimeValue);
                    }

                    lastProcessedMs = idxMs;
                }
            }
        }

        @Override
        public void run() {
            // Don't let Android get the exception
            try {
                internalRun();
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage(), e);
            }
        }
    }
}
