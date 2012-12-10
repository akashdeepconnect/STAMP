package com.android.locproof.stamp;

import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Currency;

import android.content.Context;
import android.os.Environment;

/**
 * EP record structure
 * TODO: replace with sqlite database
 */
public class StampEPRecord {
	private Context mContext;
	
	private long mEPTime;		// use time as single index, not necessarily identical to preq time
	private String mEPID;		// ID used when generating this proof 	
	private BigInteger mEPRandomP;	// random number used when generating this proof
	private String mPreq;		// messages are converted and stored as String 
	private ArrayList<String> mEPList;	// list of EPs from witnesses
	
	//For Bussard-Bagga
	private BigInteger u;	// random u used for symmetric encryption
	private BigInteger v;	// random v used for generation of bit commitments TODO: v should be an array
	
	public StampEPRecord(ProverContext currStampContext, Context aContext){
		mContext = aContext;
		mEPTime = ProverContext.getTime();
		mEPID = currStampContext.getPubDSASelf().toString();
		mEPRandomP = currStampContext.getEPRandomP();
		mPreq = null;
		mEPList = new ArrayList<String>();
		u = currStampContext.getU();
		v = currStampContext.getV();
	}
	
	public long getEPTime(){
		return mEPTime;
	}
	
	public String getEPID(){
		return mEPID;
	}
	
	public BigInteger getEPRandomP(){
		return mEPRandomP;
	}
	
	public void setPreq(byte preq[]){
		mPreq = new String(preq);
	}
	
	public String getPreq(){
		return mPreq;
	}
	
	public void addEP(byte newEP[]){
		mEPList.add(new String(newEP));
	}
	
	public ArrayList<String> getEPList(){
		ArrayList<String> clone = new ArrayList<String>(mEPList);
		return clone;
	}
	
	public void printEPRecord(){
		try{
			if(isReadyExternalStorage()){
				String mData = mEPTime+"\t"+mEPID+"\t"+mPreq+"\n";
				for(int i = 0; i < mEPList.size(); i++){
					mData = mData + mEPList.get(i) + "\n";
				}
				mData = mData + "\n";
				/* write record to file */	
				File logfile = new File(mContext.getExternalFilesDir(null), "EPRecord.txt");
				FileWriter writer = new FileWriter(logfile, true);
				/* don't worry if the file exists or not, just append */ 
				writer.append(mData);
		        writer.flush();
		        writer.close();
			}
			/* TODO: handle errors when device is unavailable */
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * check if the external storage is ready to write
	 * @return true on writable, false on otherwise
	 */
	private boolean isReadyExternalStorage(){
		boolean mExternalStorageWriteable = false;
		String state = Environment.getExternalStorageState();

		if (Environment.MEDIA_MOUNTED.equals(state)) {
		    /* We can read and write the media */
		    mExternalStorageWriteable = true;
		}
		/* we need a writable device */
		return mExternalStorageWriteable;
	}
}
