// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Properties;

public class EPROM implements Rom {
	protected byte[] rom;
	protected int org;
	protected int msk;
	protected int xor = 0;
	private String path;

	private InputStream openROM(String rom) throws Exception {
		Exception ex = null;
		InputStream is;
		try {
			is = new FileInputStream(rom);
			path = "file:"+rom;
			return is;
		} catch (Exception ee) {
			ex = ee;
		}
		try {
			// This returns null if not found!
			InputStream fi = this.getClass().getResourceAsStream(rom);
			if (fi != null) {
				path = "jar:" + rom;
				return fi;
			}
		} catch (Exception ee) {
			ex = ee;
		}
		if (ex != null) {
			throw ex;
		}
		throw new FileNotFoundException(rom);
	}

	public int length() { return rom.length; }
	public int base() { return org; }

	public EPROM(String img, int base) throws Exception {
		org = base;
		String[] ss = img.split("\\s");
		InputStream fi = openROM(ss[0]);
		msk = fi.available() - 1;
		rom = new byte[msk + 1];
		fi.read(rom);
		fi.close();
		if (ss.length > 1 && ss[1].startsWith("rev")) {
			xor = msk;
		}
	}

	public int read(int addr) {
		return rom[(addr ^ xor) & msk] & 0xff;
	}

	public void write(int addr, int val) {}
	public void writeEnable(boolean we) {}

	public void set(int addr, int val) {
		rom[(addr ^ xor) & msk] = (byte)val;
	}

	public String dumpDebug() {
		return String.format("%d ROM at %04x ^ %04x %s\n",
				rom.length, org, xor, path);
	}
}
