package com.android.locproof.stamp;

import java.nio.ByteBuffer;

public class Commitment {
	private byte[] _a;
	private byte[] _b;
	private byte[] _y;
	
	public Commitment(byte[] aBytes, byte[] bBytes, byte[] yBytes) {
		this._a = aBytes;
		this._b = bBytes;
		this._y = yBytes;
	}
	
	public Commitment(String comString) {
		byte[] aBytes = comString.substring(0, comString.indexOf(",")).getBytes();
		byte[] bBytes = comString.substring(comString.indexOf(",")+1, comString.lastIndexOf(",")).getBytes();
		byte[] yBytes = comString.substring(comString.lastIndexOf(",")+1).getBytes();
		
		this._a = aBytes;
		this._b = bBytes;
		this._y = yBytes;
	}
	
	public byte[] toByteArray(){
		short aSize = (short) _a.length;
		short bSize = (short) _b.length;
		short ySize = (short) _y.length;
		
		byte[] buffer = new byte[6+aSize+bSize+ySize];
		System.arraycopy(ByteBuffer.allocate(2).putShort(aSize).array(), 0, buffer, 0, 2);
		System.arraycopy(_a, 0, buffer, 2, aSize);
		System.arraycopy(ByteBuffer.allocate(2).putShort(bSize).array(), 0, buffer, 2+aSize, 2);
		System.arraycopy(_b, 0, buffer, 4+aSize, bSize);
		System.arraycopy(ByteBuffer.allocate(2).putShort(ySize).array(), 0, buffer, 4+aSize+bSize, 2);
		System.arraycopy(_y, 0, buffer, 6+aSize+bSize, ySize);
		
		return buffer;
	}
	
	// TODO: instantiate from a byte array

	public byte[] getA() {
		return _a;
	}
	
	public byte[] getB() {
		return _b;
	}
	
	public byte[] getY() {
		return _y;
	}
	
	public String toString() {
		return _a.toString() + "," + _b.toString() + "," + _y.toString();
	}
}
