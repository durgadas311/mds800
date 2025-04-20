// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Properties;

public class MDSRoms {
	protected Rom mon;	// 2K at 0xf800
	protected Rom boot;	// 256 at 0x0000
	protected int monMask;

	public MDSRoms(Properties props) {
		InputStream fi = null;
		String s = props.getProperty("mds800_mon");
		if (s == null) {
			s = "intel_monitor.rom";
		}
		try {
			mon = new EPROM(s, 0xf800);
		} catch (Exception ee) {
			ee.printStackTrace();
			System.exit(1);
		}
		monMask = mon.length();
		if (monMask != 0x0800) {
			System.err.format("Monitor ROM wrong size: %d\n", monMask);
		}
		s = props.getProperty("mds800_boot");
		if (s == null) {
			s = "1702_MDS.rom";
		}
		EPROM br = null;
		try {
			br = new EPROM(s, 0x0000);
		} catch (Exception ee) {
			ee.printStackTrace();
			System.exit(1);
		}
		boot = br;
		int n = boot.length();
		if (n != 0x0100) {
			System.err.format("Boot ROM wrong size: %d\n", n);
			return;
		}
		s = props.getProperty("mds800_iobyte"); // change defaults in PROM
		if (s != null && s.matches("^[0-3][0-3][0-3]0$")) {
			// [LST][PUN][RDR][CON]
			int iobyte = (Integer.valueOf(s.substring(0, 1)) << 6) |
				(Integer.valueOf(s.substring(1, 2)) << 4) |
				(Integer.valueOf(s.substring(2, 3)) << 2);
			br.set(0x0003, iobyte);
		}
	}
}
