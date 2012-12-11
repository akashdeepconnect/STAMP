package com.android.locproof.stamp;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;

import android.content.Context;

/**
 * Context information associated with each proof operation
 */
public class ProverContext {		
	// private static final int LOCATION_LEVEL = 1; // Location level from 0 to 5, 0 is the finest
	
	private Location mLocation;			// current location
	
	private static DSAPublicKey _pubDSASelf;
	private static DSAPrivateKey _priDSASelf;
			
	private BigInteger randomP;						// prover's commmitment nonce
	private BigInteger h;
	private BigInteger u;
	private BigInteger k;
	private BigInteger e;
	private BigInteger v;
	
	private byte[] mCommittedID; 					// prover's committed ID
	
	/**
	 * Constructor 
	 * @param aID entity's ID
	 */
	public ProverContext(Context aContext){
		//Initialize keys
		initKeys(aContext);
		
		// Generate rp and prepare committed ID
		randomP = CryptoUtil.getRandomSecureNumber();
		try {
			mCommittedID = CryptoUtil.getCommitment(getPubDSASelf().toString().getBytes(), randomP).toByteArray();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		updateLocation();
		
		// Prepare stuff to be used for Bussard-Bagga distance bounding
		PrepareDB();
	}
	
	private void initKeys(Context aContext) {
		Keys.initKeys(aContext);
		try {
			setSelfDSAPubKey(Keys.MY_DSA_PUP_KEY);
			setSelfDSAPriKey(Keys.MY_DSA_PRI_KEY);
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
		} catch (InvalidKeySpecException e1) {
			e1.printStackTrace();
		}
	}

	private void PrepareDB() {
		BigInteger x = _priDSASelf.getX();
		BigInteger p = _pubDSASelf.getParams().getP();
		h = CryptoUtil.getH(p);
		k = CryptoUtil.getNonce(p);
		u = CryptoUtil.getU(p);
		e = CryptoUtil.getE(getU(), getK(), p, x);
		v = CryptoUtil.getNonce(p);
	}

	public DSAPublicKey getPubDSASelf() {
		return _pubDSASelf;
	}

	/**
	 * Get current location context
	 * @return current location
	 */
	public Location getLocation(){
		return mLocation;
	}
		
	/**
	 * Prover: Get committed ID
	 * @return committed ID
	 */
	public byte[] getCommittedID(){
		return mCommittedID;
	}
	
	/**
	 * Prover: Set committed ID
	 * @param aCommittedID committed ID
	 */
	public void setCommittedID(byte[] aCommittedID){
		mCommittedID = aCommittedID;
	}
	
	public BigInteger getEPRandomP() {
		return randomP;
	}

	
	/**
	 * Wrapper function gives current time 
	 * @return current time in milliseconds 
	 */
	public static long getTime(){
		return new Date().getTime();
	}
	
	/**
	 * Obtain current location fix from arbitrary location provider
	 */
	private void updateLocation(){
		mLocation = new Location("test", 38.534844, -121.752685);
	}
	
	/**
	 * Dummy function that makes location translation at appropriate level 
	 * TODO: add look up for different levels
	 * @param aLocation source location
	 * @param aLevel finest level this location should be
	 * @return String representation of location
	 */
//	private String locationLookup(Location aLocation, int aLevel){
//		String loc = aLevel+":"+aLocation.getLatitude()+","+aLocation.getLongitude();
//		return loc;
//	}
	
	public BigInteger getH() {
		return h;
	}

	public BigInteger getU() {
		return u;
	}

	public BigInteger getK() {
		return k;
	}

	public BigInteger getE() {
		return e;
	}

	public BigInteger getV() {
		return v;
	}
	
	private static void setSelfDSAPubKey(byte[] keyBytes) throws NoSuchAlgorithmException, InvalidKeySpecException{
		_pubDSASelf = CryptoUtil.getDSAPubKeyfromEncoded(keyBytes);
	}
	
	
	private static void setSelfDSAPriKey(byte[] keyBytes) throws NoSuchAlgorithmException, InvalidKeySpecException{
		_priDSASelf = CryptoUtil.getDSAPriKeyfromEncoded(keyBytes);
	}
	
}
