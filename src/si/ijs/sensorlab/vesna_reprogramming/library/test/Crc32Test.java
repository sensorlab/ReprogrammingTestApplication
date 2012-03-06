package si.ijs.sensorlab.vesna_reprogramming.library.test;

import java.util.zip.CRC32;

import si.ijs.sensorlab.vesna_reprogramming.library.Stm32Crc32;

public class Crc32Test {

	final static int data1[] = { 1 };
	final static int data2[] = { 1, 2 };
	final static int data3[] = { 1, 2, 3 };
	final static int data4[] = { 1, 2, 3, 4 };
	
	final static byte[] b1 = { 0,0,0,1 };
	final static byte[] b2 = { 0,0,0,1, 0,0,0,2 };

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		CRC32 crc = new CRC32();

		crc.reset();		
		System.out.println("CRC on nothing: " + Long.toHexString(crc.getValue()));

		crc.reset();
		crc.update('0');
		System.out.println("CRC on '0': " + Long.toHexString(crc.getValue()));

		crc.reset();
		crc.update('0');
		crc.update('1');
		System.out.println("CRC on '01': " + Long.toHexString(crc.getValue()));

		crc.reset();
		crc.update('0');
		crc.update('0');
		crc.update('0');
		crc.update('1');
		System.out.println("CRC on '0001': " + Long.toHexString(crc.getValue()));

		crc.reset();
		crc.update('1');
		crc.update('0');
		crc.update('0');
		crc.update('0');
		System.out.println("CRC on '1000': " + Long.toHexString(crc.getValue()));
		
		crc.reset();
		/*
		crc.update(0);
		crc.update(0);
		crc.update(0);
		crc.update(1);
		*/
		crc.update(1);
		crc.update(0);
		System.out.println("CRC on {1} = 0x" + Long.toHexString(crc.getValue()));
		
		crc.reset();
		/*
		crc.update(0);
		crc.update(0);
		crc.update(0);
		crc.update(1);
		crc.update(0);
		crc.update(0);
		crc.update(0);
		crc.update(2);
		*/
		crc.update(b2);
		System.out.println("CRC on {1,2} = 0x" + Long.toHexString(crc.getValue()));

		Stm32Crc32 crc2 = new Stm32Crc32();
		
		crc2.reset();
		crc2.getValue();
		System.out.println("CRC2 on nothing: " + Long.toHexString(crc2.getValue()));
		
		crc2.reset();
		crc2.update(1);
		System.out.println("CRC2 on {1} = 0x" + Long.toHexString(crc2.getValue()));
	}

}
