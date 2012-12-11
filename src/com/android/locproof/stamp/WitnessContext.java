package com.android.locproof.stamp;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Date;

import android.location.Location;

/**
 * Context information associated with each proof operation
 */
public class WitnessContext {		
	private static final int LOCATION_LEVEL = 1; // Location level from 0 to 5, 0 is the finest
	
	private ArrayList<String> mLocations;			// current location
	
	private static RSAPublicKey _pubRSACA;
	private static DSAPublicKey _pubDSASelf;
	private static DSAPrivateKey _priDSASelf;
			
	
	private byte remoteCommittedID[] = null;		// committed ID from remote preq msg
	private byte remoteLocation[] = null;			// location from remote preq msg
	private byte remoteTime[] = null;				// time from remote preq msg
	private BigInteger randomW;						// witness's commitment nonce
	private BigInteger remoteZ;						// z calcuated based on ce ck received from prover
	
	/**
	 * Constructor 
	 * @param aID entity's ID
	 */
	public WitnessContext(){
		//Initialize keys
		initKeys();
		
		// Prepare a randomW 
		randomW = CryptoUtil.getRandomSecureNumber();
	}
		
	private void initKeys() {
		Keys.initKeys();
		try {
			setSelfDSAPubKey(Keys.MY_DSA_PUP_KEY);
			setSelfDSAPriKey(Keys.MY_DSA_PRI_KEY);
			setCAPubKey(Keys.CA_PUP_KEY);
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
		} catch (InvalidKeySpecException e1) {
			e1.printStackTrace();
		}
	}

	public DSAPublicKey getPubDSASelf() {
		return _pubDSASelf;
	}

	public RSAPublicKey getPubRSACA() {
		return _pubRSACA;
	}

	public DSAPrivateKey getPriDSASelf() {
		return _priDSASelf;
	}

	/**
	 * Get current location context
	 * @return current location
	 */
	public ArrayList<String> getLocation(){
		ArrayList<String> clone = new ArrayList<String>(mLocations);
		return clone;
	}
	
	/**
	 * Set current location
	 * @param aLocation current fix from location source 
	 * @param aLevel finest level this location should be
	 */
	public void setLocation(Location aLocation, int aLevel){
		/* for prover the level is always set to 0 */
		for (int i = aLevel; i < LOCATION_LEVEL; i++){
			mLocations.set(i, locationLookup(aLocation, aLevel));
		}
	}
	
	/**
	 * Witness: Set committed ID of current prover
	 * @param aCommittedID new committed ID
	 */
	public byte[] getRemoteCommittedID(){
		return remoteCommittedID;
	}
	
	/**
	 * Witness: Get committed ID of current prover
	 * @return committed ID
	 */
	public void setRemoteCommittedID(byte aRemoteCommittedID[]){
		remoteCommittedID = aRemoteCommittedID;
	}
	
	/**
	 * Witness: Get preq time of current prover
	 * @return preq time
	 */
	public byte[] getRemoteTime(){
		return remoteTime;
	}
	
	/**
	 * Witness: Set preq time of current prover
	 * @param aRemoteTime preq time
	 */
	public void setRemoteTime(byte aRemoteTime[]){
		remoteTime = aRemoteTime;
	}
	
	/**
	 * Witness: Get preq location of current prover
	 * @return preq location
	 */
	public byte[] getRemoteLocation(){
		return remoteLocation;
	}
	
	/**
	 * Witness: Get preq location of current prover
	 * @param aRemoteLocation preq location
	 */
	public void setRemoteLocation(byte aRemoteLocation[]){
		remoteLocation = aRemoteLocation;
	}
	
	/**
	 * Witness: Get commitment random nonce
	 * @return commitment random nonce
	 */
	public BigInteger getEPRandomW(){
		return randomW;
	}
	
	/**
	 * Witness: Set commitment random nonce
	 * @param aRandomW random nonce
	 */
	public void setEPRandomW(BigInteger aRandomW){
		randomW = aRandomW;
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
		Location update = new Location("test");
		update.setLatitude(38.534844);
		update.setLongitude(-121.752685);
		for(int i = 0; i < LOCATION_LEVEL; i++){
			/* initialize location at each level to N/A */
			mLocations.add(i, locationLookup(update,i));
		}
	}
	
	/**
	 * Dummy function that makes location translation at appropriate level 
	 * TODO: add look up for different levels
	 * @param aLocation source location
	 * @param aLevel finest level this location should be
	 * @return String representation of location
	 */
	private String locationLookup(Location aLocation, int aLevel){
		String loc = aLevel+":"+aLocation.getLatitude()+","+aLocation.getLongitude();
		return loc;
	}

	public void setRemoteZ(BigInteger z) {
		this.remoteZ = z;
	}

	public BigInteger getRemoteZ() {
		return remoteZ;
	}
	
	private static void setSelfDSAPubKey(byte[] keyBytes) throws NoSuchAlgorithmException, InvalidKeySpecException{
		_pubDSASelf = CryptoUtil.getDSAPubKeyfromEncoded(keyBytes);
	}
	
	
	private static void setSelfDSAPriKey(byte[] keyBytes) throws NoSuchAlgorithmException, InvalidKeySpecException{
		_priDSASelf = CryptoUtil.getDSAPriKeyfromEncoded(keyBytes);
	}
	
	private static void setCAPubKey(byte[] keyBytes) throws NoSuchAlgorithmException, InvalidKeySpecException{
		_pubRSACA = CryptoUtil.getRSAPubKeyfromEncoded(keyBytes);
	}

}
