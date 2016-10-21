package org.ivj.android.sound;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CommandWriter extends Thread implements Observer {
    private static final String LOG_TAG = CommandWriter.class.getName();

    public static final int AUDIO_SAMPLE_RATE = 44100;
    public static final int TONE_FREQUENCY = 1000;
    private AudioTrack audioTrack;
    private final CommandReader commandReader;
    private Handler handler;
    private WritingThread writingThread;
    private boolean ackReceived;
    private Queue<int[]> commandQueue;

    public CommandWriter(Handler handler, CommandReader commandReader) {
        this.handler = handler;
        this.commandReader = commandReader;
        commandReader.addObserver(this);
        this.commandQueue = new ConcurrentLinkedQueue<>();
    }

    public void postCommand(int... command) {
        commandQueue.offer(command);
        if (writingThread == null) {
            writingThread = new WritingThread();
            writingThread.start();
        }
    }

    private byte[] generateFrequency(int... duration) {
        // Total size of the array
        int totalDuration = duration.length * ((Constants.LENGTH_EOI + Constants.LENGTH_EOP) / 2);
        for (int i = 0; i < duration.length; i++) {
            totalDuration += duration[i];
        }

        // /1000 because sample rate is in hertz and durantion in ms
        final int numSamples = (int) (totalDuration * AUDIO_SAMPLE_RATE / 1000);
        final double sample[] = new double[numSamples];
        final byte result[] = new byte[2 * numSamples];

        int offset = 0;
        for (int itemIdx = 0; itemIdx < duration.length; itemIdx++) {
            // What is the last index of the wave (start silence?)
            int length = (int) (duration[itemIdx] * AUDIO_SAMPLE_RATE / 1000);
            for (int sampleIdx = offset; sampleIdx < length + offset; ++sampleIdx) {
                sample[sampleIdx] = Math.sin(2 * Math.PI * sampleIdx / (AUDIO_SAMPLE_RATE / TONE_FREQUENCY));
            }
            offset += length + (Constants.LENGTH_EOI * AUDIO_SAMPLE_RATE / 1000);
        }

        int idx = 0;
        for (final double currValue : sample) {
            // values are between 0 and 1, scale to maximun value of short
            final short val = (short) ((currValue * Short.MAX_VALUE));
            // in 16 bit wav PCM, first byte is the low order byte
            result[idx++] = (byte) (val & 0x00ff);
            result[idx++] = (byte) ((val & 0xff00) >>> 8);

        }

        return result;
    }

    public void send(int... duration) {
        Log.i(LOG_TAG, "Sending commands " + Arrays.toString(duration));

        byte[] generatedWave = generateFrequency(duration);

        audioTrack.write(generatedWave, 0, generatedWave.length);
    }

    public void stopPlaying() {
        if (writingThread != null) {
            this.writingThread.stopRunning();
        }
    }

    public void logOnScreen(String text) {
        Message message = new Message();
        message.what = 1;
        Bundle bundle = new Bundle();
        bundle.putString("value", text);
        message.setData(bundle);
        handler.sendMessage(message);
    }

    public void setup() {
        int minSize = AudioTrack.getMinBufferSize( AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT );
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minSize,
                AudioTrack.MODE_STREAM);
        audioTrack.play();

        if (!ackReceived) {
            Log.i(LOG_TAG, "Starting communication with Arduino");
            logOnScreen("Starting communication with Arduino");

            try {
                commandReader.startReading();
                for (int attempt = 0; !ackReceived; attempt++) {
                    postCommand(10); // Echo
                    Thread.sleep(5000);
                    if (attempt % 2 == 0) {
                        logOnScreen("Failed " + (attempt + 1) + " times.");
                    }
                }
                logOnScreen("Good to go!");
            } catch (InterruptedException e) {
                e.printStackTrace();
                logOnScreen("Failed " + e.getMessage());
            } finally {
                commandReader.stopReading();
            }
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg != null && ((int[]) arg)[0] < 15) {
            ackReceived = true;
        }
    }

    class WritingThread extends Thread {
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

        @Override
        public void run() {
            // Don't let Android get the exception
            try {
                internalRun();
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage(), e);
            }

        }

        private void internalRun() {
            while (checkStop() && !commandQueue.isEmpty()) {
                try {
                    int[] command = commandQueue.poll();
                    Log.i(LOG_TAG, "Sending command from thread " + command[0]);
                    send(command);
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                writingThread = null;
            }
        }
    }

    public boolean isAckReceived() {
        return ackReceived;
    }
}
