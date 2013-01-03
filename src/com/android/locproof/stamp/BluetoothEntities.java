package com.android.locproof.stamp;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public abstract class BluetoothEntities {
	/* Key for Bluetooth event data bundle */
	public static final String REMOTE_DEVICE = "device";
	public static final String RCVD_MESSAGE = "rcvd_message";
	public static final String SENT_MESSAGE = "sent_message";
	
	/* Bluetooth event */
	public static final int BTEVENT_INQUIRY_FINISHED 	= 1;
	public static final int BTEVENT_CONNECTING 			= 2;
	public static final int BTEVENT_CONNECTION_FAILED	= 3;
	public static final int BTEVENT_CONNECTED 			= 4;
	public static final int BTEVENT_CONNECTION_LOST		= 5;
	public static final int BTEVENT_MSG_RCVD			= 6;
	public static final int BTEVENT_MSG_SENT			= 7;
	
	/* STAMP Messages
	 * 0---------2----3----5--------      
	 * |0x0A 0x0B|TYPE|SIZE|PAYLOAD| */
	public static final int MESSAGE_HEADER_LEN		= 7;
	public static final byte MESSAGE_STAMP_BYTE1	= 0x0A;
	public static final byte MESSAGE_STAMP_BYTE2	= 0x0B;
	public static final byte MESSAGE_PREQ			= 0x01;
	public static final byte MESSAGE_EP				= 0x02;
    public static final byte MESSAGE_DBSTART		= 0x03;
    public static final byte MESSAGE_CECK			= 0x04;
    
    /* State machine states */
    public static final int PROVER_S_INIT			= 0;	// initialized
    public static final int PROVER_S_INQUIRY 		= 1;	// in discovery
    public static final int PROVER_S_CONNECTING 	= 2;	// initiating an outgoing connection
    public static final int PROVER_S_CONNECTED 		= 3; 	// connected to a remote device
    public static final int PROVER_S_PREQ_SENT 		= 4;  	// sent out preq message 
    public static final int PROVER_S_DB_START 		= 5;  	// received signal to start DB
    public static final int PROVER_S_DB_SUCCESS 	= 6;	// received signal to finish DB
    public static final int PROVER_S_EP_RCVD 		= 7;  	// received ep message
    
    /* State machine states */
    public static final int WITNESS_S_INIT			= 0;	// initialized
    public static final int WITNESS_S_LISTENING		= 1;	// start Bluetooth listening
    public static final int WITNESS_S_CONNECTED		= 2;	// connected to a remote device
    public static final int WITNESS_S_PREQ_RCVD 	= 3;	// received preq message
    public static final int WITNESS_S_DB_START 		= 4;	// sent db start message
    public static final int WITNESS_S_DB_SUCCESS 	= 5; 	// sent db success message
    public static final int WITNESS_S_EP_SENT 		= 6;  	// sent ep message
    
    private Context mContext;
	private Handler mHandler;
	private MyLooper mLooper;
	private int mSMState;
	
    // For data collection
    protected int comOverhead = 0;
    protected long requestTime;
    protected long finishTime;
    protected long dbStartTime;
    protected long dbFinishTime;
    
    protected long ceckPrepStartTime;
    protected long ceckPrepFinishTime;
    
    /**
     * Constructor
     * @param aContext context of main activity
     * @param aHandler UI handler from main activity
     */
	public BluetoothEntities(Context aContext, Handler aHandler){
		this.mContext = aContext;
		this.mHandler = aHandler;
		this.mLooper = new MyLooper();
		this.mLooper.start();
		while(!this.mLooper.isReady()){
		}
	}
	
	/**
	 * Start state machine
	 */
	public abstract void startSM();
	
	/**
	 * State transition
	 * @param newSMState
	 * @param param
	 */
	public abstract void pushSM(int newSMState, Bundle param);
	
	/**
	 * Stop state machine
	 */
	public abstract void stopSM();
	
	/**
	 * Set current state machine state to newSMState
	 * @param newSMState new state machine state
	 */
	public synchronized void setSMState(int newSMState){
		mSMState = newSMState;
	}
	
	/**
	 * Get current state machine state
	 * @return current state machine state
	 */
	public synchronized int getSMState(){
		return mSMState;
	}
	
	/**
	 * Schedule a transition task
	 * @param trans transition task
	 */
	public void addSMTask(Runnable smTask){
		this.mLooper.mLoopHandler.post(smTask);
	}
    
    /**
     * Send state change notice to UI
     * @param oldSMState
     * @param newSMState
     * @param valid
     */
    public void printTransition(int oldSMState, int newSMState, boolean valid, boolean prover){
    	String oldSMStateString = stateToString(oldSMState, prover);
    	String newSMStateString = stateToString(newSMState, prover);
    	String msgString;
    	if(valid){
    		msgString = "Transition "+oldSMStateString+" -> "+newSMStateString;
    	}else{
    		msgString = "INVALID "+oldSMStateString+" -> "+newSMStateString;
    	}
    	/* Send log message back to the UI Activity */
        Message msg = mHandler.obtainMessage(StampProtocolActivity.UI_M_SMTRANSITION);
        Bundle bundle = new Bundle();
        bundle.putString(StampProtocolActivity.SM_TRANSITION, msgString);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
    
    /**
     * Send data log to UI
     * @param epSize
     * @param prover
     */
    public void printDataLog(int epSize, boolean prover){
    	String msgString;
    	if(prover){
    		msgString = "Total Delay: " + (finishTime - requestTime) + " | CeCk Prep Time: " + (ceckPrepFinishTime - ceckPrepStartTime) + " |EP Size: " + epSize + " | Total Communication: " + comOverhead;
    	}else{
    		msgString = "DB Delay: " + (dbFinishTime - dbStartTime) + " | Total Communication: " + comOverhead;
    	}

    	/* Send log message back to the UI Activity */
        Message msg = mHandler.obtainMessage(StampProtocolActivity.UI_M_DATALOG);
        Bundle bundle = new Bundle();
        bundle.putString(StampProtocolActivity.DATA_LOG, msgString);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
    
    /**
     * Send Bluetooth connection status to UI
     * @param remote remote address
     * @param status connection status 
     */
    public void printRemoteStatus(String remote, int status){
    	String remoteStatus = null;
    	switch(status){
	    	case BTEVENT_CONNECTING:
	    		remoteStatus = "Connecting to: "+remote;
	    		break; 
	    	case BTEVENT_CONNECTION_FAILED:
	    		remoteStatus = "Connection attempt to: "+remote+" failed";
	    		break; 
	    	case BTEVENT_CONNECTED:
	    		remoteStatus = "Connected to: "+remote;
	    		break; 
	    	case BTEVENT_CONNECTION_LOST:
	    		remoteStatus = "Connection to: "+remote+" lost";
	    		break; 
	    	default:
	    		remoteStatus = "Unknown connection status";
	    		break;
    	}
    	/* Send log message back to the UI Activity */
        Message msg = mHandler.obtainMessage(StampProtocolActivity.UI_M_CONNECTION);
        Bundle bundle = new Bundle();
        bundle.putString(StampProtocolActivity.CONNECTION, remoteStatus);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
    
    /**
     * Send received message notification to UI
     * @param remote remote address
     * @param message message type 
     */
    public void printRemoteMessage(String remote, int message){
    	String remoteMessage = null;
    	switch(message){
    		case MESSAGE_PREQ:
    			remoteMessage = "Received message PREQ from "+remote;
    			break;
	    	case MESSAGE_DBSTART:
	    		remoteMessage = "Received message DBSTART from "+remote;
	    		break; 
	    	case MESSAGE_CECK:
	    		remoteMessage = "Received message DBSUCCESS from "+remote;
	    		break; 
	    	case MESSAGE_EP:
	    		remoteMessage = "Received message EP from "+remote;
	    		break;
	    	default:
	    		remoteMessage = "Received message unknown from "+remote;
	    		break;
		}
		/* Send log message back to the UI Activity */
	    Message msg = mHandler.obtainMessage(StampProtocolActivity.UI_M_RCVDMSG);
	    Bundle bundle = new Bundle();
	    bundle.putString(StampProtocolActivity.MESSAGE, remoteMessage);
	    msg.setData(bundle);
	    mHandler.sendMessage(msg);
    }
    
    /**
     * Helper function that convert a sm state code to string
     * @param SMState sm state 
     * @param prover true on prover state; false on witness state
     * @return string representation of state code
     */
    private String stateToString(int SMState, boolean prover){
    	if(prover){
	    	switch(SMState){
		    	case PROVER_S_INIT: 
		    		return "P_INIT";
		    	case PROVER_S_INQUIRY:
		    		return "P_INQUIRY";
		    	case PROVER_S_CONNECTING: 
		    		return "P_CONNECTING";
		    	case PROVER_S_CONNECTED: 
		    		return "P_CONNECTED";
		    	case PROVER_S_PREQ_SENT: 
		    		return "P_PREQ_SENT";
		    	case PROVER_S_DB_START: 
		    		return "P_DB_START";
		    	case PROVER_S_DB_SUCCESS: 
		    		return "P_DB_SUCCESS";
		    	case PROVER_S_EP_RCVD: 
		    		return "P_EP_RCVD";
				default:
					return "P_UNKNOWN";
			}
    	}else{
    		switch(SMState){
		    	case WITNESS_S_INIT: 
		    		return "W_INIT";
		    	case WITNESS_S_LISTENING:
		    		return "W_LISTENING";
		    	case WITNESS_S_CONNECTED: 
		    		return "W_CONNECTED";
		    	case WITNESS_S_PREQ_RCVD: 
		    		return "W_PREQ_RCVD";
		    	case WITNESS_S_DB_START: 
		    		return "W_DB_START";
		    	case WITNESS_S_DB_SUCCESS: 
		    		return "W_DB_SUCCESS";
		    	case WITNESS_S_EP_SENT: 
		    		return "W_EP_SENT";
				default:
					return "W_UNKNOWN";
	    	}
    	}
    }
    
	/**
	 * SM transition task
	 */
	public class moveSM implements Runnable{
		private final int newSMState;
		private Bundle param;
		public moveSM(final int aNewSMState, Bundle aParam){
			this.newSMState = aNewSMState;
			this.param = aParam;
		}
		public void run(){
			pushSM(newSMState, param);
		}
	}
	
	/**
	 * Pipeline thread queue for state machine transitions
	 */
	private class MyLooper extends Thread{
		private Handler mLoopHandler;
		
		public void run(){
			Looper.prepare();
			this.mLoopHandler = new Handler();
			Looper.loop();
		}
		
		public boolean isReady(){
			return this.mLoopHandler != null;
		}
	}
	
	/**
	 * Bluetooth event listener
	 */
	public interface BtEventListener{
		void onBtEvent(int what, Bundle data);
	}
}
