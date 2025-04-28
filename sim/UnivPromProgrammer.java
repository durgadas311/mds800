// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Vector;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;

// Based on Intel documentation, supported personality cards are:
//
// Card		Pins	Erased	Bits
// 361		16	0	256x4
// 864		16	1	512x4
// 864:24	24	1	512x8
// 878		24	1	512x8 1Kx8
// 872		24	1	256x8
// 816		24	1	2Kx8

public class UnivPromProgrammer extends RackUnit implements IODevice,
				TimeListener, PowerListener, ActionListener {
	// by mutual agreement with any TTY-attached peripheral
	private static final int RDR = 0x0f;	// ^O - new: advance reader once

	private int basePort;
	private String name = null;
	private long now;
	private long timeout;
	boolean pwrOn;

	// 0xf1 - status
	private static final int STS_BUSY = 0x01;	// busy
	private static final int STS_DONE = 0x02;	// completed
	private static final int STS_FAIL = 0x04;	// failed to program
	private static final int STS_PERR = 0x08;	// programming error
	private static final int STS_AERR = 0x10;	// address error
	private static final int STS_HERR = 0x20;	// h/w error
	private static final int STS_BRD = 0x40;	// board sense error
	private static final int STS_ORI = 0x80;	// orientation error
	// 0xf1 - control
	private static final int CTL_ADRH = 0x0f;	// high addr mask
	private static final int CTL_NIBL = 0x10;	// nibble select
	private static final int CTL_SOCK = 0x20;	// socket select
	private static final int CTL_READ = 0x40;	// start read command
	private static final int CTL_CTRL = 0x80;	// control # (always 0)

	static Color norm = RackUnit.BG;
	static Color actv = RackUnit.BG.brighter();
	static final Color normHi = actv.brighter();
	static final Color normLo = Color.black;
	static final String[] sufx = { "rom" };
	static final String[] sufd = { "ROM Image" };
	File last = null;
	GridBagConstraints gc;
	GridBagLayout gb;
	private Font lesstiny;

	PromSocket[] soks;
	PromSocket cursok;
	LED pwr;
	LED pgm;
	int prom_sts;
	int prom_adr;
	int prom_sok;
	int prom_nib;
	int prom_ctl;
	int prom_rd;

	static class Personality {
		int pins;	// socket/chip pins
		int width;	// bits per word
		byte init;	// erased value (word)
		String[] org;	// SizexWidth

		public Personality(int p, int w, int i, String[] o) {
			pins = p;
			width = w;
			init = (byte)i;
			org = o;
		}
	};

	static Map<String,Personality> pers;

	static {
		pers = new HashMap<String,Personality>();
		pers.put("361", new Personality(16, 4, 0,
			new String[] { "256x4" }));
		pers.put("865", new Personality(16, 4, 0x0f,
			new String[] { "256x4", "512x4" }));
		pers.put("865:18", new Personality(18, 8, 0xff,
			new String[] { "1Kx8", "1Kx4" }));
		pers.put("865:24", new Personality(24, 8, 0xff,
			new String[] { "512x8", "1Kx8", "2Kx8" }));
		pers.put("878", new Personality(24, 8, 0xff,
			new String[] { "512x8", "1Kx8" }));
		pers.put("872", new Personality(24, 8, 0xff,
			new String[] { "256x8" }));
		pers.put("816", new Personality(24, 8, 0xff,
			new String[] { "2Kx8" }));
		pers.put("832", new Personality(24, 8, 0xff,
			new String[] { "4Kx8" }));
	}

	class PromSocket extends JPanel implements MouseListener {
		public byte[] prom;
		public File promFile;
		public boolean dirty;
		public int pins;
		public byte init; // initial (erased) byte value
		public int id;
		public ActionListener lstr;
		public JCheckBox bn;
		public String[] sz;	// supported PROM sizes
		public JComboBox<String> kb;
		public JCheckBox cb;
		public JPanel pn;
		public int last;	// last selected item in kb
		// set by specific chip selection (varies)
		public int width;
		public int size;
		public int mask;
		public long time; // progam pulse, nS

		public PromSocket(Properties props, int id, ActionListener lstr) {
			super();
			this.id = id;
			this.lstr = lstr;
			ImageIcon icn0, icn1;
			String icn;
			setOpaque(true);
			setBackground(norm);
			String s = props.getProperty(String.format("upp_socket%d", id));
			if (s.matches("^[Dd][Ii][Pp][12][4680].*$")) {
				// "dipXX E"
				// XX = pins in socket
				// E = 0/1 (erased state) [default 1]
				String[] ss = s.split("\\s");
				pins = Integer.valueOf(ss[0].substring(3));
				if (pins < 16) pins = 16;
				if (pins > 24) pins = 24;
				if (ss.length > 1) {
					init = (byte)(Integer.valueOf(ss[1]) != 0 ? 0xff : 0);
				} else {
					init = (byte)0xff;
				}
				sz = defaultOrgs();
			} else {
				if (!pers.containsKey(s)) {
					System.err.format("Invalid UPP personality " +
						"\"%s\", using default\n", s);
					s = null;
				}
				if (s == null) {
					s = (id == 1 ? "361" : "816");
				}
				if (pers.containsKey(s)) {
					Personality p = pers.get(s);
					pins = p.pins;
					init = p.init;
					width = p.width;
					sz = p.org;
				}
			}
			icn = String.format("icons/upp%d-", pins);
			icn0 = new ImageIcon(getClass().getResource(icn + "mt.png"));
			icn1 = new ImageIcon(getClass().getResource(icn + "ic.png"));

			bn = new JCheckBox();
			bn.setOpaque(false);
			bn.setText("");
			bn.setIcon(icn0);
			bn.setDisabledIcon(icn0);
			bn.setSelectedIcon(icn1);
			bn.setDisabledSelectedIcon(icn1);
			//bn.addActionListener(lstr);
			bn.setMnemonic(0x1000 + id);
			bn.addMouseListener(this);
			bn.setEnabled(false);
			add(bn);

			kb = new JComboBox<String>(sz);
			cb = new JCheckBox("Erase");
			pn = new JPanel();
			pn.setLayout(new BoxLayout(pn, BoxLayout.Y_AXIS));
			pn.add(cb);
			pn.add(kb);
			last = 0;
		}

		// pins	data	max	min(arb)
		// 16	4	512	32
		// 16	8	32	32
		// 18	4	2048	256
		// 18	8	128	32
		// 20	4	4096 *	256
		// 20	8	512	128
		// 24	4	4096 *	256
		// 24	8	4096 *	256
		private String[] defaultOrgs() {
			Vector<String> orgs = new Vector<String>();
			int sz, na, ma;
			for (int w = 4; w <= 8; w += 4) {
				// theoretical maximum number of addr bits possible,
				// taking into account data bits, power/ground,
				// and one chip select.
				na = pins - w - 3; // dat,pwr,gnd,cs
				if (na > 12) {	// pgmr has only 12 adr bits
					na = 12;
				}
				ma = (1 << na);
				if (ma >= 1024) {
					sz = 256;
				} else if (ma > 256) {
					sz = 128;
				} else {
					sz = 32;
				}
				for (; sz <= ma; sz *= 2) {
					if (sz > 512) {
						orgs.add(String.format("%dKx%d",
							sz / 1024, w));
					} else {
						orgs.add(String.format("%dx%d", sz, w));
					}
				}
			}
			return orgs.toArray(new String[0]);
		}

		public void changeSocket() {
			// TODO:
			// bn.setDisabledIcon(icn0);
			// bn.setSelectedIcon(icn1);
			// ...
		}

		public void pickProm() {
			setFile(null);
			kb.setSelectedIndex(last);
			cb.setSelected(false);
			File f = pickFile("Insert PROM", pn);
			if (f == null) {
				return;
			}
			setFile(f);
		}

		// 'p' is PROM size, e.g. "2Kx8"
		public void setFile(File f) {
			// assume already flushed...
			dirty = false;
			promFile = f;
			if (f == null) {
				prom = null;
				bn.setToolTipText("(none)");
				bn.setSelected(false);
				return;
			}
			int idx = kb.getSelectedIndex();
			String p = kb.getItemAt(idx);
			String[] pp = p.split("x");
			int e = pp[0].length();
			if (pp[0].endsWith("K")) {
				--e;
				size = 1024;
			} else {
				size = 1;
			}
			size *= Integer.valueOf(pp[0].substring(0, e));
			int wid = width;
			if (pp.length > 1) {
				wid = Integer.valueOf(pp[1]);
			}
			if (f.exists()) {
				long len = f.length();
				if (len != size) {
					PopupFactory.warning(this, "PROM",
						String.format("image size %dx%d != %s\n",
							len, wid, p));
					setFile(null);
					return;
				}
			}
			mask = (1 << wid) - 1;
			prom = new byte[size];
			Arrays.fill(prom, (byte)(init & mask));
			dirty = true;
			time = 1000000; // assume 1mS program pulse width
			if (!cb.isSelected() && f.exists()) {
				try {
					InputStream is = new FileInputStream(f);
					int n = is.read(prom);
					is.close();
					dirty = (n < size);
				} catch (Exception ee) {}
			}
			bn.setToolTipText(promFile.getName());
			bn.setSelected(true);
			last = idx;
		}

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
				lstr.actionPerformed(new ActionEvent(this, e.getID(), "prom"));
			}
		}

		public String dumpDebug() {
			String ret = String.format("socket #%d dip%d x%d " +
						"era %02x msk %02x\n",
					id, pins, width, init, mask);
			if (prom == null) {
				ret += "(empty)\n";
			} else {
				ret += String.format("%s %dB dirty=%s\n",
					promFile.getName(), prom.length, dirty);
			}
			return ret;
		}
	};

	public UnivPromProgrammer(Properties props, int base, MDS800 mds) {
		super(4);	// nothing fancy, yet
		setBackground(RackUnit.BLUE);
		name = "UPP";
		lesstiny = new Font("Sans-serif", Font.PLAIN, 10);
		mds.addTimeListener(this);
		mds.addPowerListener(this);
		last = new File(System.getProperty("user.dir"));
		basePort = base;
		norm = getBackground();
		actv = getBackground().darker();
		soks = new PromSocket[2];
		soks[0] = new PromSocket(props, 1, this);
		soks[1] = new PromSocket(props, 2, this);
		pwr = new RoundLED(LED.Colors.RED);
		pgm = new RoundLED(LED.Colors.RED);

		gb = new GridBagLayout();
		setLayout(gb);
		gc = new GridBagConstraints();
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
		setGap(this, 50, 10);
		++gc.gridx;
		setLabel(this, "POWER", 30);
		++gc.gridy;
		gb.setConstraints(pwr, gc);
		add(pwr);
		++gc.gridy;
		gc.gridwidth = 3;
		JLabel lab = new JLabel("MDS Universal PROM Programmer");
		lab.setOpaque(false);
		gb.setConstraints(lab, gc);
		add(lab);
		gc.gridwidth = 1;
		gc.gridy -= 2;
		++gc.gridx;
		setGap(this, 200, 10);
		++gc.gridx;
		setLabel(this, "PROGRAMMING", 100);
		++gc.gridy;
		gb.setConstraints(pgm, gc);
		add(pgm);
		--gc.gridy;
		++gc.gridx;
		gc.gridheight = 3;
		setGap(this, 200, 10);
		++gc.gridx;
		gb.setConstraints(soks[0], gc);
		add(soks[0]);
		++gc.gridx;
		setGap(this, 50, 10);
		++gc.gridx;
		gb.setConstraints(soks[1], gc);
		add(soks[1]);
		++gc.gridx;
		gc.gridheight = 1;
		setGap(this, 50, 10);
		++gc.gridx;

		gc.gridwidth = gc.gridx;
		gc.gridx = 0;
		gc.gridy = 0;
		setGap(this, RackUnit.WIDTH - 4, 10);
		gc.gridwidth = 1;

		pwrOn = false;
		reset();
	}

	public void addTime(int nsec) {
		now += nsec;
	}

	public void setPower(boolean on) {
		pwrOn = on;
		reset();
	}

	private void setGap(JPanel panel, int w, int h) {
		JPanel pan = new JPanel();
		pan.setPreferredSize(new Dimension(w, h));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		panel.add(pan);
	}

	private void setLabel(JPanel panel, String l, int w) {
		JLabel lab = new JLabel(l);
		lab.setFont(lesstiny);
		lab.setOpaque(false);
		lab.setForeground(Color.white);
		gb.setConstraints(lab, gc);
		panel.add(lab);
	}

	// Conditions affecting interrupts have changed, ensure proper signal.
	private void chkIntr() {
		// TOD: implement interrupts
	}

	private boolean chkProm(PromSocket sk) {
		if (sk == null || sk.prom == null) {
			prom_sts = STS_HERR; // ??
			return true;
		}
		if (prom_adr >= sk.prom.length) {
			prom_sts = STS_AERR;
			return true;
		}
		return false;
	}

	private void flushProm(PromSocket sk) {
		if (sk == null || sk.prom == null || !sk.dirty) {
			return;
		}
		try {
			OutputStream os = new FileOutputStream(sk.promFile);
			os.write(sk.prom);
			os.close();
		} catch (Exception ee) {}
		sk.dirty = false; // or do this inside "try"?
	}

	///////////////////////////////
	/// Interfaces for IODevice ///
	public int in(int port) {
		int off = port - basePort;
		int val = 0;
		switch(off) {
		case 0: // Read Data
			if (chkProm(cursok)) {
				break;
			}
			flushProm(cursok); // what if done after each write?
			val = cursok.prom[prom_adr] & 0xff;
			if (prom_sok == 1) {
				val &= 0x0f;
			}
			if (prom_nib != 0) {
				val <<= 4;
			}
			prom_sts = STS_DONE;
			break;
		case 1:
			if (timeout > 0) {
				if (now < timeout) {
					val = STS_BUSY;
					break;
				}
				timeout = 0;
				pgm.set(false); // or by idle timeout?
			}
			val = prom_sts;
			break;
		}
		return val;
	}

	public void out(int port, int val) {
		int off = port - basePort;
		val &= 0xff; // necessary?
		switch(off) {
		case 0: // Program Data
			if (chkProm(cursok)) {
				break;
			}
			prom_sts = STS_BUSY;
			timeout = 0;
			pgm.set(true);
			if (prom_nib != 0) {
				// error if width != 4?
				val >>= 4;
			}
			val &= cursok.mask;
			int val0 = cursok.prom[prom_adr] & cursok.mask;
			if (cursok.init == 0) { // PROM bits erase to "0"
				val0 |= val;
			} else {	// PROM bits erase to "1"
				val0 &= val;
			}
			if (val0 != val) {
				prom_sts = STS_PERR; // BUSY off
				// TODO: go ahead and trash byte? continue?
				pgm.set(false);	//
				break;		//
			}
			cursok.prom[prom_adr] = (byte)val;
			cursok.dirty = true;
			// TODO: flush to file after every write?
			prom_sts = STS_DONE; // success... after timeout
			timeout = now + cursok.time;
			break;
		case 1:	// adr hi, ctl
			prom_ctl = (val & 0x80) >> 7;
			prom_rd = (val & 0x40) >> 6;	// how does this work?
			prom_sok = (val & 0x20) >> 5;
			if (prom_sok == 0) prom_sok = 2;
			prom_nib = (val & 0x10) >> 4;
			prom_adr = (prom_adr & 0xff) |
				((val & 0x0f) << 8);
			cursok = soks[prom_sok - 1];
			break;
		case 2:	// adr lo
			prom_adr = (prom_adr & ~0xff) | val;
			break;
		}
	}

	public void reset() {
		prom_sts = STS_DONE;
		pwr.set(pwrOn);
		// TODO: how much is reset?
		// PROMs remain in sockets... but auto-flush?
		if (!pwrOn) {
			flushProm(soks[0]);
			flushProm(soks[1]);
		}
	}
	public int getBaseAddress() {
		return basePort;
	}
	public int getNumPorts() {
		return 3;
	}

	public String getDeviceName() { return name; }

	public String dumpDebug() {
		String ret = new String();
		ret += String.format("UPP port %02x\n", basePort);
		ret += String.format("sts=%02x adr=%03x sok=%d nib=%d\n",
			prom_sts, prom_adr, prom_sok, prom_nib);
		ret += soks[0].dumpDebug();
		ret += soks[1].dumpDebug();
		return ret;
	}

	private File pickFile(String purpose, JComponent obj) {
		File file = null;
		SuffFileChooser ch = new SuffFileChooser(purpose, sufx, sufd, last, obj);
		int rv = ch.showDialog(this);
		if (rv == JFileChooser.APPROVE_OPTION) {
			file = ch.getSelectedFile();
			last = file;
		}
		return file;
	}

	public void actionPerformed(ActionEvent e) {
		if (!(e.getSource() instanceof PromSocket)) {
			return;
		}
		PromSocket sk = (PromSocket)e.getSource();
		flushProm(sk);
		sk.pickProm();
	}
}
