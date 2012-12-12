package com.android.locproof.stamp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import com.android.locproof.stamp.BluetoothEntities.BtEventListener;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

public class BluetoothService {
	/* Debugging */
	private static final String TAG = "BluetoothDelivery";
	private static final boolean D = true;
	
	/* Name for the SDP record when creating server socket */
    private static final String NAME_SECURE = "StampSecure";
    private static final String NAME_INSECURE = "StampInsecure";

    /* Unique UUID for this application */
    private static final UUID STAMP_UUID_SECURE =
        UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID STAMP_UUID_INSECURE =
        UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    
    /* Current Bluetooth connection state */
    private static final int BT_S_NONE 			= 0;	// we're doing nothing
    private static final int BT_S_INQUIRY		= 1;	// we are discovering
    private static final int BT_S_LISTEN 		= 2;	// now listening for incoming connections
    private static final int BT_S_CONNECTING 	= 3;	// now initiating an outgoing connection
    private static final int BT_S_CONNECTED 	= 4;	// now connected to a remote device

    private final BluetoothAdapter mBtAdapter;
    private final BtEventListener mBtListener;
    private ArrayList<BluetoothDevice> mBtNeighbors;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mBtState;
    private boolean server;
    private Context mContext; 
    
    /**
     * Constructor 
     * @param aContext context of main activity
     * @param aBtListener Bluetooth event listener, passed by individual prover and witness
     */
    public BluetoothService(Context aContext, BtEventListener aBtListener){
    	this.mContext = aContext;
    	this.mBtListener = aBtListener;
    	this.mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    	this.mBtNeighbors = new ArrayList<BluetoothDevice>(); 
    	
    	/* Register for broadcasts when a device is discovered */
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mContext.registerReceiver(mReceiver, filter);

        /* Register for broadcasts when discovery has finished */
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        mContext.registerReceiver(mReceiver, filter);
        
    	this.mBtState = BT_S_NONE;
    	this.server = false;
    }
    
    /**
     * Start an inquiry
     */
    public void inquiry(){
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }
        mBtNeighbors.clear();
        mBtAdapter.startDiscovery();
        setBtState(BT_S_INQUIRY);
    }
    
    /**
     * Fetch neighbor list after an inquiry 
     * @return neighbor list
     */
    public ArrayList<BluetoothDevice> getNeighbors(){
    	ArrayList<BluetoothDevice> clone = new ArrayList<BluetoothDevice>(mBtNeighbors);
    	return clone;
    }
    
    /**
     * Start AcceptThread to begin a session in listening (server) mode. 
     */
    public synchronized void listen() {
        if (D) Log.d(TAG, "start listen");

        /* Cancel any thread attempting to make a connection */
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        /* Cancel any thread currently running a connection */
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        setBtState(BT_S_LISTEN);
        server = false;

        /* Start the thread to listen on a BluetoothServerSocket */
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }
//        if (mInsecureAcceptThread == null) {
//            mInsecureAcceptThread = new AcceptThread(false);
//            mInsecureAcceptThread.start();
//        }
    }
    
    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        if (D) Log.d(TAG, "connecting to: " + device);

        /* Cancel any thread attempting to make a connection */
        if (mBtState == BT_S_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        /* Cancel any thread currently running a connection */
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        /* Start the thread to connect with the given device */
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        setBtState(BT_S_CONNECTING);
    }
    
    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        if (D) Log.d(TAG, "connected, Socket Type:" + socketType);

        /* Cancel the thread that completed the connection */
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        /* Cancel any thread currently running a connection */
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        /* Cancel the accept thread because we only want to connect to one device */
        if (mSecureAcceptThread != null) {
        	mSecureAcceptThread.cancel();
        	mSecureAcceptThread = null;
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        /* Start the thread to manage the connection and perform transmissions */
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        setBtState(BT_S_CONNECTED);
    }
    
    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }
        try{
        	mContext.unregisterReceiver(mReceiver);
        }catch(IllegalArgumentException e){
        	e.printStackTrace();
        }
        
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        setBtState(BT_S_NONE);
    }
    
    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out, byte type) {
        /* Create temporary object */
        ConnectedThread r;
        /* Synchronize a copy of the ConnectedThread */
        synchronized (this) {
            if (mBtState != BT_S_CONNECTED) return;
            r = mConnectedThread;
        }
        /* Perform the write unsynchronized */
        r.write(out, type);
    }
    
    /* The BroadcastReceiver that listens for discovered devices */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mBtNeighbors.add(device);
            // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
            	/* TODO: inquiry only start from BT_S_NONE? */
            	setBtState(BT_S_NONE);
            	/* invoke callback to notify caller that neighbor list is ready */
            	mBtListener.onBtEvent(BluetoothEntities.BTEVENT_INQUIRY_FINISHED, null);
            }
        }
    };
    
    /**
     * Set current state of Bluetooth connection
     * @param newBtState new Bluetooth connection state
     */
    private synchronized void setBtState(int newBtState){
    	if (D) Log.d(TAG, "setState() " + mBtState + " -> " + newBtState);
    	mBtState = newBtState;
    }
    
    /**
     * Get current state of Bluetooth connection
     * @return current Bluetooth connection state
     */
    private synchronized int getBtState(){
    	return mBtState;
    }
    
    /**
     * Indicate that the connection attempt failed and notify the caller.
     */
    private void connectionFailed(BluetoothDevice aBtDevice, String aSocketType) {
    	/* Generating callback first */
    	Bundle bundle = new Bundle();
		String deviceDesc = aBtDevice.getName()+";"+aBtDevice.getAddress()+";"+aSocketType;
		bundle.putString(BluetoothEntities.REMOTE_DEVICE, deviceDesc);
		mBtListener.onBtEvent(BluetoothEntities.BTEVENT_CONNECTION_FAILED, bundle);
		
    	/* Cancel any thread attempting to make a connection */
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        /* Cancel any thread currently running a connection */
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        setBtState(BT_S_NONE);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost(BluetoothDevice aBtDevice, String aSocketType) {
    	/* Generating callback first */
    	Bundle bundle = new Bundle();
		String deviceDesc = aBtDevice.getName()+";"+aBtDevice.getAddress()+";"+aSocketType;
		bundle.putString(BluetoothEntities.REMOTE_DEVICE, deviceDesc);
		mBtListener.onBtEvent(BluetoothEntities.BTEVENT_CONNECTION_LOST, bundle);
		
        if(server){
        	/* server side? restart listening */
        	listen();
        }else{
        	/* client side, fall back to none */
        	/* Cancel any thread attempting to make a connection */
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
            /* Cancel any thread currently running a connection */
            if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
            setBtState(BT_S_NONE);
        }
    }
    
    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        /* The local server socket */
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure":"Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mBtAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                    		STAMP_UUID_SECURE);
                } else {
                    tmp = mBtAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            NAME_INSECURE, STAMP_UUID_INSECURE);
                }
            } catch (IOException e) {
            	Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
        	if (D) Log.d(TAG, "Socket Type: " + mSocketType + "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;	// connection socket

            /* Listen to the server socket if we're not connected 
             * Cancel will be run if connected */
            while (mBtState != BT_S_CONNECTED) {
                try {
                    /* This is a blocking call and will only return on a
                     * successful connection or an exception */
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                	Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                	/* restart the listening */
                	listen();
                    break;
                }

                /* If a connection was accepted */
                if (socket != null) {
                	synchronized (BluetoothService.this) {
                        switch (mBtState) {
                        case BT_S_LISTEN:
                        case BT_S_CONNECTING:
                            /* Situation normal. Start the connected thread. */
                        	server = true;
                            connected(socket, socket.getRemoteDevice(), mSocketType);
                            break;
                        case BT_S_NONE:
                        case BT_S_CONNECTED:
                            /* Either not ready or already connected. Terminate new socket. */
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Could not close unwanted socket", e);
                            }
                            break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);
        }

        public void cancel() {
        	if (D) Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
            	Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }
    
    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread{
    	private final BluetoothSocket mmSocket;
    	private final BluetoothDevice mmDevice;
    	private String mSocketType;
    	
    	public ConnectThread(BluetoothDevice device, boolean secure){
    		mmDevice = device;
    		BluetoothSocket tmp = null;
    		mSocketType = secure ? "Secure":"Insecure";
    		
    		/* Get a BluetoothSocket for a connection with the given BluetoothDevice */
    		try{
    			if(secure){
    				tmp = device.createRfcommSocketToServiceRecord(STAMP_UUID_SECURE);
    			}else{
    				tmp = device.createInsecureRfcommSocketToServiceRecord(STAMP_UUID_INSECURE);
    			}
    		}catch(IOException e){
    			Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
    		}
    		mmSocket = tmp;
    	}
    	
    	public void run(){
    		Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
    		setName("ConnectThread"+mSocketType);
    		
    		/* Always cancel discovery because it will slow down a connection */
    		mBtAdapter.cancelDiscovery();
    		
    		/* Make a connection to the BluetoothSocket */
    		try{
    			/* This is a blocking call and will only return on a
    			 * successful connection or an exception
    			 */
    			mmSocket.connect();
    		}catch(IOException e){
    			Log.e(TAG, "mConnectThread failed", e);
    			/* close the socket */
    			try{
    				mmSocket.close();
    			}catch(IOException e2){
    				Log.e(TAG, "unable to close()" + mSocketType + 
    						" socket during connection failure", e2);
    			}
    			connectionFailed(mmDevice, mSocketType);
    			return;
    		}
    		
    		/* Reset the ConnectThread because we're done */
    		synchronized(BluetoothService.this){
    			Log.i(TAG, "END mConnectThread SocketType:" + mSocketType);
    			mConnectThread = null;
    		}
    		
    		/* Notify caller then start the connected thread */
    		Bundle bundle = new Bundle();
    		String deviceDesc = mmDevice.getName()+";"+mmDevice.getAddress()+";"+mSocketType;
    		bundle.putString(BluetoothEntities.REMOTE_DEVICE, deviceDesc);
    		mBtListener.onBtEvent(BluetoothEntities.BTEVENT_CONNECTING, bundle);
    		
            connected(mmSocket, mmDevice, mSocketType);
    	}
    	
    	public void cancel(){
    		try{
    			mmSocket.close();
    		}catch(IOException e){
    			e.printStackTrace();
    		}
    	}
    }
    
    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
    	private final BluetoothDevice mmDevice;
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final String mmSocketType;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            /* Get the BluetoothSocket input and output streams */
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mmDevice = mmSocket.getRemoteDevice();
            mmSocketType = socketType;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[40960];
            int bytes;
            
            /* Connection is active, notify caller */
            Bundle bundle = new Bundle();
    		String deviceDesc = mmDevice.getName()+";"+mmDevice.getAddress()+";"+mmSocketType;
    		bundle.putString(BluetoothEntities.REMOTE_DEVICE, deviceDesc);
    		mBtListener.onBtEvent(BluetoothEntities.BTEVENT_CONNECTED, bundle);
    		
            /* Keep listening to the InputStream while connected*/
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    byte data[] = new byte[bytes];
                    System.arraycopy(buffer, 0, data, 0, bytes);
                	
                    /* send received messages to caller */
                    Bundle newMessage = new Bundle();
                    newMessage.putString(BluetoothEntities.REMOTE_DEVICE, 
                    						mmDevice.getName()+";"+mmDevice.getAddress());
                    newMessage.putByteArray(BluetoothEntities.RCVD_MESSAGE, data);
            		mBtListener.onBtEvent(BluetoothEntities.BTEVENT_MSG_RCVD, newMessage);

                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost(mmDevice, mmSocketType);
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer, byte type) {
            try {
                mmOutStream.write(buffer);
                mmOutStream.flush();
                
                /* Notify caller the send is completed */
                Bundle sent = new Bundle();
                sent.putString(BluetoothEntities.REMOTE_DEVICE, 
                						mmDevice.getName()+";"+mmDevice.getAddress());
                sent.putByte(BluetoothEntities.SENT_MESSAGE, type);
        		mBtListener.onBtEvent(BluetoothEntities.BTEVENT_MSG_SENT, sent);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
