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
		try {
			boot = new EPROM(s, 0x0000);
		} catch (Exception ee) {
			ee.printStackTrace();
			System.exit(1);
		}
		int n = boot.length();
		if (n != 0x0100) {
			System.err.format("Boot ROM wrong size: %d\n", n);
		}
	}
}
