package com.ivj.androidremotecontroller.socket;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Itamar on 19/10/2016.
 */

public class Client {
    public static final int PORT = 8080;
    private final Handler handler;
    private String ipAddress;
    private Queue<String> commands = new ConcurrentLinkedQueue<>();
    private Thread thread;

    public Client(Handler handler) {
        this.handler = handler;
    }

    public void logOnScreen(String text) {
        Message message = new Message();
        message.what = 1;
        Bundle bundle = new Bundle();
        bundle.putString("value", text);
        message.setData(bundle);
        handler.sendMessage(message);
    }

    public void send(String command) {
        commands.offer(command);
        if (thread == null) {
            thread = new ClientThread();
            thread.start();
        }
    }

    public void setIp(String ip) {
        this.ipAddress = ip;
    }

    public class ClientThread extends Thread {
        private boolean shouldStop;

        public void stopRunning() {
            this.shouldStop = true;
        }

        public void run() {
            try {
                InetAddress serverAddr = InetAddress.getByName(ipAddress);
                Log.d("ClientActivity", "C: Connecting...");
                while (!shouldStop && !commands.isEmpty()) {
                    Socket socket = new Socket(serverAddr, PORT);
                    try {
                        Log.d("ClientActivity", "C: Sending command.");
                        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket
                                .getOutputStream())), true);
                        out.println(commands.poll());
                        Log.d("ClientActivity", "C: Sent.");
                        for (int i = 0; i < 10; i++) {
                            Thread.sleep(100);
                            if (socket.isClosed()) {
                                break;
                            }
                            if (socket.getInputStream().available() > 0) {
                                String line = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
                                Log.d("ClientActivity", "Received back: " + line);
                                logOnScreen(line);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("ClientActivity", "S: Error", e);
                    }
                    if (!socket.isClosed()) {
                        socket.close();
                    }
                }
                Log.d("ClientActivity", "C: Closed.");
            } catch (Exception e) {
                Log.e("ClientActivity", "C: Error", e);
            }

            thread = null;
        }
    }
}
