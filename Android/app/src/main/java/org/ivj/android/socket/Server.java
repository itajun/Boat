package org.ivj.android.socket;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.ivj.android.sound.Constants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

public class Server {
    public static String SERVERIP = "10.1.1.10";

    public static final int SERVERPORT = 8080;

    private Handler handler;

    private ServerSocket serverSocket;

    private ServerThread thread;

    public Server(Handler handler) {
        SERVERIP = getLocalIpAddress();
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

    public class ServerThread extends Thread {
        boolean shouldStop = false;

        public void stopRunning() {
            shouldStop = true;
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                if (SERVERIP != null) {
                    logOnScreen("Listening on ip " + SERVERIP);
                    serverSocket = new ServerSocket(SERVERPORT);
                    while (!shouldStop) {
                        Socket client = serverSocket.accept();
                        logOnScreen("Connected");

                        try {
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            String line = null;
                            while ((line = in.readLine()) != null) {
                                Log.d("ServerActivity", line);
                                final String message = "Received: " + line;
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        logOnScreen(message);
                                    }
                                });
                                if (line.equals("ping")) {
                                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(client
                                            .getOutputStream())), true);
                                    out.println("pong");
                                    out.flush();
                                }
                            }
                            client.close();
                        } catch (Exception e) {
                            logOnScreen("Ooops... " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    serverSocket.close();
                } else {
                    logOnScreen("Couldn't detect internet connection.");
                }
            } catch (Exception e) {
                logOnScreen("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) { return inetAddress.getHostAddress().toString(); }
                }
            }
        } catch (SocketException ex) {
            Log.e("ServerActivity", ex.toString());
        }
        return null;
    }

    public void shutdown() {
        if (thread != null) {
            thread.stopRunning();
        };
    }

    public void start() {
        this.thread = new ServerThread();
        this.thread.start();
    }
}
