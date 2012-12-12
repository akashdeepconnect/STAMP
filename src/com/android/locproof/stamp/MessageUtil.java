package com.android.locproof.stamp;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class MessageUtil {

	/**
	 * Combine messages in an array and put a size header before each message
	 * @param array
	 * @return
	 */
	public static byte[] compileMessages(ArrayList<byte[]>  array){
		
		int bufferSize = 0;
		for(int i = 0; i< array.size(); i++){
			byte[] thisByte = array.get(i);
			bufferSize += (4+thisByte.length);
		}
		
		byte[] buffer = new byte[bufferSize];
		
		int pointer = 0;	// used to index the next empty byte to fill
		for(int i = 0; i< array.size(); i++){
			int thisSize = array.get(i).length;
			System.arraycopy(ByteBuffer.allocate(4).putInt(thisSize).array(), 0, buffer, pointer, 4);
			System.arraycopy(array.get(i), 0, buffer, pointer+4, thisSize);
			pointer += (4+thisSize);
		}
		
		return buffer;
	}
	
	/**
	 * Combine two messages directly
	 * @param msg1
	 * @param msg2
	 * @return
	 */
	public static byte[] concatenateMessages(byte[] msg1, byte[] msg2){
		
		int bufferSize = msg1.length + msg2.length;
		byte[] buffer = new byte[bufferSize];
		
		System.arraycopy(msg1, 0, buffer, 0, msg1.length);
		System.arraycopy(msg2, 0, buffer, msg1.length, msg2.length);
		
		return buffer;
	}

	/**
	 * Create a message from an array, first field of the message is the size of the array
	 * @param array
	 * @return
	 */
	public static byte[] createMessageFromArray(ArrayList<byte[]>  array){
		
		byte[] buffer1 = new byte[4];
		System.arraycopy(ByteBuffer.allocate(4).putInt(array.size()).array(), 0, buffer1, 0, 4);
		
		byte[] buffer2 = compileMessages(array);
		
		return concatenateMessages(buffer1, buffer2);
	}
	
	public static ArrayList<byte[]> parseMessage(byte[] origMsg, int msgCount){
		
		ArrayList<byte[]> array = new ArrayList<byte[]>();
		
		int pointer = 0; // used to index the next byte to read
		
		for (int i= 0; i< msgCount; i++){
			int thisSize = ByteBuffer.wrap(origMsg, pointer, 4).getInt();
			byte[] b = new byte[thisSize];
			System.arraycopy(origMsg, pointer+4, b, 0, thisSize);
			array.add(b);
			pointer += (4+thisSize);
		}
		return array;
	}
	
	public static ArrayList<byte[]> parseMessages(byte[] origMsg){
		
		int msgCount = ByteBuffer.wrap(origMsg, 0, 4).getInt();
		byte[] newMsg = new byte[origMsg.length-4];
		System.arraycopy(origMsg, 4, newMsg, 0, origMsg.length-4);
		
		return parseMessage(newMsg, msgCount);
	}
	
	
}