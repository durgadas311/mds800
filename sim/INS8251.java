// Copyright (c) 2016 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Vector;
import java.util.Properties;
import java.io.*;
import java.lang.reflect.Constructor;
import javax.swing.JFrame;

public class INS8251 implements IODevice, VirtualUART, PeripheralContainer {
	static final int fifoLimit = 10; // should never even exceed 2
	private Interruptor intr;
	private int irq;
	private int src;
	private int basePort;
	private String name = null;
	private String prefix = null;
	private java.util.concurrent.LinkedBlockingDeque<Integer> fifo;
	private java.util.concurrent.LinkedBlockingDeque<Integer> fifi;
	private int MCR;
	private int MSR;
	private int MOD;
	private int INT;
	private boolean mode;
	private int modem;

	private static final int MSR_TXR = 0x01;
	private static final int MSR_RXR = 0x02;
	private static final int MSR_TXE = 0x04;
	private static final int MSR_PE  = 0x08;
	private static final int MSR_OVR = 0x10;
	private static final int MSR_FE  = 0x20;
	private static final int MSR_SYN = 0x40;
	private static final int MSR_DSR = 0x80;
	private static final int MSR_RXERR = (MSR_FE|MSR_PE|MSR_OVR);
	private static final int MSR_STATIC = (MSR_DSR|MSR_SYN);
	private static final int MSR_CTS = 0x100;	// not SW accessible...

	private static final int MCR_TXE = 0x01;
	private static final int MCR_DTR = 0x02;
	private static final int MCR_RXE = 0x04;
	private static final int MCR_BRK = 0x08;
	private static final int MCR_ERS = 0x10;	// error reset
	private static final int MCR_RTS = 0x20;
	private static final int MCR_RST = 0x40;
	private static final int MCR_HNT = 0x80;

	private Object attObj;
	private PeripheralContainer attPC;
	private OutputStream attFile;
	private InputStream attInFile;
	private SerialDevice io;
	private boolean io_in = false;
	private boolean io_out = false;
	private boolean excl = true;
	private long lastTx = 0;
	private long lastRx = 0;
	private int clock = 154000;	// Hz, both TxC and RxC
	private long nanoBaud = 0; // length of char in nanoseconds
	private int bits; // bits per character

	public INS8251(Properties props, String pfx, int base, int irq,
			Interruptor intr) {
		prefix = pfx;
		name = pfx.toUpperCase() + "_INS8251";
		attObj = null;
		attFile = null;
		attInFile = null;
		this.intr = intr;
		String s;
		// src = intr.registerINT(irq);
		basePort = base;
		// TODO: simulate divider straps?
		s = props.getProperty(pfx + "_clock");
		if (s != null) {
			int i = Integer.valueOf(s);
			if (i > 0 && i <= 154000) {
				clock = i;
			}
		}
		fifo = new java.util.concurrent.LinkedBlockingDeque<Integer>();
		fifi = new java.util.concurrent.LinkedBlockingDeque<Integer>();
		reset();
		s = props.getProperty(pfx + "_att");
		if (s != null && s.length() > 1) {
			if (s.charAt(0) == '>') { // redirect output to file
				attachFile(s.substring(1));
			} else if (s.charAt(0) == '<') {
				attachInFile(s.substring(1));
			} else if (s.charAt(0) == '!') { // pipe to/from program
				attachPipe(s.substring(1));
			} else {
				attachClass(props, s);
			}
		}
	}

	private void attachInFile(String s) {
		String[] args = s.split("\\s");
		try {
			attInFile = new FileInputStream(args[0]);
			MSR |= MSR_RXR;
		} catch (Exception ee) {
			System.err.format("Invalid file in attachment: %s\n", args[0]);
		}
	}

	private void attachFile(String s) {
		String[] args = s.split("\\s");
		setupFile(args, 0);
	}

	private void setupFile(String[] args, int start) {
		boolean append = false;
		for (int x = start + 1; x < args.length; ++x) {
			if (args[x].equals("a")) {
				append = true;
			} else if (args[x].equals("+")) {
				excl = false;
			}
		}
		if (args[start].equalsIgnoreCase("syslog")) {
			attFile = System.err;
			excl = false;
		} else try {
			attFile = new FileOutputStream(args[start], append);
			if (excl) {
				setModem(VirtualUART.SET_CTS | VirtualUART.SET_DSR);
			}
		} catch (Exception ee) {
			System.err.format("Invalid file in attachment: %s\n", args[start]);
			return;
		}
	}

	private void attachPipe(String s) {
		System.err.format("Pipe attachments not yet implemented: %s\n", s);
	}

	private void attachClass(Properties props, String s) {
		String[] args = s.split("\\s");
		for (int x = 1; x < args.length; ++x) {
			if (args[x].startsWith(">")) {
				excl = false;
				args[x] = args[x].substring(1);
				setupFile(args, x);
				// TODO: truncate args so Class doesn't see?
			}
		}
		Vector<String> argv = new Vector<String>(Arrays.asList(args));
		// try to construct from class...
		try {
			Class<?> clazz = Class.forName(args[0]);
			Constructor<?> ctor = clazz.getConstructor(
					Properties.class,
					argv.getClass(),
					VirtualUART.class);
			// funky "new" avoids "argument type mismatch"...
			attObj = ctor.newInstance(
					props,
					argv,
					(VirtualUART)this);
			if (attObj instanceof PeripheralContainer) {
				attPC = (PeripheralContainer)attObj;
			}
		} catch (Exception ee) {
			System.err.format("Invalid class in attachment: %s\n", s);
			return;
		}
	}

	public void attachDevice(SerialDevice io) {
		this.io = io;
		io_in = (io != null && (io.dir() & SerialDevice.DIR_IN) != 0);
		io_out = (io != null && (io.dir() & SerialDevice.DIR_OUT) != 0);
	}

	public JFrame getFrame() {
		if (attPC != null) {
			return attPC.getFrame();
		}
		return null;
	}

	public String getName() {
		if (attPC != null) {
			return attPC.getName();
		}
		return null;
	}

	// Conditions affecting interrupts have changed, ensure proper signal.
	private void chkIntr() {
		// NOTE: DTR enabling interrupts is H8-5 behavior, not INS8251...
		boolean ion = ((MCR & MCR_DTR) != 0 && (MSR & INT) != 0);
		if (ion) {
			intr.raiseINT(irq, src);
		} else {
			intr.lowerINT(irq, src);
		}
	}

	private void updateStatus() {
		long t = System.nanoTime();
		// TODO: factor in RxE/TxE/CTS?
		MSR &= ~MSR_RXR;
		if (attInFile != null) {
			try {
				if (attInFile.available() > 0) {
					MSR |= MSR_RXR;
				}
			} catch (Exception ee) {}
		} else if (io_in) {
			if (io.available() > 0) {
				MSR |= MSR_RXR;
			}
		} else {
			// simulate Rx overrun from neglect...
			while (t - lastRx > 30000000 && fifi.size() > 1) {
				try {
					fifi.take();
				} catch (Exception ee) {}
				MSR |= MSR_OVR;
			}
			if (fifi.size() > 0) {
				MSR |= MSR_RXR;
			}
			lastRx = t;
		}
		if (t - lastTx > nanoBaud) {
			if (attFile != null || io_out) {
				MSR |= MSR_TXR;
				MSR |= MSR_TXE;
				lastTx = t;
			} else if (fifo.size() < 2) {
				MSR |= MSR_TXR;
				if (fifo.size() < 1) {
					MSR |= MSR_TXE;
				} else {
					lastTx = t;
				}
			}
		}
	}

	///////////////////////////////
	/// Interfaces for IODevice ///
	public int in(int port) {
		int off = port - basePort;
		int val = 0;
		switch(off) {
		case 0: // Rx Data
			if ((MCR & MCR_RXE) == 0) {
				break;
			}
			if (attInFile != null) {
				try {
					val = attInFile.read();
					if (val >= 0) {
						break;
					}
				} catch (Exception ee) {}
				MSR &= ~MSR_RXR;
				chkIntr();
				break;
			}
			if (io_in) {
				val = io.read();
				if (val >= 0) {
					break;
				}
				MSR &= ~MSR_RXR;
				chkIntr();
				break;
			}
			synchronized(this) {
				if (fifi.size() > 0) {
					try {
						val = fifi.take();
					} catch (Exception ee) {}
					if (fifi.size() == 0) {
						MSR &= ~MSR_RXR;
						chkIntr();
					}
				}
			}
			break;
		case 1:
			updateStatus();
			val = MSR & 0xff;
			if ((MCR & MCR_RXE) == 0) {
				val &= ~MSR_RXR;
			}
			// TODO: nothing special here for TxEnable?
			break;
		}
		return val;
	}

	public void out(int port, int val) {
		int off = port - basePort;
		val &= 0xff; // necessary?
		switch(off) {
		case 0: // Tx Data
			if (!canTx()) {
				break;
			}
			if (attFile != null) {
				try {
					attFile.write(val);
				} catch (Exception ee) {}
			}
			if (io_out) {
				io.write(val);
			}
			if ((attFile == null && !io_out) || !excl) {
				fifo.add(val);
				lastTx = System.nanoTime();
				MSR &= ~MSR_TXE;
				if (fifo.size() > 1) {
					MSR &= ~MSR_TXR;
					try {
						while (fifo.size() > fifoLimit) {
							fifo.removeFirst();
						}
					} catch (Exception ee) {}
				} else {
					// probably already set
					MSR |= MSR_TXR;
				}
				chkIntr();
			}
			break;
		case 1:
			if (mode) {
				mode = false;
				setMode(val);
			} else {
				MCR = val;
				if ((val & MCR_ERS) != 0) {
					MSR &= ~MSR_RXERR;
				}
				if ((val & MCR_RST) != 0) {
					reset(); // more? less?
				} else {
					changeModem();
				}
			}
			break;
		}
	}

	private void setMode(int val) {
		MOD = val;
		bits = ((val >> 2) & 0x03) + 5 + 1;
		if ((val & 0x80) != 0) {
			++bits;	// 1.5 or 2 stop bits...
		}
		if ((val & 0x10) != 0) {
			++bits;	// parity bit
		}
		int f = 1;
		switch (val & 0x03) {
		case 0:
			// TODO: sync mode...
			break;
		case 1:
			break;
		case 2:
			f = 16;
			break;
		case 3:
			f = 64;
			break;
		}
		int baud = clock / f;
		int cps = baud / bits;
		nanoBaud = (1000000000 / cps);
	}

	public void reset() {
		mode = true;
		MSR &= ~MSR_STATIC;
		MSR |= (MSR_TXR | MSR_TXE);
		MCR = 0;
		changeModem();
		chkIntr();
		fifo.clear();
		fifi.clear();
	}
	public int getBaseAddress() {
		return basePort;
	}
	public int getNumPorts() {
		return 2;
	}

	private void changeModem() {
		if (io == null) {
			return;
		}
		int m = getModem();
		int diff = modem ^ m;
		if (diff == 0) {
			return;
		}
		modem = m;
		io.modemChange(this, modem);
	}

	private boolean canTx() {
		return ((MSR & MSR_CTS) != 0 && (MCR & MCR_TXE) != 0);
	}

	////////////////////////////////////////////////////
	/// Interfaces for the virtual peripheral device ///
	public boolean attach(Object periph) { return false; }
	public void detach() {
		if (attObj == null) {
			return;
		}
		attObj = null;
		excl = true;
		try {
			fifo.addFirst(-1);
		} catch (Exception ee) {
			fifo.add(-1);
		}
	}
	public int available() {
		return fifo.size();
	}

	// Must sleep if nothing available...
	public int take() {
		try {
			int c = fifo.take();
			// Tx always appears empty...
			// But might need to simulate intr.
			// This is separate thread from CPU so must be careful...
			// TBD: MSR |= MSR_TXR; chkIntr();
			return c;
		} catch(Exception ee) {
			return -1;
		}
	}

	public boolean ready() {
		return (MSR & MSR_RXR) == 0;
	}
	// Must NOT sleep
	public synchronized void put(int ch, boolean sleep) {
		// TODO: prevent infinite growth?
		fifi.add(ch & 0xff);
		lastRx = System.nanoTime();
		MSR |= MSR_RXR;
		chkIntr();
	}

	public void setModem(int mdm) {
		int nuw = MSR & ~MSR_DSR;
		if ((mdm & VirtualUART.SET_DSR) != 0) {
			nuw |= MSR_DSR;
		}
		if ((mdm & VirtualUART.SET_CTS) != 0) {
			nuw |= MSR_CTS;
		}
		MSR = nuw;
		// TODO: must make this thread-safe...
		chkIntr();
		changeModem();
	}
	public int getModem() {
		int mdm = 0;
		if ((MCR & MCR_DTR) != 0) {
			mdm |= VirtualUART.GET_DTR;
		}
		if ((MCR & MCR_RTS) != 0) {
			mdm |= VirtualUART.GET_RTS;
		}
		if ((MCR & MCR_TXE) != 0) {
			mdm |= VirtualUART.GET_TXE;
		}
		if ((MSR & MSR_SYN) != 0) {
			mdm |= VirtualUART.GET_SYN;
		}
		if ((MSR & MSR_DSR) != 0) {
			mdm |= VirtualUART.SET_DSR;
		}
		if ((MSR & MSR_CTS) != 0) {
			mdm |= VirtualUART.SET_CTS;
		}
		return mdm;
	}
	public String getPortId() { return prefix; }

	public String getDeviceName() { return name; }

	public String dumpDebug() {
		String ret = new String();
		ret += String.format("port %02x, #fifo = %d, #fifi = %d\n",
			basePort, fifo.size(), fifi.size());
		ret += String.format("MOD = %02x, INT = %02x\n", MOD, INT);
		ret += String.format("clock = %d nanBaud = %d\n", clock, nanoBaud);
		ret += String.format("MCR = %02x, MSR = %02x\n", MCR, MSR);
		if (io != null) {
			ret += io.dumpDebug();
		}
		return ret;
	}
}
