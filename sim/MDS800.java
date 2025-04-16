// Copyright 2025 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;
import java.io.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Properties;

import z80core.*;
import z80debug.*;

public class MDS800 implements Computer, Interruptor, Runnable {
	private I8080 cpu;
	private MDSFrontPanel fp;
	private long clock;
	private Map<Integer, IODevice> ios;
	private Vector<IODevice> devs;
	private Vector<DiskController> dsks;
	private Vector<InterruptController> intrs;
	private Memory mem;
	private boolean running;
	private boolean stopped;
	private Semaphore stopWait;
	private boolean doReset;
	private int[] intRegistry;
	private int[] intLines;
	private int intState;
	private int intMask;
	private Vector<ClockListener> clks;
	private Vector<TimeListener> times;
	private	int cpuSpeed = 2000000;
	private int cpuCycle1ms = 2000;
	private int nanoSecCycle = 500;
	private long backlogNs;
	private CPUTracer trc;
	private ReentrantLock cpuLock;

	public MDS800(Properties props, MDSFrontPanel fp) {
		String s;
		this.fp = fp;
		intRegistry = new int[8];
		intLines = new int[8];
		Arrays.fill(intRegistry, 0);
		Arrays.fill(intLines, 0);
		intState = 0;
		intMask = 0;
		running = false;
		stopped = true;
		stopWait = new Semaphore(0);
		cpuLock = new ReentrantLock();

		cpu = new I8080(this);
		ios = new HashMap<Integer, IODevice>();
		devs = new Vector<IODevice>();
		dsks = new Vector<DiskController>();
		clks = new Vector<ClockListener>();
		times = new Vector<TimeListener>();
		intrs = new Vector<InterruptController>();
		// Do this early, so we can log messages appropriately.
		s = props.getProperty("mds800_log");
		if (s != null) {
			String[] args = s.split("\\s");
			boolean append = false;
			for (int x = 1; x < args.length; ++x) {
				if (args[x].equals("a")) {
					append = true;
				}
			}
			try {
				FileOutputStream fo = new FileOutputStream(args[0], append);
				PrintStream ps = new PrintStream(fo, true);
				System.setErr(ps);
			} catch (Exception ee) {}
		}
		s = props.getProperty("configuration");
		if (s == null) {
			System.err.format("No config file found\n");
		} else {
			System.err.format("Using configuration from %s\n", s);
		}

		mem = new MDSMemory(props, this);

		INS8251 sp;
		sp = new INS8251(props, "tty", 0xf4, 3, this);
		addDevice(sp);
		sp = new INS8251(props, "crt", 0xf6, 3, this);
		addDevice(sp);

		s = props.getProperty("mds800_trace");
		trc = new I8080Tracer(props, "mds800", cpu, mem, s);
	}

	public void reset() {
		boolean wasRunning = running;
		trc.setTrace("off");
		// TODO: reset other interrupt state? devices should do that...
		intMask = 0;
		clock = 0;
		stop();
		cpu.reset();
		mem.reset();
		for (int x = 0; x < devs.size(); ++x) {
			devs.get(x).reset();
		}
		if (wasRunning) {
			start();
		}
	}

	public boolean addDevice(IODevice dev) {
		if (dev == null) {
			System.err.format("NULL I/O device\n");
			return false;
		}
		int base = dev.getBaseAddress();
		int num = dev.getNumPorts();
		if (num < 0) {
			//System.err.format("No ports\n");
			//return false;
			return true;
		}
		for (int x = 0; x < num; ++x) {
			if (ios.get(base + x) != null) {
				System.err.format("Conflicting I/O %02x (%02x)\n", base, num);
				return false;
			}
		}
		devs.add(dev);
		for (int x = 0; x < num; ++x) {
			ios.put(base + x, dev);
		}
		return true;
	}

	public IODevice getDevice(int basePort) {
		IODevice dev = ios.get(basePort);
		return dev;
	}

	public Vector<DiskController> getDiskDevices() {
		return dsks;
	}

	public boolean addDiskDevice(DiskController dev) {
		if (!addDevice(dev)) {
			return false;
		}
		dsks.add(dev);
		return true;
	}

	// These must NOT be called from the thread...
	public void start() {
		stopped = false;
		if (running) {
			return;
		}
		running = true;
		Thread t = new Thread(this);
		t.setPriority(Thread.MAX_PRIORITY);
		t.start();
	}
	public void stop() {
		stopWait.drainPermits();
		if (!running) {
			return;
		}
		running = false;
		// This is safer than spinning, but still stalls the thread...
		try {
			stopWait.acquire();
		} catch (Exception ee) {}
	}
	private void addTicks(int ticks) {
		clock += ticks;
		for (ClockListener lstn : clks) {
			lstn.addTicks(ticks, clock);
		}
		int t = ticks * nanoSecCycle;
		for (TimeListener lstn : times) {
			lstn.addTime(t);
		}
	}

	// I.e. output from the H89 Console Port...
	public InputStream getInputStream() {
		// TODO: single instance
		return (InputStream)new ConsoleInput();
	}

	// I.e. input to the H89 Console Port...
	public OutputStream getOutputStream() {
		// TODO: single instance
		return (OutputStream)new ConsoleOutput();
	}

	// I.e. admin commands to virtual H89...
	public H89Commander getCommander() {
		return (H89Commander)this;
	}

	// TODO: these may be separate classes...

	/////////////////////////////////////////////
	/// Interruptor interface implementation ///
	public boolean bootOn() {
		return fp.bootOn();
	}

	public int registerINT(int irq) {
		int val = intRegistry[irq & 7]++;
		// TODO: check for overflow (32 bits max?)
		return val;
	}
	public synchronized void raiseINT(int irq, int src) {
		irq &= 7;
		intLines[irq] |= (1 << src);
		intState |= (1 << irq);
		if ((intState & ~intMask) != 0) {
			cpu.setINTLine(true);
		}
	}
	public synchronized void lowerINT(int irq, int src) {
		irq &= 7;
		intLines[irq] &= ~(1 << src);
		if (intLines[irq] == 0) {
			intState &= ~(1 << irq);
			if ((intState & ~intMask) == 0) {
				cpu.setINTLine(false);
			}
		}
	}
	public void blockInts(int msk) {
		intMask |= msk;
		if ((intState & ~intMask) == 0) {
			cpu.setINTLine(false);
		}
	}
	public void unblockInts(int msk) {
		intMask &= ~msk;
		if ((intState & ~intMask) != 0) {
			cpu.setINTLine(true);
		}
	}
	public void triggerNMI() {
		cpu.triggerNMI();
	}
	public void triggerRESET() {
		doReset = true;
	}
	// TODO: no longer used...
	public void timeout2ms() {
	}
	public void addClockListener(ClockListener lstn) {
		clks.add(lstn);
	}
	public void addTimeListener(TimeListener lstn) {
		times.add(lstn);
	}
	public void addIntrController(InterruptController ctrl) {
		// There really should be only zero or one.
		intrs.add(ctrl);
	}
	public void waitCPU() {
		// Keep issuing clock cycles while stalling execution.
		addTicks(1);
	}
	public boolean isTracing() {
		return false;
	}
	public void startTracing(int cy) {
	}
	public void stopTracing() {
	}

	/////////////////////////////////////////////
	/// OutputStream interface implementation ///
	class ConsoleOutput extends OutputStream {
		public void write(int b) {
			con.put(b, false);
		}
	}

	////////////////////////////////////////////
	/// InputStream interface implementation ///
	class ConsoleInput extends InputStream {
		public int read() {
			return con.take(); // must sleep
		}
		public int available() {
			return con.available();
		}
	}

	/////////////////////////////////////////////
	/// H89Commander interface implementation ///
	public Vector<String> sendCommand(String cmd) {
		// TODO: stop Z80 during command? Or only pause it?
		String[] args = cmd.split("\\s");
		Vector<String> ret = new Vector<String>();
		ret.add("ok");
		Vector<String> err = new Vector<String>();
		err.add("error");
		if (args.length < 1) {
			return ret;
		}
		if (args[0].equalsIgnoreCase("quit")) {
			// Release Z80, if held...
			stop();
			System.exit(0);
		}
		if (args[0].equalsIgnoreCase("trace") && args.length > 1) {
			if (!traceCommand(args, err, ret)) {
				return err;
			}
			return ret;
		}
		try {
			cpuLock.lock(); // This might sleep waiting for CPU to finish 2mS
			if (args[0].equalsIgnoreCase("reset")) {
				reset();
				return ret;
			}
			if (args[0].equalsIgnoreCase("mount")) {
				if (args.length < 3) {
					err.add("syntax");
					err.add(cmd);
					return err;
				}
				// must special-case Cassette...
				GenericRemovableDrive drv = findDrive(args[1]);
				if (drv == null) {
					err.add("nodrive");
					err.add(args[1]);
					return err;
				}
				drv.insertMedia(Arrays.copyOfRange(args, 2, args.length));
				return ret;
			}
			if (args[0].equalsIgnoreCase("unmount")) {
				if (args.length < 2) {
					err.add("syntax");
					err.add(cmd);
					return err;
				}
				GenericRemovableDrive drv = findDrive(args[1]);
				if (drv == null) {
					err.add("nodrive");
					err.add(args[1]);
					return err;
				}
				drv.insertMedia(null);
				return ret;
			}
			if (args[0].equalsIgnoreCase("getdevs")) {
				for (IODevice dev : devs) {
					String nm = dev.getDeviceName();
					if (nm != null) {
						ret.add(nm);
					}
				}
				return ret;
			}
			if (args[0].equalsIgnoreCase("getdisks")) {
				for (DiskController dev : dsks) {
					Vector<GenericRemovableDrive> drvs = dev.getDiskDrives();
					for (GenericRemovableDrive drv : drvs) {
						if (drv != null) {
							ret.add(drv.getDriveName() + "=" + drv.getMediaName());
						}
					}
				}
				return ret;
			}
			if (args[0].equalsIgnoreCase("dump") && args.length > 1) {
				if (args[1].equalsIgnoreCase("core") && args.length > 2) {
					mem.dumpCore(args[2]);
				}
				if (args[1].equalsIgnoreCase("cpu")) {
					ret.add(cpu.dumpDebug());
					ret.add(trc.disas.disas(cpu.getRegPC()) + "\n");
				}
				if (args[1].equalsIgnoreCase("mem")) {
					ret.add(mem.dumpDebug());
				}
				if (args[1].equalsIgnoreCase("page") && args.length > 2) {
					String s = dumpPage(args);
					if (s == null) {
						err.add("syntax");
						err.addAll(Arrays.asList(args));
						return err;
					}
					ret.add(s);
				}
				if (args[1].equalsIgnoreCase("disas") && args.length > 3) {
					String s = dumpDisas(args);
					if (s == null) {
						err.add("syntax");
						err.addAll(Arrays.asList(args));
						return err;
					}
					ret.add(s);
				}
				if (args[1].equalsIgnoreCase("mach")) {
					ret.add(dumpDebug());
				}
				if (args[1].equalsIgnoreCase("disk") && args.length > 2) {
					IODevice dev = findDevice(args[2]);
					if (dev == null) {
						err.add("nodevice");
						err.add(args[2]);
						return err;
					}
					ret.add(dev.dumpDebug());
				}
				return ret;
			}
			err.add("badcmd");
			err.add(cmd);
			return err;
		} finally {
			cpuLock.unlock();
		}
	}

	private boolean traceCommand(String[] args, Vector<String> err,
			Vector<String> ret) {
		// TODO: do some level of mutexing?
		if (args[1].equalsIgnoreCase("on")) {
			trc.setTrace(":");
		} else if (args[1].equalsIgnoreCase("off")) {
			trc.setTrace("off");
		} else if (args[1].equalsIgnoreCase("cycles") && args.length > 2) {
			trc.setTrace(". " + args[2]);
		} else if (args[1].equalsIgnoreCase("pc") && args.length > 2) {
			// TODO: this could be a nasty race condition...
			if (args.length > 3) {
				trc.setTrace(args[2] + ":" + args[3]);
			} else {
				trc.setTrace(args[2] + ":");
			}
		} else {
			err.add("unsupported:");
			err.add(args[1]);
			return false;
		}
		return true;
	}

	private GenericRemovableDrive findDrive(String name) {
		for (DiskController dev : dsks) {
			GenericRemovableDrive drv = dev.findDrive(name);
			if (drv != null) {
				return drv;
			}
		}
		return null;
	}

	private IODevice findDevice(String name) {
		for (IODevice dev : devs) {
			if (name.equals(dev.getDeviceName())) {
				return dev;
			}
		}
		return null;
	}

	public void setSpeed(int spd) {
	}

	/////////////////////////////////////////
	/// Computer interface implementation ///

	public int peek8(int address) {
		int val = mem.read(address);
		return val;
	}
	public void poke8(int address, int value) {
		mem.write(address, value);
	}

	// fetch Interrupt Response byte, IM0 (instruction bytes) or IM2 (vector).
	// Implementation must keep track of multi-byte instruction sequence,
	// and other possible state. For IM0, Z80 will call as long as 'intrFetch' is true.
	public int intrResp(Z80State.IntMode mode) {
		if (mode != Z80State.IntMode.IM0) {
			// H89 cannot operate in IM2.(?)  IM1 should never call this.
			return 0; // TODO: What to return in this case?
		}
		int opCode = -1;
		for (InterruptController ctrl : intrs) {
			opCode = ctrl.readDataBus();
			if (opCode >= 0) {
				return opCode;
			}
		}
		// If no other hardware claims intr ack, use H89 hardware.
		// This will always be a single-byte RST instruction.
		int irq = Integer.numberOfTrailingZeros(intState & ~intMask);
		if (irq > 7) {
			// what to do? this is NOT the right action...
			irq = 7;
		}
		// Construct RST instruction form irq.
		opCode = 0xc7 | (irq << 3);
		// TODO: prevent accidental subsequent calls?
		return opCode;
	}

	public void retIntr(int opCode) {
	}

	public int inPort(int port) {
		int val = 0;
		port &= 0xff;
		IODevice dev = ios.get(port);
		if (dev == null) {
			//System.err.format("Undefined Input on port %02x\n", port);
		} else {
			val = dev.in(port);
		}
		return val;
	}
	public void outPort(int port, int value) {
		port &= 0xff;
		IODevice dev = ios.get(port);
		if (dev == null) {
			//System.err.format("Undefined Output on port %02x value %02x\n", port, value);
		} else {
			dev.out(port, value);
		}
	}

	// No longer used...
	public void contendedStates(int address, int tstates) {
		addTicks(tstates);
	}
	// not used?
	public long getTStates() {
		return clock;
	}

	public void breakpoint() {
	}
	public void execDone() {
	}

	public boolean intEnabled() {
		return cpu.isIE();
	}

	public boolean isRunning() {
		return !stopped;
	}

	public void changeSpeed(int mlt, int div) {
		// Z180 only.
		// TODO: anything to do?
		// int spd = (sysSpeed * mlt) / div;
		// setSpeed(spd);
		// ...notifications?
	}

	//////// Runnable /////////
	public void run() {
		String xtra = null;
		int clk = 0;
		int limit = 0;
		while (running) {
			cpuLock.lock(); // This might sleep waiting for GUI command...
			limit += cpuCycle1ms;
			long t0 = System.nanoTime();
			int traced = 0; // assuming any tracing cancels 2mS accounting
			while (running && limit > 0) {
				int PC = cpu.getRegPC();
				boolean trace = trc.preTrace(PC, clock);
				if (trace) {
					++traced;
					xtra = String.format("<%02x/%02x>%s",
						intState, intMask,
						cpu.isINTLine() ? " INT" : "");
				}
				doReset = false;
				clk = cpu.execute();
				if (trace) {
					trc.postTrace(PC, clk, xtra);
				}
				if (doReset) {
					break; // while still "running"
				}
				if (clk < 0) {
					clk = -clk;
				}
				limit -= clk;
				addTicks(clk);
			}
			cpuLock.unlock();
			if (!running) {
				break;
			}
			if (doReset) {
				running = false;
				reset();
				running = true;
				continue;
			}
			long t1 = System.nanoTime();
			if (traced == 0) {
				backlogNs += (1000000 - (t1 - t0));
				t0 = t1;
				if (backlogNs > 10000000) {
					try {
						Thread.sleep(10);
					} catch (Exception ee) {}
					t1 = System.nanoTime();
					backlogNs -= (t1 - t0);
				}
			}
			t0 = t1;
			// TODO: trigger 1mS interrupt/status
		}
		stopped = true;
		stopWait.release();
	}

	public String dumpPage(String[] args) {
		String str = "";
		int pg = 0;
		int bnk = -1;
		int i = 2;
		boolean rom = false;
		if (args[i].equalsIgnoreCase("rom")) {
			rom = true;
			++i;
		}
		if (args.length - i > 1) {
			try {
				bnk = Integer.valueOf(args[i++]);
			} catch (Exception ee) {
				return ee.getMessage();
			}
		}
		if (args.length - i < 1) {
			return null;
		}
		try {
			pg = Integer.valueOf(args[i++], 16);
		} catch (Exception ee) {
			return ee.getMessage();
		}
		int adr = pg << 8;
		int end = adr + 0x0100;
		while (adr < end) {
			if (bnk < 0) {
				str += String.format("%04x:", adr);
			} else {
				str += String.format("%d %04x:", bnk, adr);
			}
			for (int x = 0; x < 16; ++x) {
				int c;
				if (bnk < 0) {
					c = mem.read(adr + x);
				} else {
					c = mem.read(rom, bnk, adr + x);
				}
				str += String.format(" %02x", c);
			}
			str += "  ";
			for (int x = 0; x < 16; ++x) {
				int c;
				if (bnk < 0) {
					c = mem.read(adr + x);
				} else {
					c = mem.read(rom, bnk, adr + x);
				}
				if (c < ' ' || c > '~') {
					c = '.';
				}
				str += String.format("%c", (char)c);
			}
			str += '\n';
			adr += 16;
		}
		return str;
	}

	public String dumpDisas(String[] args) {
		int lo = 0;
		int hi = 0;
		String ret = "";
		boolean rom = false;
		int bnk = -1;
		int i = 2;
		if (args[i].equalsIgnoreCase("rom")) {
			rom = true;
			++i;
		}
		if (args.length - i > 2) {
			try {
				bnk = Integer.valueOf(args[i++]);
			} catch (Exception ee) {
				return ee.getMessage();
			}
		}
		try {
			lo = Integer.valueOf(args[i++], 16);
			hi = Integer.valueOf(args[i++], 16);
		} catch (Exception ee) {
			return ee.getMessage();
		}
		for (int a = lo; a < hi;) {
			String d;
			if (bnk < 0) {
				ret += String.format("%04X:", a);
				d = trc.disas.disas(a);
			} else {
				ret += String.format("%d %04X:", bnk, a);
				d = trc.disas.disas(rom, bnk, a);
			}
			int n = trc.disas.instrLen();
			for (int x = 0; x < n; ++x) {
				int b;
				if (bnk < 0) {
					b = mem.read(a + x);
				} else {
					b = mem.read(rom, bnk, a + x);
				}
				ret += String.format(" %02X", b);
			}
			a += n;
			while (n < 4) {
				ret += "   ";
				++n;
			}
			ret += ' ';
			ret += d;
			ret += '\n';
		}
		return ret;
	}

	public String dumpDebug() {
		String ret = "";
		ret += String.format("System Clock %d Hz\n", cpuSpeed);
		ret += String.format("CLK %d", getTStates());
		if (running) {
			ret += " RUN";
		}
		if (stopped) {
			ret += " STOP";
		}
		if (!running && !stopped) {
			ret += " limbo";
		}
		ret += "\n";
		ret += String.format("2mS Backlog = %d nS\n", backlogNs);
		ret += "INT = {";
		for (int x = 0; x < 8; ++x) {
			ret += String.format(" %x", intLines[x]);
		}
		ret += String.format(" } %02x %02x\n", intState, intMask);
		return ret;
	}
}
