// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.util.Properties;

// A dummy device to pass-thru TTY RDR advance char
public class DummyPaperTape implements IODevice {
	// by mutual agreement with any TTY-attached peripheral
	private static final int RDR = 0x0f;	// ^O - new: advance reader once

	INS8251 tty;
	private int basePort;
	private String name = null;

	// 0xf9 - status
	private static final int STS_PTR_RXD = 0x01;	// data ready
	private static final int STS_PTR_RDY = 0x02;	// device ready
	private static final int STS_PTP_TXE = 0x04;	// ready for data
	private static final int STS_PTP_RDY = 0x08;	// device ready
	private static final int STS_PTP_CHD = 0x10;	// chad error
	private static final int STS_PTP_LOW = 0x20;	// tape low
	// 0xf9 - control
	private static final int CTL_TTY_ADV = 0x02;	// strobe TTY PTR one char
	private static final int CTL_PTR_DIR = 0x04;	// reader direction?
	private static final int CTL_PTR_DRV = 0x08;	// strobe PTR one char
	private static final int CTL_PTP_FOR = 0x10;	// punch forward (direction)?
	private static final int CTL_PTP_ADV = 0x20;	// punch one char

	private int tty_rdr_adv_char;

	public DummyPaperTape(Properties props, int base, INS8251 tty) {
		name = "DMY_PT";
		this.tty = tty;
		tty_rdr_adv_char = RDR;
		basePort = base;
		String s;
		s = props.getProperty("mds800_rdr_adv_char");
		if (s == null) {
			s = props.getProperty("tty_rdr_adv_char");
		}
		if (s != null) {
			tty_rdr_adv_char = Integer.decode(s) & 0xff;
		}
		reset();
	}

	///////////////////////////////
	/// Interfaces for IODevice ///
	public int in(int port) {
		int off = port - basePort;
		int val = 0;
		switch(off) {
		case 0: // Rx Data
			break;
		case 1:
			break;
		}
		return val;
	}

	public void out(int port, int val) {
		int off = port - basePort;
		val &= 0xff; // necessary?
		switch(off) {
		case 0: // Tx Data
			break;
		case 1:
			if ((val & CTL_TTY_ADV) != 0) {
				if (tty != null) {
					tty.out(tty.getBaseAddress(), tty_rdr_adv_char);
				}
			}
			break;
		}
	}

	public void reset() {
	}
	public int getBaseAddress() {
		return basePort;
	}
	public int getNumPorts() {
		return 2;
	}

	public String getDeviceName() { return name; }

	public String dumpDebug() {
		String ret = new String();
		ret += String.format("Dummy PT port %02x tty_rdr_adv %02x\n",
			basePort, tty_rdr_adv_char);
		return ret;
	}
}
