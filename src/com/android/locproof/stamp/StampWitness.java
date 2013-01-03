package com.android.locproof.stamp;

import java.nio.ByteBuffer;

import com.android.locproof.stamp.BluetoothEntities.moveSM;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class StampWitness extends BluetoothEntities{
	/* Debugging */
    private static final String TAG = "StampWitness";
    private static final boolean D = true;
    
    private WitnessBtListener mWitnessBtListener;
    private BluetoothService mWitnessBtService;
    
    private static final String ID = "StampWitness";
    private WitnessContext mStampContext;
    private byte[] assembleBuffer = null;
    private int bytesRead = 0;
    private int packetSize = 0;
    
    private Context mContext;
    private Handler mHandler;
    
    
    /**
     * Constructor
     * @param aContext context of main activity
     * @param aHandler UI handler from main activity
     */
    public StampWitness(Context aContext, Handler aHandler){
    	super(aContext, aHandler);
    	this.mContext = aContext;
    	this.mHandler = aHandler;

    	/* Create Bluetooth service agent */
    	mWitnessBtListener = new WitnessBtListener();
    	mWitnessBtService = new BluetoothService(aContext, mWitnessBtListener);

        setSMState(WITNESS_S_INIT);
    }
    
    /**
     * External interface to start witness SM
     */
    public void startSM(){
    	if(getSMState() == WITNESS_S_INIT){
    		addSMTask(new moveSM(WITNESS_S_LISTENING, null));
    	}
    }
    
    /**
     * Stop witness SM
     */
    public synchronized void stopSM(){
    	mWitnessBtService.stop();
    	setSMState(WITNESS_S_INIT);
    }
    
    /**
     * Witness state machine
     */
    public synchronized void pushSM(int newSMState, Bundle param){
    	int oldSMState = getSMState();
    	setSMState(newSMState);
    	switch(newSMState){
	    	case WITNESS_S_INIT: 
	    		switch(oldSMState){
	    			/* sm will never come back to INIT state until forced stop */
	    			default:
	    				printTransition(oldSMState, newSMState, false, false);
	    				break;	
	    		}
	    		break;
	    	case WITNESS_S_LISTENING:
	    		switch(oldSMState){
	    			case WITNESS_S_INIT:
	    			case WITNESS_S_EP_SENT:
	    				mWitnessBtService.listen();
	    				printTransition(oldSMState, newSMState, true, false);
	    				break;
	    			case WITNESS_S_LISTENING:
	    			case WITNESS_S_CONNECTED:
	    			case WITNESS_S_PREQ_RCVD:
	    			case WITNESS_S_DB_START:
	    			case WITNESS_S_DB_SUCCESS:
	    				/* caused by connection failed? 
	    				 * already restarted by Bluetooth service*/
	    				printTransition(oldSMState, newSMState, true, false);
	    				break;
	    			default:
	    				printTransition(oldSMState, newSMState, false, false);
	    				break;	
	    		}
	    		break;
	    	case WITNESS_S_CONNECTED: 
	    		switch(oldSMState){
	    			case WITNESS_S_LISTENING:
	    				mStampContext = new WitnessContext(mContext);
	    				printTransition(oldSMState, newSMState, true, false);
	    				break;
	    			default:
	    				printTransition(oldSMState, newSMState, false, false);
	    				break;	
	    		}
	    		break;
	    	case WITNESS_S_PREQ_RCVD: 
	    		switch(oldSMState){
		    		case WITNESS_S_CONNECTED:
		    			StampMessage.processPreq(mStampContext, param.getByteArray(RCVD_MESSAGE));
		    			/* notify remote we are ready for distance bounding */
		    			sendMessage(MESSAGE_DBSTART);
		    			printTransition(oldSMState, newSMState, true, false);
		    			break;
	    			default:
	    				printTransition(oldSMState, newSMState, false, false);
	    				break;
	    		}
	    		break;
	    	case WITNESS_S_DB_START: 
	    		switch(oldSMState){
		    		case WITNESS_S_PREQ_RCVD:
		    			printTransition(oldSMState, newSMState, true, false);
		    			break;
		    		default:
		    			printTransition(oldSMState, newSMState, false, false);
		    			break;
	    		}
	    		break;
	    	case WITNESS_S_DB_SUCCESS: 
	    		switch(oldSMState){
		    		case WITNESS_S_DB_START:
		    			StampMessage.processCeCk(mStampContext, param.getByteArray(RCVD_MESSAGE));
		    			dbFinishTime = System.currentTimeMillis();
		    			sendMessage(MESSAGE_EP);
		    			printTransition(oldSMState, newSMState, true, false);
		    			break;
		    		default:
		    			printTransition(oldSMState, newSMState, false, false);
		    			break;
	    		}
	    		break;
	    	case WITNESS_S_EP_SENT: 
	    		switch(oldSMState){
		    		case WITNESS_S_DB_SUCCESS:
		    			// Log data
		    			Log.i(TAG, "Total DB Time: " + (dbFinishTime - dbStartTime));
		    			Log.i(TAG, "Total Communication: " + comOverhead);
		    			
		    			/* finished, back to listening */
//		    			try{Thread.sleep(3000);}catch(InterruptedException e){e.printStackTrace();}
//		    			addSMTask(new moveSM(WITNESS_S_LISTENING,null));
		    			printTransition(oldSMState, newSMState, true, false);
		    			printDataLog(0, false);
		    			break;
		    		default:
		    			printTransition(oldSMState, newSMState, false, false);
		    			break;
	    		}
	    		break;
    		default: 
    			printTransition(oldSMState, newSMState, false, false);
    			break;
    	}
    }
    
	/**
	 * Handler for sending messages
	 * @param messageType type of message
	 */
    private void sendMessage(byte messageType){
    	byte mWriteBuf[] ={};
    	byte header[] = {0x0A, 0x0B, messageType, 0x00, 0x00, 0x00, 0x00};
    	byte payload[] = null;
    	int size = 0;		// size cannot be that large can it? 
    	
    	switch(messageType){
    		case MESSAGE_DBSTART:
    			dbStartTime = System.currentTimeMillis();
    			payload = StampMessage.createDBStart();
    			size = payload.length;
    			System.arraycopy(ByteBuffer.allocate(4).putInt(size).array(), 0, header, 3, 4);
    			mWriteBuf = new byte[header.length + size];
    			System.arraycopy(header, 0, mWriteBuf, 0, header.length);
    			System.arraycopy(payload, 0, mWriteBuf, header.length, size);
    			mWitnessBtService.write(mWriteBuf, MESSAGE_DBSTART);
    			break;
    		case MESSAGE_EP:
    			payload = StampMessage.createEP(mStampContext);
    			size = payload.length;
    			System.arraycopy(ByteBuffer.allocate(4).putInt(size).array(), 0, header, 3, 4);
    			mWriteBuf = new byte[header.length + size];
    			System.arraycopy(header, 0, mWriteBuf, 0, header.length);
    			System.arraycopy(payload, 0, mWriteBuf, header.length, size);
    			mWitnessBtService.write(mWriteBuf, MESSAGE_EP);
    			break;
    		default:
    			break;
    	}
    	comOverhead += mWriteBuf.length;
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
    	comOverhead += message.length;
    	
    	int msgStart = 0;
    	byte header[];
    	byte payload[];
    	int size = 0;
    	int bytesRcvd;
    	
    	Bundle bundle; 
    	
    	if(assembleBuffer == null){
    		/* new packet */
    		while(remainSize > 0){
    			if(remainSize > MESSAGE_HEADER_LEN){
    				/* copy header in */
    				header = new byte[MESSAGE_HEADER_LEN];
    				System.arraycopy(message, msgStart, header, 0, MESSAGE_HEADER_LEN);
    				bytesRcvd = message.length - MESSAGE_HEADER_LEN;	// rcvd payload size
    				/* check packet size */
    				size = ByteBuffer.wrap(header, 3, 4).getInt();
    				packetSize = size;					// total payload size
    				if(bytesRcvd < size){
    					/* incomplete, save payload into assemble buffer */
    					assembleBuffer = new byte[163840];
    					bytesRead = 0;
    					System.arraycopy(message, msgStart, 
    							assembleBuffer, bytesRead, message.length);
    					bytesRead += message.length;
    					/* finish this buffer read, wait for next fragment */
    					break;
    				}else{
    					/* bytesRcvd >= size? normal case */
    					payload = new byte[packetSize];
    	    			System.arraycopy(message, msgStart+MESSAGE_HEADER_LEN, payload, 0, packetSize);
    	    			if((header[0]==MESSAGE_STAMP_BYTE1)&&(header[1]==MESSAGE_STAMP_BYTE2)){
    	    				switch(header[2]){
    		    				case MESSAGE_PREQ:
    			    				/* send notification */
    			    				printRemoteMessage(remote[1], MESSAGE_PREQ);
    			    				/* wrap payload */
    			    				bundle = new Bundle();
    			    				bundle.putByteArray(RCVD_MESSAGE, payload);
    			    				addSMTask(new moveSM(WITNESS_S_PREQ_RCVD,bundle));
    			    				break;
    			    			case MESSAGE_CECK:
    			    				printRemoteMessage(remote[1], MESSAGE_CECK);
    			    				/* wrap payload */
    			    				bundle = new Bundle();
    			    				bundle.putByteArray(RCVD_MESSAGE, payload);
    			    				addSMTask(new moveSM(WITNESS_S_DB_SUCCESS,bundle));
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
    			}
    			msgStart = msgStart + MESSAGE_HEADER_LEN + size;
        		remainSize -= msgStart;
    		}
    	}else{
    		if((remainSize+bytesRead-MESSAGE_HEADER_LEN) < packetSize){
    			/* all payload belongs to previous incomplete packet */
    			System.arraycopy(message, msgStart, 
						assembleBuffer, bytesRead, remainSize);
    			bytesRead += remainSize;
    		}else{
    			/* previous packet receive complete, but may be with new packet */
    			System.arraycopy(message, msgStart, 
						assembleBuffer, bytesRead, packetSize+MESSAGE_HEADER_LEN-bytesRead);
    			Log.d("TAG", "packet size: "+packetSize+" bytesREad: "+bytesRead+" remain: "+remainSize);
    			/* start normal processing on assemble Buffer*/
    			header = new byte[MESSAGE_HEADER_LEN];
				System.arraycopy(assembleBuffer, 0, header, 0, MESSAGE_HEADER_LEN);
    			payload = new byte[packetSize];
    			System.arraycopy(assembleBuffer, MESSAGE_HEADER_LEN, payload, 0, packetSize);
    			if((header[0]==MESSAGE_STAMP_BYTE1)&&(header[1]==MESSAGE_STAMP_BYTE2)){
    				switch(header[2]){
	    				case MESSAGE_PREQ:
		    				/* send notification */
		    				printRemoteMessage(remote[1], MESSAGE_PREQ);
		    				/* wrap payload */
		    				bundle = new Bundle();
		    				bundle.putByteArray(RCVD_MESSAGE, payload);
		    				addSMTask(new moveSM(WITNESS_S_PREQ_RCVD,bundle));
		    				break;
		    			case MESSAGE_CECK:
		    				printRemoteMessage(remote[1], MESSAGE_CECK);
		    				/* wrap payload */
		    				bundle = new Bundle();
		    				bundle.putByteArray(RCVD_MESSAGE, payload);
		    				addSMTask(new moveSM(WITNESS_S_DB_SUCCESS,bundle));
		    				break;
	    				default:
	    					break;
    				}
    			}
    			if(D){
    				Log.d(TAG, "Assemble Message "+header[2]+": "+packetSize+" bytes received");
    				Log.d(TAG, new String(payload));
    			}

    			remainSize = remainSize - (packetSize+MESSAGE_HEADER_LEN-bytesRead);
    			/* clear assemble buffer*/
    			assembleBuffer = null;
    			bytesRead = 0;
    			while(remainSize>0){
    				if(remainSize > MESSAGE_HEADER_LEN){
        				/* copy header in */
        				header = new byte[MESSAGE_HEADER_LEN];
        				System.arraycopy(message, msgStart, header, 0, MESSAGE_HEADER_LEN);
        				bytesRcvd = message.length - MESSAGE_HEADER_LEN;	// rcvd payload size
        				/* check packet size */
        				size = ByteBuffer.wrap(header, 3, 4).getInt();
        				packetSize = size;					// total payload size
        				if(bytesRcvd < size){
        					/* incomplete, save payload into assemble buffer */
        					assembleBuffer = new byte[40960];
        					bytesRead = 0;
        					System.arraycopy(message, msgStart, 
        							assembleBuffer, bytesRead, message.length);
        					bytesRead += message.length;
        					/* finish this buffer read, wait for next fragment */
        					break;
        				}else{
        					/* bytesRcvd >= size? normal case */
        					payload = new byte[packetSize];
        	    			System.arraycopy(message, msgStart+MESSAGE_HEADER_LEN, payload, 0, packetSize);
        	    			if((header[0]==MESSAGE_STAMP_BYTE1)&&(header[1]==MESSAGE_STAMP_BYTE2)){
        	    				switch(header[2]){
        		    				case MESSAGE_PREQ:
        			    				/* send notification */
        			    				printRemoteMessage(remote[1], MESSAGE_PREQ);
        			    				/* wrap payload */
        			    				bundle = new Bundle();
        			    				bundle.putByteArray(RCVD_MESSAGE, payload);
        			    				addSMTask(new moveSM(WITNESS_S_PREQ_RCVD,bundle));
        			    				break;
        			    			case MESSAGE_CECK:
        			    				printRemoteMessage(remote[1], MESSAGE_CECK);
        			    				/* wrap payload */
        			    				bundle = new Bundle();
        			    				bundle.putByteArray(RCVD_MESSAGE, payload);
        			    				addSMTask(new moveSM(WITNESS_S_DB_SUCCESS,bundle));
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
        			}
    				msgStart = msgStart + MESSAGE_HEADER_LEN + size;
    	    		remainSize -= msgStart;
    			}
    		}
    	}
    	
//    	while(remainSize > 0){
//    		if(remainSize > MESSAGE_HEADER_LEN){
//    			header = new byte[MESSAGE_HEADER_LEN]; 
//    			System.arraycopy(message, msgStart, header, 0, MESSAGE_HEADER_LEN);
//    			size = ByteBuffer.wrap(header, 3, 4).getInt();
//    			payload = new byte[size];
//    			System.arraycopy(message, msgStart+MESSAGE_HEADER_LEN, payload, 0, size);
//    			if((header[0]==MESSAGE_STAMP_BYTE1)&&(header[1]==MESSAGE_STAMP_BYTE2)){
//    				switch(header[2]){
//	    				case MESSAGE_PREQ:
//		    				/* send notification */
//		    				printRemoteMessage(remote[1], MESSAGE_PREQ);
//		    				/* wrap payload */
//		    				bundle = new Bundle();
//		    				bundle.putByteArray(RCVD_MESSAGE, payload);
//		    				addSMTask(new moveSM(WITNESS_S_PREQ_RCVD,bundle));
//		    				break;
//		    			case MESSAGE_CECK:
//		    				printRemoteMessage(remote[1], MESSAGE_CECK);
//		    				/* wrap payload */
//		    				bundle = new Bundle();
//		    				bundle.putByteArray(RCVD_MESSAGE, payload);
//		    				addSMTask(new moveSM(WITNESS_S_DB_SUCCESS,bundle));
//		    				break;
//	    				default:
//	    					break;
//    				}
//    			}
//    			if(D){
//    				Log.d(TAG, "Message "+header[2]+": "+size+" bytes received");
//    				Log.d(TAG, new String(payload));
//    			}
//    		}
//    		msgStart = msgStart + MESSAGE_HEADER_LEN + size;
//    		remainSize -= msgStart;
//    	}
    }
    
    /**
	 * Witness's handler for Bluetooth events
	 */
	public class WitnessBtListener implements BtEventListener{
		private String remote[];
		public void onBtEvent(int what, Bundle data) {
			switch(what){
				case BTEVENT_INQUIRY_FINISHED: 
					break;
				case BTEVENT_CONNECTING:
					break;
				case BTEVENT_CONNECTION_FAILED: 
					break;
				case BTEVENT_CONNECTED:
					remote = data.getString(REMOTE_DEVICE).split(";");
					printRemoteStatus(remote[1], BTEVENT_CONNECTED);
					addSMTask(new moveSM(WITNESS_S_CONNECTED,null));
					break;
				case BTEVENT_CONNECTION_LOST: 
					remote = data.getString(REMOTE_DEVICE).split(";");
					printRemoteStatus(remote[1], BTEVENT_CONNECTION_LOST);
					if(getSMState()!= WITNESS_S_LISTENING){
						addSMTask(new moveSM(WITNESS_S_LISTENING,null));
					}
					break;
				case BTEVENT_MSG_RCVD:
					readMessage(data);
					break;
				case BTEVENT_MSG_SENT:
					byte msgType = data.getByte(SENT_MESSAGE);
					switch(msgType){
						case MESSAGE_DBSTART:
							/* move sm until we get the confirmation */
							addSMTask(new moveSM(WITNESS_S_DB_START,null));
							break;
						case MESSAGE_EP:
							/* move sm until we get the confirmation */
							addSMTask(new moveSM(WITNESS_S_EP_SENT,null));
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
