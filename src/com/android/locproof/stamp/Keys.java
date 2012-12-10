package com.android.locproof.stamp;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;


public class Keys {
	
	public static final String DSA_SIZE = "512";
	public static final String RSA_SIZE = "1024";
	
	public static byte[] MY_DSA_PUP_KEY;
	public static byte[] MY_DSA_PRI_KEY;
	public static byte[] CA_PUP_KEY;
	
	public static void initKeys(){
		FileInputStream keyfis;
		try {
			keyfis = new FileInputStream("keys\\DSAPubKey"+DSA_SIZE);
			MY_DSA_PUP_KEY = new byte[keyfis.available()];
			keyfis.close();
			
			keyfis = new FileInputStream("keys\\DSAPriKey"+DSA_SIZE);
			MY_DSA_PRI_KEY = new byte[keyfis.available()];
			keyfis.close();
			
			keyfis = new FileInputStream("keys\\RSAPubKey"+RSA_SIZE);
			CA_PUP_KEY = new byte[keyfis.available()];
			keyfis.close();
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
}
