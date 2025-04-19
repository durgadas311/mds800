// Copyright (c) 2018 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Vector;
import java.util.Properties;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;

// TODO: add GUI...
public class MDS_FDC extends RackUnit implements DiskController,
					ActionListener, MouseListener {
	// iopb:
	//	db	ctrl: xxDDxCCC C=command, D=unit
	//	...
	//
	//	out port+1 set low addr
	//	out port+2 set high addr and start
	//	in port+0 get status (ready=xxxxx1xx)
	//	in port+1 get result type
	//	in port+3 get result byte

	
	private static final int STS_DONE = 0x04;
	private static final int STS_RDY0 = 0x01;
	private static final int STS_RDY1 = 0x02;

	private static final int MSK_CMD	= 0x07;
	private static final int MSK_UNIT	= 0x30;
	private static final int SHF_UNIT	= 4;

	private static final int CMD_RESTORE	= 3;
	private static final int CMD_READ	= 4;
	private static final int CMD_WRITE	= 6;

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
	private int[] sectorLen;
	private int[] currTrack;
	private boolean intrEnable;
	private boolean selectErr;
	private boolean protect;
	private boolean error;
	private boolean active;
	private boolean multi;
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

	private int iopbAdr;
	private int resType;
	private int resByte;
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
			if (name == null) {
				name = String.format("FD%d", id);
			} else {
				name = String.format("%s%d", name, id + 1);
			}
			setOpaque(true);
			setBackground(norm);
			setPreferredSize(new Dimension(220, 80));
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
			JLabel lb = new JLabel(name);
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
		timer = new javax.swing.Timer(500, this);
		curBuf = new byte[128];
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
		pnl.setOpaque(false);
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
		gb.setConstraints(fds[s], gc);
		pnl.add(fds[s]);
		++gc.gridx;
		JPanel pan = new JPanel();
		pan.setOpaque(false);
		pan.setPreferredSize(new Dimension(30, 20));
		gb.setConstraints(pan, gc);
		pnl.add(pan);
		++gc.gridx;
		gb.setConstraints(fds[s + 1], gc);
		pnl.add(fds[s + 1]);
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
	public MDS_FDC(Properties props, String label, int base, int irq, int lun,
						MDSMemory mem, Interruptor intr) {
		// TODO: handle LUN...
		super(4);	// 4U height
		tiny = new Font("Sans-serif", Font.PLAIN, 8);
		FDCctrl(props, label, base, irq, mem, intr);
		FDCpanel(this, 0, 2);
		// power is off initially...
		pwrOn = false;
		reset();
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

	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) { }
	public void mouseReleased(MouseEvent e) { }

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == timer) {
			timer.removeActionListener(this);
			if (unit >= 0) {
				//hld[unit].set(false);
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
		// must have been and I/O command...
//		if (flushBuf() < 0) {
//			cmdError(bit(CRU_DAT_ERR));
//		} else {
//			cmdComplete();
//		}
		dataIdx = 128;
		chkIntr();
	}

	private void selectDrive(int u) {
		selectErr = false;
		protect = false;
		// TODO: is there such thing as "none"?
		if (unit >= 0) {
			leds[unit].set(false);
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
			if (!curr.isReady()) {
				selectErr = true;
//				status |= bit(CRU_DRV_NRDY);
			}
			if (curr.isWriteProtect()) {
				protect = true;
			}
		} else {
			timer.removeActionListener(this);
			selectErr = true;
//			status |= bit(CRU_DRV_NRDY);
			unit = -1;
			curr = null;
		}
	}

	private void cmdError(int bits) {
		error = true;
//		status &= ~bit(CRU_XFER_RDY);
//		status &= ~bit(CRU_CTRL_BSY);
		status |= bits;
//		status |= bit(CRU_INTR);
	}

	private void cmdXfer() {
//		status |= bit(CRU_XFER_RDY);
//		status |= bit(CRU_INTR);
	}

	private void cmdComplete() {
//		status &= ~bit(CRU_XFER_RDY);
//		status &= ~bit(CRU_CTRL_BSY);
//		status |= bit(CRU_OPCOMPLETE);
//		status |= bit(CRU_INTR);
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
		iopbOp = mem.read(iopbAdr);
		iopbCmd = mem.read(iopbAdr + 1);
		iopbSC = mem.read(iopbAdr + 2);
		iopbTrk = mem.read(iopbAdr + 3);
		iopbSec = mem.read(iopbAdr + 4);
		iopbDma = mem.read(iopbAdr + 5) |
			(mem.read(iopbAdr + 6) << 8);

		if (iopbOp != 0x80) {
			// what sort of error
			fdcStat |= STS_DONE;
			return;
		}

		if (iopbCmd == CMD_RESTORE) {
			fdcStat |= STS_DONE;
			return;
		}

		int n;
		int c = (iopbCmd & MSK_CMD);
		int u = (iopbCmd & MSK_UNIT) >> SHF_UNIT;
		error = false;
//		status &= ~bit(CRU_INTR);
//		status |= bit(CRU_CTRL_BSY);
		switch (c) {
		case CMD_READ:
			command = iopbCmd;
			if (selectErr) {
				cmdError(0); // bits already set
				break;
			}
//			multi = ((cmd & bit(CRU_MULTI)) == 0);
//			head = (cmd >> CRU_HEAD) & 1;
//			sector = (cmd & MSK_SECTOR);
			if (sector == 0 || sector > 26) {
//				cmdError(bit(CRU_NO_ID));
				break;
			}
			if (fillBuf() < 0) {
//				cmdError(bit(CRU_DAT_ERR));
				break;
			}
			cmdXfer();
			active = true;
			if (!multi) {
//				status |= bit(CRU_OPCOMPLETE);
			}
			break;
		case CMD_WRITE:
			command = iopbCmd;
			if (selectErr || protect) {
//				cmdError(protect ? bit(CRU_WR_PROT) : 0);
				break;
			}
//			head = (cmd >> CRU_HEAD) & 1;
//			sector = (cmd & MSK_SECTOR);
			if (sector == 0 || sector > 26) {
//				cmdError(bit(CRU_NO_ID));
				break;
			}
			dataIdx = 0;
			cmdXfer();
			active = true;
			break;
		default:
System.err.format("Unknown command %02x\n", iopbCmd);
			break;
		}
		if (active) {
			timer.removeActionListener(this);
			timer.addActionListener(this);
			timer.restart();
			// hld[unit].set(true);
		}
		if (!active && !error) {
			cmdComplete();
		}
		chkIntr();
	}

	private int fillNext() {
		if (curr == null) {
			return -1;
		}
		// TODO: where does head come in?
		++sector;
		if (sector > 26) {
			sector = 1;
			++track;
			if (track > 76) {
				track = 76;
				cmdComplete();
				chkIntr();
				return 0;
			}
		}
		return fillBuf();
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
		for (int x = 0; x < 128; ++x) {
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

	private int getData() {
		if (unit < 0 || dataIdx >= sectorLen[unit]) {
			return 0;
		}
		int x = dataIdx;
		int data = (curBuf[x++] & 0xff) << 8;
		data |= (curBuf[x++] & 0xff);
		return data;
	}

	private void ackData() {
		if (unit < 0 || dataIdx >= sectorLen[unit]) {
			return;
		}
		dataIdx += 2;
		if (dataIdx >= sectorLen[unit]) {
			if (!multi) {
				cmdComplete();
				chkIntr();
			} else if (fillNext() < 0) {
//				cmdError(bit(CRU_DAT_ERR));
			}
		}
	}

	private void putData(int val) {
		if (unit < 0 || dataIdx >= sectorLen[unit]) {
			return;
		}
		dirty = true;
		curBuf[dataIdx++] = (byte)(val >> 8);
		curBuf[dataIdx++] = (byte)val;
		if (dataIdx >= sectorLen[unit]) {
			if (flushBuf() < 0) {
//				cmdError(bit(CRU_DAT_ERR));
			} else {
				cmdComplete();
				chkIntr();
			}
		}
	}

	///////////////////////////////
	/// Interfaces for CRUDevice ///

	// Returns value with LSB == 'off' bit of data
	// TODO: how to enforce 16-bit I/O restrictions for FD800
	public int readCRU(int adr, int cnt) {
		int off = adr - basePort;
		int val;
		if (off < 0x010) {
			val =  getData();
		} else {
			// Status word is maintained on the fly, only return it here...
			val = status;
		}
		val >>= (off & 0x0f);
		val &= ((1 << cnt) - 1);
		return val;
	}

	// Value has LSB == 'off' bit of data
	public void writeCRU(int adr, int cnt, int val) {
		int off = adr - basePort;
		val <<= (off & 0x0f);
		if (off < 0x010) {
			if (cnt == 1 && off == 0x0f) {
				ackData();
			} else {
				putData(val);
			}
		} else {
//			doCommand(val);
		}
	}

	public void reset() {
		// if commands are asynch, must abort any here...
		abort();
		selectDrive(pwrOn ? 0 : -1);
		status = 0;
		command = 0;
		dirty = false;
		Arrays.fill(sectorLen, 128);
		Arrays.fill(currTrack, -1);
		intrEnable = false;
		intr.lowerINT(irq, src);
	}

	public int getBaseAddress() { return 0x78; }

	public int getNumPorts() { return 4; }

	public int in(int port) {
		int off = port - getBaseAddress();
		int val = 0;
		switch (off) {
		case 0:
			val |= 0x04; // I/O done (also need disk ready bit(s))
			break;
		case 1:	// result type
			// ------xx meaning:
			// 00 = unlinked i/o complete, 01 = linked i/o complete (not used)
			// 10 = disk status changed, 11 = (not used)
			val = resType;
			break;
		case 2:
			break;
		case 3:	// result byte - Error bits:
			// 0 - deleted data (accepted as ok above)
			// 1 - crc error
			// 2 - seek error
			// 3 - address error (hardware malfunction)
			// 4 - data over/under flow (hardware malfunction)
			// 5 - write protect (treated as not ready)
			// 6 - write error (hardware malfunction)
			// 7 - not ready
			val = resByte;
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
		}
	}

	public String getDeviceName() { return name; }

	public String dumpDebug() {
		String ret = new String();
		ret += String.format("CRU base %03x, status = %04x, command = %04x\n",
			basePort, status, command);
		ret += String.format("track %d, head = %d, sector %d, unit %d\n",
			track, head, sector, unit);
		ret += String.format("dataIdx %d IntrEnable=%s\n", dataIdx, intrEnable);
		return ret;
	}

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
}
