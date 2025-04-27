// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Vector;
import java.util.Properties;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;
import java.util.concurrent.Semaphore;

// TODO: add GUI...
public class MDS_FDC extends RackUnit implements DiskController, PowerListener,
				ActionListener, MouseListener, Runnable {
	// iopb:
	//	db	ctrl: xxDDxCCC C=command, D=unit
	//	...
	//
	//	out port+1 set low addr
	//	out port+2 set high addr and start
	//	in port+0 get status (ready=xxxxx1xx)
	//	in port+1 get result type
	//	in port+3 get result byte

	
	private static final int STS_RDY3 = 0x40;
	private static final int STS_RDY2 = 0x20;
	private static final int STS_DD   = 0x10;	// DD present
	private static final int STS_CTRL = 0x08;	// controller present
	private static final int STS_DONE = 0x04;	// "interrupt pending" - transient
	private static final int STS_RDY1 = 0x02;
	private static final int STS_RDY0 = 0x01;

	// first byte of iopb:
	private static final int CH_RND	= 0x40;	// random sector table for FORMAT
	private static final int CH_INT	= 0x30;	// 00 = interrupt CPU, 01 = disabled
	private static final int CH_16B	= 0x08;	// 16-bit bus/data...

	private static final int MSK_CMD	= 0x07;
	private static final int MSK_16B	= 0x08;	// 16-bit bus/data...
	private static final int MSK_UNIT	= 0x30;
	private static final int SHF_UNIT	= 4;
	private static final int MSK_SEC	= 0x1f; // oddity in ctrlr:
							// newer supt 52 sectors (0x3f)
	private static final int MSK_SEC_DBK	= 0x20; // "disk bank" in DRI CP/M
	private static final int MSK_TRK	= 0x7f; // needed?

	private static final int CMD_NONE	= 0;
	private static final int CMD_SEEK	= 1;
	private static final int CMD_FORMAT	= 2;
	
	private static final int CMD_RESTORE	= 3;
	private static final int CMD_READ	= 4;
	private static final int CMD_VERIFY	= 5;	// verify CRC
	private static final int CMD_WRITE	= 6;
	private static final int CMD_WRDD	= 7;	// write deleted data mark

	// error bits after 0b00 result type
	private static final int RBYTE_DD = 0x01; // deleted data
	private static final int RBYTE_CE = 0x02; // CRC error
	private static final int RBYTE_SE = 0x04; // seek error
	private static final int RBYTE_AE = 0x08; // adr error
	private static final int RBYTE_DE = 0x10; // data over/under run
	private static final int RBYTE_WP = 0x20; // write protect
	private static final int RBYTE_WE = 0x40; // write error
	private static final int RBYTE_NRDY = 0x80; // not ready

	// ready bits after 0b10 result type
	private static final int RBYTE_RDY1 = 0x80;
	private static final int RBYTE_RDY0 = 0x40;
	private static final int RBYTE_RDY3 = 0x20;
	private static final int RBYTE_RDY2 = 0x10;

	private Font tiny;

	private Interruptor intr;
	private MDSMemory mem;
	private int irq;
	private int src;
	private int basePort;
	private String name = null;

	private int status;
	private int command;
	private int unit;
	private int track;
	private int head;
	private int sector;
	private int multi;	// sector count
	private int[] sectorLen;
	private int[] currTrack;
	private boolean intrEnable;
	private boolean selectErr;
	private boolean protect;
	private boolean error;
	private boolean active;
	private GenericFloppyDrive[] units;
	private GenericFloppyDrive curr;
	private LED[] leds;
	private boolean pwrOn;
	private javax.swing.Timer timer;
	private FloppyDrive[] fds;
	private JCheckBox prot;
	private boolean dirty;
	private int dataIdx;
	private byte[] curBuf;
	private Semaphore cmdWait;
	private int state;
	private static final int DONE = 0;
	private static final int IOPB = 1;
	private static final int DATA = 2;

	private int iopbAdr;
	private int resType;
	private int resByte;
	private int resByte10; // if resType=0b10
	private int fdcStat;

	private int iopbOp;
	private int iopbCmd;
	private int iopbSC;
	private int iopbTrk;
	private int iopbSec;
	private int iopbDma;

	static final Color norm = RackUnit.BG;
	static final Color actv = RackUnit.BG.brighter();
	static final Color normHi = actv.brighter();
	static final Color normLo = Color.black;
	private ActionListener lstr;
	static final String[] sufx = { "logdisk" };
	static final String[] sufd = { "Floppy Image" };
	File last = null;

	class FloppyDrive extends JPanel implements MouseListener {
		int id;
		ActionListener lstr;

		public FloppyDrive(LED led, String name, int id, ActionListener lstr) {
			super();
			this.lstr = lstr;
			this.id = id;
			String label = String.format("DRIVE %d", id);
			setOpaque(true);
			setBackground(norm);
			setPreferredSize(new Dimension(350, 80));
			setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED,
				normHi, normHi, normLo, normLo));
			GridBagLayout gb = new GridBagLayout();
			setLayout(gb);
			GridBagConstraints gc = new GridBagConstraints();
			gc.fill = GridBagConstraints.NONE;
			gc.gridx = 0;
			gc.gridy = 0;
			gc.weightx = 0;
			gc.weighty = 0;
			gc.gridwidth = 1;
			gc.gridheight = 1;
			gc.anchor = GridBagConstraints.CENTER;
			JPanel pan = new JPanel();
			pan.setPreferredSize(new Dimension(90, 40));
			pan.setOpaque(false);
			gb.setConstraints(pan, gc);
			add(pan);
			++gc.gridx;
			gc.anchor = GridBagConstraints.SOUTH;
			gc.insets.bottom = 5;
			JLabel lb = new JLabel(label);
			lb.setForeground(Color.white);
			lb.setFont(tiny);
			gb.setConstraints(lb, gc);
			add(lb);
			++gc.gridy;
			gc.insets.bottom = 0;
			gc.anchor = GridBagConstraints.CENTER;
			gb.setConstraints(led, gc);
			add(led);
			++gc.gridx;
			pan = new JPanel();
			pan.setPreferredSize(new Dimension(90, 20));
			pan.setOpaque(false);
			gb.setConstraints(pan, gc);
			add(pan);
			addMouseListener(this);
		}

		public int getId() { return id; }

		public void mousePressed(MouseEvent e) { }
		public void mouseReleased(MouseEvent e) { }
		public void mouseEntered(MouseEvent e) {
			if (lstr != null) {
				setBackground(actv);
			}
		}
		public void mouseExited(MouseEvent e) {
			if (lstr != null) {
				setBackground(norm);
			}
		}
		public void mouseClicked(MouseEvent e) {
			if (lstr != null) {
				lstr.actionPerformed(new ActionEvent(this, e.getID(), "fd"));
			}
		}
	}

	private void FDCctrl(Properties props, String lbl, int base, int irq,
						MDSMemory mem, Interruptor intr) {
		this.mem = mem;
		units = new GenericFloppyDrive[4];
		leds = new LED[4];
		fds = new FloppyDrive[4];
		sectorLen = new int[4];
		currTrack = new int[4];
		prot = new JCheckBox("Protect");
		timer = new javax.swing.Timer(1000, this);
		curBuf = new byte[128];
		command = CMD_NONE;
		name = "MDS_FDC";	// TODO: allow more than one?
		this.intr = intr;
		String s = props.getProperty("fdc_intr");
		if (s != null) {
			int i = Integer.valueOf(s);
			if (i >= 3 && i <= 15) {
				irq = i;
			}
		}
		this.irq = irq;
		basePort = base;
		src = intr.registerINT(irq);
		last = new File(System.getProperty("user.dir"));
		FDCextn(props, lbl, 0, 2);
	}

	private void FDCextn(Properties props, String lbl, int s, int e) {
		for (int x = s; x < e; ++x) {
			String n = String.format("%d", x);
			leds[x] = new RoundLED(LED.Colors.RED);
			units[x] = GenericFloppyDrive.getInstance("FDD_8_SS", n);
			fds[x] = new FloppyDrive(leds[x], lbl, x, this);
			fds[x].setToolTipText("(no disk)");
			String prop = String.format("fdc_disk%d", x + 1);
			String t = props.getProperty(prop);
			if (t == null) continue;
			Vector<String> args = new Vector<String>(Arrays.asList(t.split("\\s")));
			File f = new File(args.get(0));
			mountDisk(x, f, args);
		}
	}

	private void FDCpanel(JPanel pnl, int s, int e) {
		GridBagLayout gb = new GridBagLayout();
		pnl.setLayout(gb);
		GridBagConstraints gc = new GridBagConstraints();
		//pnl.setOpaque(false);
		gc.fill = GridBagConstraints.NONE;
		gc.gridx = 0;
		gc.gridy = 0;
		gc.weightx = 0;
		gc.weighty = 0;
		gc.gridwidth = 1;
		gc.gridheight = 1;
		gc.anchor = GridBagConstraints.CENTER;
		gc.gridx = 1;
		gc.gridy = 1;
		gb.setConstraints(fds[s + 1], gc);
		pnl.add(fds[s + 1]);
		++gc.gridx;
		JPanel pan = new JPanel();
		pan.setOpaque(false);
		pan.setPreferredSize(new Dimension(60, 20));
		gb.setConstraints(pan, gc);
		pnl.add(pan);
		++gc.gridx;
		gb.setConstraints(fds[s], gc);
		pnl.add(fds[s]);
		gc.gridx = 1;
		gc.gridy = 2;
		gc.gridwidth = 3;
		pan = new JPanel();
		pan.setOpaque(false);
		pan.setPreferredSize(new Dimension(440, 30));
		pan.add(new JLabel("MDS Floppy Drive"));
		gb.setConstraints(pan, gc);
		pnl.add(pan);
		++gc.gridy;
	}

	private void setLabel(JPanel pnl, GridBagLayout gb,
				GridBagConstraints gc, int w, String lab) {
		JLabel lbl = new JLabel(lab);
		lbl.setForeground(Color.white);
		lbl.setFont(tiny);
		JPanel pan = new JPanel();
		pan.setOpaque(false);
		pan.add(lbl);
		pan.setPreferredSize(new Dimension(w, 20));
		gb.setConstraints(pan, gc);
		pnl.add(pan);
		++gc.gridx;
	}

	// Main rack, controller and 2 drives
	public MDS_FDC(Properties props, String label, int base, int irq,
					MDSMemory mem, Interruptor intr, MDS800 mds) {
		// TODO: handle LUN...
		super(4);	// 4U height
		setBackground(RackUnit.BLUE);
		cmdWait = new Semaphore(0);
		tiny = new Font("Sans-serif", Font.PLAIN, 8);
		FDCctrl(props, label, base, irq, mem, intr);
		FDCpanel(this, 0, 2);
		// power is off initially...
		pwrOn = false;
		mds.addPowerListener(this);
		reset();
		Thread t = new Thread(this);
		t.start();
	}

	private File pickFile(String purpose) {
		File file = null;
		prot.setSelected(false);
		SuffFileChooser ch = new SuffFileChooser(purpose, sufx, sufd, last, prot);
		int rv = ch.showDialog(this);
		if (rv == JFileChooser.APPROVE_OPTION) {
			file = ch.getSelectedFile();
		}
		return file;
	}

	private void setReady(int x, boolean rdy) {
		int res = 0;
		int sts = 0;

		switch (x) {
		case 0:
			sts = STS_RDY0;
			res = RBYTE_RDY0;
			break;
		case 1:
			sts = STS_RDY1;
			res = RBYTE_RDY1;
			break;
		case 2:
			sts = STS_RDY2;
			res = RBYTE_RDY2;
			break;
		case 3:
			sts = STS_RDY3;
			res = RBYTE_RDY3;
			break;
		}
		if (rdy) {
			fdcStat |= sts;
			resByte10 |= res;
		} else {
			fdcStat &= ~sts;
			resByte10 &= ~res;
		}
	}

	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) { }
	public void mouseReleased(MouseEvent e) { }

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == timer) {
			timer.removeActionListener(this);
			if (unit >= 0) {
				leds[unit].set(false);
			}
			return;
		}
		FloppyDrive fd = (FloppyDrive)e.getSource();
		int x = fd.getId();
		if (units[x] == null) {
			return; // can't happen?
		}
		fds[x].setToolTipText("(no disk)");
		File f = pickFile(String.format("Mount FD%d", x));
		if (f == null) {
			units[x].insertDisk(null);
			setReady(x, false);
			return;
		}
		Vector<String> args = new Vector<String>();
		args.add(f.getAbsolutePath());
		if (prot.isSelected()) {
			args.add("ro");
		} else {
			args.add("rw");
		}
		mountDisk(x, f, args);
	}

	private void mountDisk(int x, File f, Vector<String> args) {
		fds[x].setToolTipText(f.getName());
		units[x].insertDisk(SectorFloppyImage.getDiskette(units[x], args));
		setReady(x, units[x].isReady());
	}

	// Conditions affecting interrupts have changed, ensure proper signal.
	private void chkIntr() {
		if (!intrEnable) {
			return;
		}
//		if ((status & bit(CRU_INTR)) != 0) {
//			intr.raiseINT(irq, src);
//		} else {
//			intr.lowerINT(irq, src);
//		}
	}

	private void abort() {
		if (!active) {
			return;
		}
		active = false;
		// must have been an I/O command...
		if (flushBuf() < 0) {
			cmdError(RBYTE_DE);
		} else {
			cmdComplete();
		}
		dataIdx = 128;
		chkIntr();
	}

	// Called at the start of a command.
	// also in reset() w/-1 for power off.
	private void selectDrive(int u) {
		selectErr = false;
		protect = false;
		// TODO: is there such thing as "none"?
		if (unit >= 0) {
			leds[unit].set(false);
			timer.removeActionListener(this);
		}
//		status &= ~bit(CRU_DRV_NRDY);
//		status &= ~bit(CRU_WR_PROT);
//		status &= ~MSK_ST_UNIT;
//		if (u >= 0) {
//			status |= (u << CRU_ST_UNIT0);
//		}
		if (u >= 0 && units[u] != null) {
			unit = u;
			curr = units[u];
			leds[u].set(true);
			timer.addActionListener(this);
			timer.restart();
			if (!curr.isReady()) {
				selectErr = true;
				cmdErrNR();
			}
			if (curr.isWriteProtect()) {
				protect = true;
			}
		} else {
			timer.removeActionListener(this);
			selectErr = true;
			unit = -1;
			curr = null;
		}
	}

	private void cmdErrNR() {
		error = true;
		resType = 0b10;
		fdcStat |= STS_DONE;
		state = DONE;
		active = false;
	}

	private void cmdError(int bits) {
		error = true;
		resType = 0b00;
		resByte |= bits;
		fdcStat |= STS_DONE;
		state = DONE;
		active = false;
	}

	private void cmdComplete() {
		fdcStat |= STS_DONE;
		state = DONE;
		active = false;
	}

	private int nextSector() {
		++sector;
		if (sector > 26) {
			sector = 1;
			++track;
			if (track > 76) {
				cmdError(RBYTE_SE);
				return -1;
			}
		}
		return 0;
	}

	private void cmdXfer(boolean write) {
		if (write) {
			curBuf[dataIdx++] = (byte)mem.read(iopbDma++);
			dirty = true;
			if (dataIdx >= 128) {
				if (flushBuf() != 0) {
					cmdError(RBYTE_WE);
					return;
				}
				if (--multi == 0) {
					cmdComplete();
					return;
				}
				if (nextSector() != 0) {
					return;
				}
			}
		} else {
			// TODO: mutex?
			mem.write(iopbDma++, curBuf[dataIdx++]);
			if (dataIdx >= 128) {
				if (--multi == 0) {
					cmdComplete();
					return;
				}
				if (nextSector() != 0) {
					return;
				}
				if (fillBuf() < 0) {
					cmdError(RBYTE_DE);
					return;
				}
			}
		}
	}

	private void restoreDrive() {
		while (!curr.getTrackZero()) {
			curr.step(false);
		}
		currTrack[unit] = 0;
	}

	// TODO: return error ever?
	private void seekTrack(int track) {
		if (track == 0 || currTrack[unit] < 0) {
			restoreDrive();
		}
		while (currTrack[unit] < track) {
			curr.step(true);
			++currTrack[unit];
		}
		while (currTrack[unit] > track) {
			curr.step(false);
			--currTrack[unit];
		}
	}

	private void doCommand() {
		// TODO: pass this off to a thread...
		fdcStat &= ~STS_DONE;
		resType = 0;
		resByte = 0;
		state = IOPB;
		active = true;
		cmdWait.release();
	}

	private void getIopb() {
		iopbOp = mem.read(iopbAdr);
		iopbCmd = mem.read(iopbAdr + 1);
		iopbSC = mem.read(iopbAdr + 2);
		iopbTrk = mem.read(iopbAdr + 3);
		iopbSec = mem.read(iopbAdr + 4);
		iopbDma = mem.read(iopbAdr + 5) |
			(mem.read(iopbAdr + 6) << 8);
		command = iopbCmd;
		error = false;
		// This appears to only affect setting of STS_DONE,
		// unclear how to poll if DONE is never set.
		// actual CPU interrupt is under control of host.
		//if ((iopbOp & CH_INT) == 0) {
		//}
		int u = ((iopbCmd & MSK_UNIT) >> SHF_UNIT);
		if (u == 3) u = 1;	// CP/M quirk...
		selectDrive(u);
		if (curr == null || !curr.isReady()) {
			selectErr = true;
			cmdErrNR();
			return;
		}

		int c = (iopbCmd & MSK_CMD);
		if (c == CMD_RESTORE) {
			// TODO: set unit track to 0?
			cmdComplete();
			return;
		}
		multi = iopbSC;
		head = 0; // always SS for now
		sector = iopbSec & MSK_SEC;
		// TODO: might need "disk bank" (iopbSec & MSK_SEC_DBK)
		track = iopbTrk & MSK_TRK;
		if (sector == 0 || sector > 26) {
			cmdError(RBYTE_AE);
			return;
		}
		if (track > 76) {
			cmdError(RBYTE_SE);
			return;
		}
		seekTrack(track);
		switch (c) {
		case CMD_READ:
			if (fillBuf() < 0) {
				cmdError(RBYTE_DE);
				break;
			}
			state = DATA;
			break;
		case CMD_WRITE:
			if (protect) {
				cmdError(RBYTE_WP);
				break;
			}
			dataIdx = 0;
			state = DATA;
			break;
		default:
			cmdError(RBYTE_SE);
			break;
		}
		if (multi == 0) {
			cmdComplete();
		}
// already done in selectDrive() ?
//		if (active) {
//			timer.removeActionListener(this);
//			timer.addActionListener(this);
//			timer.restart();
//			leds[unit].set(true);
//		}
	}

	private void doData() {
		int n;
		int c = (iopbCmd & MSK_CMD);
		switch (c) {
		case CMD_READ:
			cmdXfer(false);
			break;
		case CMD_WRITE:
			cmdXfer(true);
			break;
		}
		chkIntr();
	}

	private int fillBuf() {
		if (curr == null) {
			return -1;
		}
		dataIdx = 0;
		if (curr.readData(0, track, head, sector, -1) !=
					GenericFloppyFormat.DATA_AM) {
			return -1;
		}
		for (int x = 0; x < 128; ++x) { // SD 128B/sec for now
			int data = curr.readData(0, track, head, sector, x);
			if (data < 0) {
				return -1;
			}
			curBuf[x] = (byte)data;
		}
		return 0;
	}

	private int flushBuf() {
		if (curr == null) {
			return -1;
		}
		if (!dirty) {
			return 0;
		}
		// FD800 write command described as doing zero-fill on short sectors
		while (dataIdx < 128) {
			curBuf[dataIdx++] = (byte)0;
		}
		// TODO: what happens to short sectors?
		//for (int x = sectorLen[unit]; x < 128; ++x) {
		//	curBuf[x] = 0;
		//}
		if (curr.writeData(0, track, head, sector, -1, 0, true) !=
					GenericFloppyFormat.DATA_AM) {
			return -1;
		}
		for (int x = 0; x < 128; ++x) {
			int err = curr.writeData(0, track, head, sector,
						x, curBuf[x] & 0xff, true);
			if (err < 0 && err != GenericFloppyFormat.CRC) {
				return -1;
			}
		}
		dataIdx = 0;
		dirty = false;
		return 0;
	}

	public void reset() {
		// if commands are asynch, must abort any here...
		cmdWait.drainPermits();
		abort();
		selectDrive(pwrOn ? 0 : -1);
		command = 0;
		dirty = false;
		Arrays.fill(sectorLen, 128);
		Arrays.fill(currTrack, -1);
		intrEnable = false;
		intr.lowerINT(irq, src);
		fdcStat |= STS_CTRL;	// always present
	}

	public int getBaseAddress() { return basePort; }

	public int getNumPorts() { return 8; }

	public int in(int port) {
		int off = port - getBaseAddress();
		int val = 0;
		switch (off) {
		case 0:
			val = fdcStat;
			break;
		case 1:	// result type
			// ------xx meaning:
			// 00 = unlinked i/o complete, 01 = linked i/o complete (not used)
			// 10 = disk status changed, 11 = (not used)
			val = resType;
			fdcStat &= ~STS_DONE;
			break;
		case 2:
			break;
		case 3:	// result byte
			if (resType == 0b10) {
				val = resByte10;
			} else {
				val = resByte;
			}
			break;
		}
		return val;
	}

	public void out(int port, int value) {
		int off = port - getBaseAddress();
		switch (off) {
		case 0:
			break;
		case 1:
			iopbAdr = value & 0xff;
			break;
		case 2:
			iopbAdr |= ((value & 0xff) << 8);
			doCommand();
			break;
		case 3:
			break;
		case 7:
			// TODO: reset controller
			break;
		}
	}

	public String getDeviceName() { return name; }

	public String dumpDebug() {
		String ret = new String();
		ret += String.format("FDC base %02x sts=%02x rtyp=%02x rbyt=%02x %02x\n",
			basePort, fdcStat, resType, resByte, resByte10);
		ret += String.format("track %d, head = %d, sector %d, unit %d\n",
			track, head, sector, unit);
		ret += String.format("dataIdx %d IntrEnable=%s\n", dataIdx, intrEnable);
		ret += String.format("last cmd %04x: %02x %02x %02x %02x %02x %04x\n",
			iopbAdr, iopbOp, iopbCmd, iopbSC, iopbTrk, iopbSec, iopbDma);
		return ret;
	}

	// PowerListener
	public void setPower(boolean on) {
		pwrOn = on;
		reset();
	}

	public GenericRemovableDrive findDrive(String name) {
		return null;
	}

	public Vector<GenericRemovableDrive> getDiskDrives() {
		return new Vector<GenericRemovableDrive>();
	}

	public void run() {
		while (true) {
			if (!pwrOn) {
				try { Thread.sleep(100); } catch (Exception e) {}
				continue;
			}
			try {
				cmdWait.acquire();
			} catch (Exception e) {
				e.printStackTrace();
				break;
			}
			while (pwrOn && state != DONE) {
				switch (state) {
				case IOPB:
					getIopb();
					break;
				case DATA:
					doData();
					break;
				}
			}
		}
	}
}
