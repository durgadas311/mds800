// Copyright (c) 2016 Douglas Miller <durgadas311@gmail.com>

import java.io.FileInputStream;
import java.util.Properties;
import java.io.*;
import z80core.Memory;

public class MDSMemory extends MDSRoms implements Memory {
	// One single bank of (up to) 64K, plus ROMs...
	private byte[] mem;
	private boolean rom;

	public MDSMemory(Properties props, Interruptor intr) {
		super(props, intr);
		String s = props.getProperty("mds800_ram");
		if (s.equals("64")) {
			mem = new byte[64*1024];
		} else if (s.equals("48")) {
			mem = new byte[48*1024];
		} else if (s.equals("32")) {
			mem = new byte[32*1024];
		} else {	// default to 16K
			mem = new byte[16*1024];
		}
	}

	public int read(boolean rom, int bank, int address) {
		address &= 0xffff; // necessary?
		if (address < boot.top && intr.bootOn()) {
			return boot.read(address);
		}
		if (address >= mon.base) {
			return mon.read(address);
		}
		if (address >= mem.size) {
			return 0;
		}
		return mem[address] & 0xff;
	}

	public int read(int address) {
		return read(rom, 0, address);
	}

	public void write(int address, int value) {
		mem[address & 0xffff] = (byte)(value & 0xff);
	}

	public void reset() {}

	public void dumpCore(String file) {
		try {
			OutputStream core = new FileOutputStream(file);
			core.write(mem);
			core.close();
		} catch (Exception ee) {
			MDS800Operator.error(null, "Core Dump", ee.getMessage());
		}
	}

	public String dumpDebug() {
		String str = String.format("MDS800 %dK RAM\n", mem.size / 1024);
		str += mon.dumpDebug();
		if (boot != null) {
			str += boot.dumpDebug();
		}
		return str;
	}
}
