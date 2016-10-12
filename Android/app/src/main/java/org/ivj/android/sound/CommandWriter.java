package org.ivj.android.sound;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.util.Log;

import java.util.Arrays;

public class CommandWriter extends Thread {
    private static final String LOG_TAG = CommandWriter.class.getName();

    public static final int AUDIO_SAMPLE_RATE = 44100;
    public static final int TONE_FREQUENCY = 1000;
    private Handler handler;
    private WritingThread writingThread;

    public CommandWriter(Handler mHandler) {
        this.handler = handler;
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

        final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, generatedWave.length,
                AudioTrack.MODE_STATIC);
        audioTrack.write(generatedWave, 0, generatedWave.length);
        audioTrack.play();
    }

    public void stopPlaying() {
        if (writingThread != null) {
            this.writingThread.stopRunning();
        }
    }

    public void doIt() {
        this.writingThread = new WritingThread();
        this.writingThread.start();
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
            while (checkStop()) {
                try {
                    send(20);
                    Thread.sleep(5000);
                    send(10, 100, 50, 10);
                    Thread.sleep(5000);
                    send(20, 1000);
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
