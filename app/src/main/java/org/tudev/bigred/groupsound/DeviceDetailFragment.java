package org.tudev.bigred.groupsound;


import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.renderscript.ScriptGroup;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.tudev.bigred.groupsound.DeviceListFragment.DeviceActionListener;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener, GroupInfoListener {

    public static final String TAG = "DeviceDetailFragment";
    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pGroup group;
    private WifiP2pInfo info;
    protected static ArrayList<Socket> clientsList;
    private static Socket hostSocket;
    private ServerSocket serverSocket;
    ProgressDialog progressDialog = null;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mContentView = inflater.inflate(R.layout.device_detail, null);
        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true
//                        new DialogInterface.OnCancelListener() {
//
//                            @Override
//                            public void onCancel(DialogInterface dialog) {
//                                ((DeviceActionListener) getActivity()).cancelDisconnect();
//                            }
//                        }
                );
                ((DeviceActionListener) getActivity()).connect(config);

            }
        });

        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((DeviceActionListener) getActivity()).disconnect();
                    }
                });

        mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        //THIS DOESNT WORK ON THE NEXUS FOR SOME REASON
                        intent.setType("audio/x-mpeg-3");
                        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                    }
                });

        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        // User has picked an image. Transfer it to group owner i.e peer using
        // FileTransferService.
        final Uri uri = data.getData();
        Log.d(TAG, uri.getPath());
        new Thread(new Runnable() {
            @Override
            public void run() {
                for(int i=0;i<clientsList.size();i++) {
                    try{
                        copyFile(new FileInputStream(uri.getPath()), clientsList.get(i).getOutputStream());
                    } catch(Exception e){
                        Log.d(TAG, e.toString());
                    }
                }
            }
        }).start();

    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info)
    {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);

        // The owner IP is now known.
        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(getResources().getString(R.string.group_owner_text) + ((info.isGroupOwner) ? getResources().getString(R.string.yes)
                : getResources().getString(R.string.no)));

        // InetAddress from WifiP2pInfo struct.
        view = (TextView) mContentView.findViewById(R.id.device_info);

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info.groupFormed && info.isGroupOwner) {
            mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG, "Entered thread owner. My IP: " + info.groupOwnerAddress.getHostAddress());
                        if(serverSocket==null) {
                            serverSocket = new ServerSocket(8988);
                            serverSocket.setReuseAddress(true);
                        }
                        if(clientsList == null){
                            clientsList = new ArrayList<>();
                        }
                        clientsList.add(serverSocket.accept());
                    } catch(Exception e){
                        Log.d(TAG, e.toString());
                    }
                }
            }).start();
        } else if (info.groupFormed) {

            ((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
                    .getString(R.string.client_text));
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Entered thread not owner");
                    hostSocket = new Socket();
                    try {
                        hostSocket.setReuseAddress(true);
                    }catch(java.net.SocketException e){
                        Log.d(TAG, e.toString());
                    }
                    try {
                        Log.d(TAG, "Entered try no2");
                        hostSocket.connect((new InetSocketAddress(info.groupOwnerAddress, 8988)), 5000);
                        Log.d(TAG, info.groupOwnerAddress.toString());
                        receiveFile(hostSocket);
                    } catch(java.io.IOException e){
                        Log.d(TAG, e.toString());
                    }
                }
            }).start();
            mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
        }

        // hide the connect button
    }

    public void onGroupInfoAvailable(final WifiP2pGroup group)
    {
        this.group = group;
        if(group.isGroupOwner()) {
            this.getView().setVisibility(View.VISIBLE);

            //TextView view = (TextView) mContentView.findViewById(R.id.group_password);
            //if(ipaddress!=null) {
            //  view.setText(getResources().getString(R.string.password) + ipaddress);
            //}
        }
    }

    /**
     * Updates the UI with device data
     *
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(device.deviceAddress);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(device.toString());

    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }

    public void receiveFile(Socket hostSocket){
        InputStream inputStream;
        final File f = new File(Environment.getExternalStorageDirectory() + "/wifip2pshared-" + System.currentTimeMillis()
                + ".mp3");
        try {
            File dirs = new File(f.getParent());
            if (!dirs.exists())
                dirs.mkdirs();
            f.createNewFile();

            inputStream = hostSocket.getInputStream();
            copyFile(inputStream, new FileOutputStream(f));
            timeExecute(this.getContext(), f.getAbsolutePath());
        }catch(Exception e){
            Log.d(TAG, e.toString());
        }
    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            Log.d(TAG, "File Sent");
        } catch (IOException e) {
            Log.d(TAG, e.toString());
            return false;
        }
        return true;
    }

    public static void timeExecute(Context context, String result){
        Intent intent = new Intent();
        intent.setAction(android.content.Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("file://" + result), "audio/x-mpeg-3");
        context.startActivity(intent);
    }

}
