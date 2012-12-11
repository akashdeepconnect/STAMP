package com.android.locproof.stamp;

import java.io.File;
import java.io.FileInputStream;
import android.content.Context;
import android.util.Log;


public class Keys {
	
	public static final String DSA_SIZE = "512";
	public static final String RSA_SIZE = "1024";
	
	public static byte[] MY_DSA_PUP_KEY;
	public static byte[] MY_DSA_PRI_KEY;
	public static byte[] CA_PUP_KEY;
	
	/**
	 * Read key content into an byte array from key file
	 * @param aKeyFile key file
	 * @return byte array storing key content
	 */
	private static byte[] readKeyFile(String aKeyFile){
		File keyFile = new File(aKeyFile);
		byte[] data = new byte[(int) keyFile.length()];
		try{
			new FileInputStream(keyFile).read(data);
		}catch (Exception e){
			e.printStackTrace();
		}
		return data;
	}
	
	public static void initKeys(Context aContext){
		String keyPath = aContext.getExternalFilesDir(null).getAbsolutePath();
		String filePath;

		filePath = keyPath+"/keys/DSAPubKey"+DSA_SIZE;
		MY_DSA_PUP_KEY = readKeyFile(filePath);
		Log.d("KEYS", "DSAPUB: "+new String(MY_DSA_PUP_KEY)+" bytes: "+MY_DSA_PUP_KEY.length);
		
		filePath = keyPath+"/keys/DSAPriKey"+DSA_SIZE;
		MY_DSA_PRI_KEY = readKeyFile(filePath);
		Log.d("KEYS", "DSAPRI: "+new String(MY_DSA_PRI_KEY)+" bytes: "+MY_DSA_PRI_KEY.length);
		
		filePath = keyPath+"/keys/RSAPubKey"+RSA_SIZE;
		CA_PUP_KEY = readKeyFile(filePath);
		Log.d("KEYS", "CAKEY: "+new String(CA_PUP_KEY)+" bytes: "+CA_PUP_KEY.length);
	}
}