package org.tudev.bigred.groupsound;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class ConnectionManager{

    private Socket server;
    private ServerSocket serverSocket;
    private static String TAG = "ConnectionManager";
    private ArrayList<Socket> clients;
    private long startTime, endTime;
    private boolean isFirst = false;
    private byte[] data;
    //We are the client
    public ConnectionManager(final InetAddress host){
        data = new byte[64];
        data = getByteArray();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server = new Socket(host, 8988);
                    server.setReuseAddress(true);
                } catch (java.io.IOException e) {
                    Log.d(TAG, e.toString());
                }
            }
        }).start();

    }

    //We are the host
    public ConnectionManager(){
        data = new byte[64];
        data = getByteArray();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(8988);
                    serverSocket.setReuseAddress(true);
                }catch(java.io.IOException e){
                    Log.d(TAG, e.toString());
                }
            }
        }).start();

    }

    public void setStartTime(long startTime){
        this.startTime = startTime;
    }

    public void setEndTime(long endTime){
        this.endTime = endTime;
    }

    public void setIsFirst(boolean isFirst){
        this.isFirst = isFirst;
    }

    public void serverConnect(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(clients==null){
                        clients = new ArrayList<>();
                    }
                    Socket socket = serverSocket.accept();
                    clients.add(socket);
                    if(clients.size()>0) {
                        serverListen(clients.get(clients.size() - 1));
                    }
                }catch(Exception e){
                    Log.d(TAG, e.toString());
                }
            }
        }).start();
    }
    //The clients will listen for traffic
    public void clientListen(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                int i=0;
                while (i<25){
                    try {
                        InputStream stream = server.getInputStream();
                        int count = stream.read(data);
                        if (count == 64) {
                            if (isFirst) {
                                Log.d(TAG, " " + (System.currentTimeMillis() - startTime));
                                i++;
                                setStartTime(System.currentTimeMillis());
                                clientSendPing();
                            } else {
                                clientSendPing();
                            }
                        }
                    } catch (java.io.IOException e) {
                        Log.d(TAG, e.toString());
                    }
                }
            }
        }).start();
    }
    //Server will listen for traffic
    public void serverListen(final Socket socket){
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean isListening = true;
                while(isListening) {
                    try {
                        InputStream stream = socket.getInputStream();
                        int count = stream.read(data);
                        if(count==64){
                            serverSendPing(clients.get(0));
                            /*
                            This is for 3 total phones
                            if ((socket == clients.get(0)) && (clients.size() > 1)) {
                                serverSendPing(clients.get(1));
                            } else if ((socket == clients.get(1)) && (clients.size() > 1)) {
                                serverSendPing(clients.get(0));
                            }
                            */
                        }
                    } catch (java.io.IOException e) {
                        Log.d(TAG, e.toString() + "serverlisten");
                    }
                }
            }
        }).start();
    }

    public void closeClientConnection(){
        try{
            if(server!=null){
                server.close();
            }
        }catch(java.io.IOException e){
            Log.d(TAG, e.toString());
        }
    }

    public void closeServerConnection(){
        try {
            if(serverSocket!=null){
                serverSocket.close();
            }
        }catch(java.io.IOException e){
            Log.d(TAG, e.toString());
        }
    }

    public void clientSendPing(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DataOutputStream dataOutputStream = new DataOutputStream(server.getOutputStream());
                    dataOutputStream.write(data,0,64);
                }catch(java.io.IOException e){
                    Log.d(TAG, e.toString());
                }
            }
        }).start();
    }

    private void serverSendPing(final Socket otherClient){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    DataOutputStream dataOutputStream = new DataOutputStream(otherClient.getOutputStream());
                    dataOutputStream.write(data,0,64);
                }catch(java.io.IOException e){
                    Log.d(TAG, e.toString() + "serversendping");
                }
            }
        }).start();
    }

    private byte[] getByteArray(){
        byte[] tmp = new byte[64];

        for(int i=0;i<64;i++){
            tmp[i]=0xF;
        }
        return tmp;
    }
}
