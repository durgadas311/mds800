// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

public class ParityGenerator {
	private static byte[] evenParity = {
		         0,(byte)0x80,(byte)0x80,         0,
		(byte)0x80,         0,         0,(byte)0x80,
		(byte)0x80,         0,         0,(byte)0x80,
		         0,(byte)0x80,(byte)0x80,         0
	};

	// 7-bit characters only, adds 8th bit parity
	public static int evenParity(int c) {
		c &= 0x7f;
		int ep = (evenParity[c & 0xf] ^ evenParity[(c >> 4) & 0x0f]) & 0xff;
		return (c | ep);
	}

	// 7-bit characters only, adds 8th bit parity
	public static int oddParity(int c) {
		return evenParity(c) ^ 0x80;
	}

	public static int parity(int c, boolean even) {
		c = evenParity(c);
		return (even ? c : c ^ 0x80);
	}

	public static int noParity(int c, boolean mark) {
		if (mark) {
			c |= 0x80;
		}
		return c;
	}
}
