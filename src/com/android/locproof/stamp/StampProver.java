package com.android.locproof.stamp;

import java.io.File;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.android.locproof.stamp.BluetoothEntities.moveSM;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class StampProver extends BluetoothEntities{
	/* Debugging */
    private static final String TAG = "StampProver";
    private static final boolean D = true;
    
    private static final String ID = "StampProver";
    private ProverBtListener mProverBtListener;
    private BluetoothService mProverBtService;
    private ArrayList<BluetoothDevice> mWtnsCandidates;
    
    private ProverContext mStampContext;
    private StampEPRecord mStampEPRecord;
    private ArrayList<StampEPRecord> mEPDatabase;		// TODO: move to sqlite db
    
    private Context mContext;
    private Handler mHandler;
    
    /**
     * Constructor
     * @param aContext context of main activity
     * @param aHandler UI handler from main activity
     */
    public StampProver(Context aContext, Handler aHandler){
    	super(aContext, aHandler);
    	this.mContext = aContext;
    	this.mHandler = aHandler;
    	
		/* Create Bluetooth service agent */
    	mProverBtListener = new ProverBtListener();
    	mProverBtService = new BluetoothService(aContext, mProverBtListener);
        
    	mWtnsCandidates = new ArrayList<BluetoothDevice>();
        mEPDatabase = new ArrayList<StampEPRecord>();
        
        setSMState(PROVER_S_INIT);
    }
    
    /**
     * External interface to start prover SM
     */
    public void startSM(){
    	if(getSMState() == PROVER_S_INIT){
    		mStampContext = new ProverContext();
    		mStampEPRecord = new StampEPRecord(mStampContext, mContext);
    		addSMTask(new moveSM(PROVER_S_INQUIRY,null));
    	}
    }
    
    /**
     * Stop prover SM
     */
    public synchronized void stopSM(){
    	mProverBtService.stop();
    	mWtnsCandidates.clear();
    	mEPDatabase.add(mStampEPRecord);
    	printEPRecords();
    	setSMState(PROVER_S_INIT);
    }
    
    /**
     * Prover state machine
     */
    public synchronized void pushSM(int newSMState, Bundle param){
    	int oldSMState = getSMState();
    	setSMState(newSMState);
    	switch(newSMState){
	    	case PROVER_S_INIT: 
	    		switch(oldSMState){
	    			case PROVER_S_INQUIRY:
	    				stopSM();
	    				printTransition(oldSMState, newSMState, true, true);
	    				break;
	    			default:
	    				printTransition(oldSMState, newSMState, false, true);
	    				break;	
	    		}
	    		break;
	    	case PROVER_S_INQUIRY:
	    		switch(oldSMState){
	    			case PROVER_S_INIT:
	    				mProverBtService.inquiry();
	    				printTransition(oldSMState, newSMState, true, true);
	    				break;
	    			case PROVER_S_CONNECTING:
	    			case PROVER_S_CONNECTED:
	    			case PROVER_S_PREQ_SENT:
	    			case PROVER_S_DB_START:
	    			case PROVER_S_DB_SUCCESS:
	    			case PROVER_S_EP_RCVD:
	    				checkWtnsCandidate(mWtnsCandidates);
	    				printTransition(oldSMState, newSMState, true, true);
	    				break;
	    			default:
	    				printTransition(oldSMState, newSMState, false, true);
	    				break;	
	    		}
	    		break;
	    	case PROVER_S_CONNECTING: 
	    		switch(oldSMState){
	    			case PROVER_S_INQUIRY:
	    				if(mWtnsCandidates.size()>0){
	    					mProverBtService.
	    						connect(mWtnsCandidates.get(mWtnsCandidates.size()-1),true);
	    					mWtnsCandidates.remove(mWtnsCandidates.size()-1);
	    				}
	    				printTransition(oldSMState, newSMState, true, true);
	    				break;
	    			default:
	    				printTransition(oldSMState, newSMState, false, true);
	    				break;	
	    		}
	    		break;
	    	case PROVER_S_CONNECTED: 
	    		switch(oldSMState){
		    		case PROVER_S_CONNECTING:
		    			sendMessage(MESSAGE_PREQ);
		    			printTransition(oldSMState, newSMState, true, true);
		    			break;
	    			default:
	    				printTransition(oldSMState, newSMState, false, true);
	    				break;
	    		}
	    		break;
	    	case PROVER_S_PREQ_SENT: 
	    		switch(oldSMState){
		    		case PROVER_S_CONNECTED:
		    			/* wait for response */
		    			printTransition(oldSMState, newSMState, true, true);
		    			break;
		    		default:
		    			printTransition(oldSMState, newSMState, false, true);
		    			break;
	    		}
	    		break;
	    	case PROVER_S_DB_START: 
	    		switch(oldSMState){
		    		case PROVER_S_PREQ_SENT:
		    			/* prepare and start distance bounding */
		    			StampMessage.processDBStart(mStampContext, param.getByteArray(RCVD_MESSAGE));
		    			/* success? */
		    			sendMessage(MESSAGE_CECK);
		    			printTransition(oldSMState, newSMState, true, true);
		    			break;
		    		default:
		    			printTransition(oldSMState, newSMState, false, true);
		    			break;
	    		}
	    		break;
	    	case PROVER_S_DB_SUCCESS: 
	    		switch(oldSMState){
		    		case PROVER_S_DB_START:
		    			/* clean up distance bounding */
		    			/* wait for EP */
		    			printTransition(oldSMState, newSMState, true, true);
		    			break;
		    		default:
		    			printTransition(oldSMState, newSMState, false, true);
		    			break;
	    		}
	    		break;
	    	case PROVER_S_EP_RCVD: 
	    		switch(oldSMState){
		    		case PROVER_S_DB_SUCCESS:
		    			/* process EP */
		    			StampMessage.processEP(mStampEPRecord, param.getByteArray(RCVD_MESSAGE));
		    			/* successful? go back and check next witness */
		    	    	addSMTask(new moveSM(PROVER_S_INQUIRY,null));
		    			printTransition(oldSMState, newSMState, true, true);
		    			break;
		    		default:
		    			printTransition(oldSMState, newSMState, false, true);
		    			break;
	    		}
	    		break;
    		default: 
    			printTransition(oldSMState, newSMState, false, true);
    			break;
    	}
    }

    /**
     * Send witness list to UI
     */
    public void printWtnsCandidate(){
    	String witnessList = "";
    	if(mWtnsCandidates.size()>0){
    		for (int i = 0; i < mWtnsCandidates.size(); i++){
    			witnessList = witnessList+mWtnsCandidates.get(i).getAddress()+";";
    		}
    	}else{
    		witnessList = "No witness discovered";
    	}
    	/* Send log message back to the UI Activity */
        Message msg = mHandler.obtainMessage(StampProtocolActivity.UI_M_WITNESSLIST);
        Bundle bundle = new Bundle();
        bundle.putString(StampProtocolActivity.NEW_WITNESS, witnessList);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
    
    /**
     * Print entire EP record database
     * Debug only
     */
    private void printEPRecords(){
    	for(int i = 0; i< mEPDatabase.size(); i++){
    		mEPDatabase.get(i).printEPRecord();
    	}
    }
    
	/**
	 * Handler for sending messages
	 * @param messageType type of message
	 */
    private void sendMessage(byte messageType){
    	byte mWriteBuf[];
    	byte header[] = {0x0A, 0x0B, messageType, 0x00, 0x00};
    	byte payload[] = null;
    	short size = 0;		// size cannot be that large can it? 
    	switch(messageType){
    		case MESSAGE_PREQ:
    			payload = StampMessage.createPreq(mStampContext);
    			size = (short) payload.length;
    			System.arraycopy(ByteBuffer.allocate(2).putShort(size).array(), 0, header, 3, 2);
    			mWriteBuf = new byte[header.length + size];
    			System.arraycopy(header, 0, mWriteBuf, 0, header.length);
    			System.arraycopy(payload, 0, mWriteBuf, header.length, size);
    			mProverBtService.write(mWriteBuf, MESSAGE_PREQ);
    			mStampEPRecord.setPreq(payload);
    			break;
    		case MESSAGE_CECK:
    			payload = StampMessage.createCeCk(mStampContext);
    			size = (short) payload.length;
    			System.arraycopy(ByteBuffer.allocate(2).putShort(size).array(), 0, header, 3, 2);
    			mWriteBuf = new byte[header.length + size];
    			System.arraycopy(header, 0, mWriteBuf, 0, header.length);
    			System.arraycopy(payload, 0, mWriteBuf, header.length, size);
    			mProverBtService.write(mWriteBuf, MESSAGE_CECK);
    			break;
    		default:
    			break;
    	}
    	if(D){
			Log.d(TAG, "Message "+messageType+": "+size+" bytes sent");
			Log.d(TAG, new String(payload));
		}
    }
    
    /**
     * Handler for receiving message
     * @param aMessage message bundle from btservice
     */
    private void readMessage(Bundle aMessage){
    	String remote[] = aMessage.getString(REMOTE_DEVICE).split(";");
    	byte message[] = aMessage.getByteArray(RCVD_MESSAGE);
    	int remainSize = message.length;
    	
    	int msgStart = 0;
    	byte header[];
    	byte payload[];
    	short size = 0;
    	
    	Bundle bundle; 
    	
    	while(remainSize > 0){
    		if(remainSize > MESSAGE_HEADER_LEN){
    			header = new byte[MESSAGE_HEADER_LEN]; 
    			System.arraycopy(message, msgStart, header, 0, MESSAGE_HEADER_LEN);
    			size = ByteBuffer.wrap(header, 3, 2).getShort();
    			payload = new byte[size];
    			System.arraycopy(message, msgStart+MESSAGE_HEADER_LEN, payload, 0, size);
    			if((header[0]==MESSAGE_STAMP_BYTE1)&&(header[1]==MESSAGE_STAMP_BYTE2)){
    				switch(header[2]){
	    				case MESSAGE_DBSTART:
		    				/* send notification */
		    				printRemoteMessage(remote[1], MESSAGE_DBSTART);
		    				/* wrap payload */
		    				bundle = new Bundle();
		    				bundle.putByteArray(RCVD_MESSAGE, payload);
		    				addSMTask(new moveSM(PROVER_S_DB_START,bundle));
		    				break;
		    			case MESSAGE_EP:
		    				printRemoteMessage(remote[1], MESSAGE_EP);
		    				/* wrap payload */
		    				bundle = new Bundle();
		    				bundle.putByteArray(RCVD_MESSAGE, payload);
		    				addSMTask(new moveSM(PROVER_S_EP_RCVD,bundle));
		    				break;
	    				default:
	    					break;
    				}
    			}
    			if(D){
    				Log.d(TAG, "Message "+header[2]+": "+size+" bytes received");
    				Log.d(TAG, new String(payload));
    			}
    		}
    		msgStart = msgStart + MESSAGE_HEADER_LEN + size;
    		remainSize -= msgStart;
    	}
    }
    
    /**
     * Filtering witness candidates when new discovered neighbors coming in
     * @param aWtnsCandidate witness candidate list
     */
    private void prepareWtnsCandidate(ArrayList<BluetoothDevice> aWtnsCandidates){
    	BluetoothDevice currDevice; 
    	ArrayList<BluetoothDevice> neighbors = mProverBtService.getNeighbors();
    	aWtnsCandidates.clear();
    	for(int i = 0; i < neighbors.size(); i++){
    		currDevice = neighbors.get(i);
    		/* check if neighbor is STAMP-compatible */
    		if(currDevice.getName().contains("STAMP")){
    			aWtnsCandidates.add(currDevice);
    		}
    	}
    }
    
    /**
     * Try to connect if there are remaining witnesses in the list, otherwise back to idle
     * @param aWtnsCandidate witness candidate list
     */
    private void checkWtnsCandidate(ArrayList<BluetoothDevice> aWtnsCandidates){
    	if(aWtnsCandidates.size()>0){
    		addSMTask(new moveSM(PROVER_S_CONNECTING,null));
    	}else{
    		addSMTask(new moveSM(PROVER_S_INIT,null));
    	}
    }
    
    /**
	 * Prover's handler for Bluetooth events
	 */
	public class ProverBtListener implements BtEventListener{
		private String remote[];
		public void onBtEvent(int what, Bundle data) {
			switch(what){
				case BTEVENT_INQUIRY_FINISHED: 
					prepareWtnsCandidate(mWtnsCandidates);
					printWtnsCandidate();
					checkWtnsCandidate(mWtnsCandidates);
					break;
				case BTEVENT_CONNECTING:
					remote = data.getString(REMOTE_DEVICE).split(";");
					printRemoteStatus(remote[1], BTEVENT_CONNECTING);
					break;
				case BTEVENT_CONNECTION_FAILED: 
					remote = data.getString(REMOTE_DEVICE).split(";");
					printRemoteStatus(remote[1], BTEVENT_CONNECTION_FAILED);
					/* sometimes connection signal comes in late */
					if(getSMState()!= PROVER_S_INIT){
						addSMTask(new moveSM(PROVER_S_INQUIRY,null));
					}
					break;
				case BTEVENT_CONNECTED:
					remote = data.getString(REMOTE_DEVICE).split(";");
					printRemoteStatus(remote[1], BTEVENT_CONNECTED);
					addSMTask(new moveSM(PROVER_S_CONNECTED,null));
					break;
				case BTEVENT_CONNECTION_LOST: 
					remote = data.getString(REMOTE_DEVICE).split(";");
					printRemoteStatus(remote[1], BTEVENT_CONNECTION_LOST);
					/* sometimes connection signal comes in late */
					if(getSMState()!= PROVER_S_INIT){
						addSMTask(new moveSM(PROVER_S_INQUIRY,null));
					}
					break;
				case BTEVENT_MSG_RCVD:
					readMessage(data);
					break;
				case BTEVENT_MSG_SENT:
					byte msgType = data.getByte(SENT_MESSAGE);
					switch(msgType){
						case MESSAGE_PREQ:
							/* move sm until we get the confirmation */
							addSMTask(new moveSM(PROVER_S_PREQ_SENT,null));
							break;
						case MESSAGE_CECK:
							/* move sm until we get the confirmation */
							addSMTask(new moveSM(PROVER_S_DB_SUCCESS,null));
							break;
						default:
							break;
					}
					break;
				default:
					break;
			}
		}
	}
}
