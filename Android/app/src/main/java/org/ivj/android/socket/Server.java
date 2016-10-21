package org.ivj.android.socket;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.ivj.android.sound.CommandWriter;
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
    private final CommandWriter commandWriter;

    private Handler handler;

    private ServerSocket serverSocket;

    private ServerThread thread;

    public Server(Handler handler, CommandWriter commandWriter) {
        SERVERIP = getLocalIpAddress();
        this.handler = handler;
        this.commandWriter = commandWriter;
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
                                String response = process(line);
                                if (response != null) {
                                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(client
                                            .getOutputStream())), true);
                                    out.println(response);
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

    private String process(String command) {
        Log.d("ServerActivity", command);
        if (command.startsWith("command.")) {
            final String message = "Received: " + command;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    logOnScreen(message);
                }
            });
            if (command.endsWith(".ping")) {
                return "PONG! Status of writer: " + commandWriter.isAckReceived();
            } else if (command.contains(".arduino.")) {
                if (!commandWriter.isAckReceived()) {
                    return "Arduino hasn't acknowledged our existence yet";
                }
                if (command.endsWith(".run")) {
                    commandWriter.postCommand(20);
                } else if (command.endsWith(".stop")) {
                    commandWriter.postCommand(20, 100);
                } else if (command.endsWith(".right")) {
                    commandWriter.postCommand(30, 45);
                } else if (command.endsWith(".left")) {
                    commandWriter.postCommand(30, 90);
                } else if (command.endsWith(".led.on")) {
                    commandWriter.postCommand(40);
                } else if (command.endsWith(".led.off")) {
                    commandWriter.postCommand(40, 100);
                }
            }
        }
        return null;
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
