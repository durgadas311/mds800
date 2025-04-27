// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Vector;
import java.util.Properties;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;

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

	class PromSocket extends JPanel implements MouseListener {
		public byte[] prom;
		public File promFile;
		public boolean dirty;
		public int width;
		public int pins;
		public int size;
		public byte init; // initial (erased) byte value
		public int id;
		public ActionListener lstr;
		public JCheckBox bn;
		public String[] sz;	// supported PROM sizes
		public JComboBox<String> kb;

		public PromSocket(Properties props, ImageIcon icn0, ImageIcon icn1,
				int id, ActionListener lstr) {
			super();
			this.id = id;
			this.lstr = lstr;
			setOpaque(true);
			setBackground(norm);
			String s = props.getProperty(String.format("upp_socket%d", id));
			if (s == null) {
				s = (id == 1 ? "361" : "816");
			}
			if (s.equals("361")) {
				pins = 16;
				width = 4;
				init = (byte)0;
				sz = new String[] { "256x4" };
			} else if (s.equals("864")) {
				pins = 16;
				width = 4;
				init = (byte)0x0f;
				sz = new String[] { "512x4" };
			} else if (s.equals("864:24")) {
				pins = 24;
				width = 8;
				init = (byte)0xff;
				sz = new String[] { "512x8" };
			} else if (s.equals("878")) {
				pins = 24;
				width = 8;
				init = (byte)0xff;
				sz = new String[] { "512x8", "1Kx8" };
			} else if (s.equals("872")) {
				pins = 24;
				width = 8;
				init = (byte)0xff;
				sz = new String[] { "256x8" };
			} else if (s.equals("816")) {
				pins = 24;
				width = 8;
				init = (byte)0xff;
				sz = new String[] { "2Kx8" };
			} else {
				// panic...
			}
				
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
		}

		// 'p' is PROM size, e.g. "2Kx8"
		public void setFile(File f, String p) {
			// assume already flushed...
			dirty = false;
			promFile = f;
			if (f == null) {
				prom = null;
				bn.setToolTipText("(none)");
				bn.setSelected(false);
				return;
			}
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
				if (len != size || wid != width) {
					PopupFactory.warning(this, "PROM",
						String.format("image size %dx%d != %s\n",
							len, wid, p));
					setFile(null, null);
					return;
				}
			}
			prom = new byte[size];
			Arrays.fill(prom, init);
			dirty = true;
			if (f.exists()) {
				try {
					InputStream is = new FileInputStream(f);
					is.read(prom);
					is.close();
					dirty = false;
				} catch (Exception ee) {}
			}
			bn.setToolTipText(promFile.getName());
			bn.setSelected(true);
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
			String ret = String.format("socket #%d dip%d x%d era %02x\n",
					id, pins, width, init & 0xff);
			if (prom == null) {
				ret += "(empty)\n";
			} else {
				ret += String.format("%s %dB\n",
					promFile.getName(), prom.length);
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
		ImageIcon icon16_out = new ImageIcon(getClass().getResource("icons/upp16-mt.png"));
		ImageIcon icon16_in = new ImageIcon(getClass().getResource("icons/upp16-ic.png"));
		ImageIcon icon24_out = new ImageIcon(getClass().getResource("icons/upp24-mt.png"));
		ImageIcon icon24_in = new ImageIcon(getClass().getResource("icons/upp24-ic.png"));
		soks[0] = new PromSocket(props, icon16_out, icon16_in, 1, this);
		soks[1] = new PromSocket(props, icon24_out, icon24_in, 2, this);
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
			pgm.set(true);
			if (prom_nib != 0) {
				val >>= 4;
			}
			if (prom_sok == 1) {
				val &= 0x0f;
			}
			int val0 = cursok.prom[prom_adr] & 0xff;
			// TODO: different for bipolar PROMs?
			if ((val0 & val) != val) {
				prom_sts = STS_PERR;
				// TODO: go ahead and trash byte?
				break;
			}
			cursok.prom[prom_adr] = (byte)val;
			cursok.dirty = true;
			// TODO: flush to file after every write?
			prom_sts = STS_DONE;
			timeout = now + 1000000; // 1mS to program?
			break;
		case 1:	// adr hi, ctl
			prom_ctl = (val & 0x80) >> 7;
			prom_rd = (val & 0x40) >> 6;
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
		}
		return file;
	}

	public void actionPerformed(ActionEvent e) {
		if (!(e.getSource() instanceof PromSocket)) {
			return;
		}
		PromSocket sk = (PromSocket)e.getSource();
		flushProm(sk);
		sk.setFile(null, null);
		File f = pickFile("Insert PROM", sk.kb);
		if (f == null) {
			return;
		}
		sk.setFile(f, sk.kb.getItemAt(sk.kb.getSelectedIndex()));
	}
}
