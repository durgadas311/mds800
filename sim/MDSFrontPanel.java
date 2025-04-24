// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Properties;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.Timer;
import javax.sound.sampled.*;

public class MDSFrontPanel implements IODevice, InterruptController, Interruptor,
					ActionListener {
	static final private int INT0 = 0;
	static final private int INT1 = 1;
	static final private int INT2 = 2;
	static final private int INT3 = 3;
	static final private int INT4 = 4;
	static final private int INT5 = 5;
	static final private int INT6 = 6;
	static final private int INT7 = 7;
	static final private int BOOT = 8;
	static final private int RESET = 9;
	static final private int PWR = 10;
	private Font tiny;
	private Font lesstiny;

	private MDS800 sys;

	private LED pwr;
	private LED hlt;
	private LED run;
	private LED[] irq;
	AbstractButton[] btns;
	JCheckBox key;
	GridBagLayout gb;
	GridBagConstraints gc;
	ImageIcon icn_on;
	ImageIcon icn_off;
	ImageIcon key_on;
	ImageIcon key_off;

	boolean clk1ms;
	int src1ms;
	int intState;
	int intService;
	int intMask;
	int priMask;
	private int[] intRegistry;
	private int[] intLines;
	private boolean i8259_init;
	private int i8259_page;	// not used - always do RST x
	private int i8259_adi;		// assumed to be 8
	private int i8259_cmd;		// assumed to always be 0b001
	private int i8259_lev;		// N/A for cmd 0b001
	private int i8259_cur;		// currently serviced intr
	private int i8259_rsel;		// register select for input
	private boolean i8259_smm;	// not supported
	private int ioSrc;
	private int ioInts;
	private boolean ioIntEna;
	JFrame main;

	public MDSFrontPanel(JFrame frame, Properties props) {
		JPanel panel;
		JPanel panel2;
		JLabel lab;
		main = frame;
		intRegistry = new int[8];
		intLines = new int[8];
		Arrays.fill(intRegistry, 1); // src 0 is FP
		Arrays.fill(intLines, 0);
		src1ms = registerINT(1);
		tiny = new Font("Sans-serif", Font.PLAIN, 8);
		lesstiny = new Font("Sans-serif", Font.PLAIN, 10);
		btns = new AbstractButton[16];
		irq = new LED[8];
		icn_on = new ImageIcon(getClass().getResource("icons/sw-on.png"));
		icn_off = new ImageIcon(getClass().getResource("icons/sw-off.png"));
		key_on = new ImageIcon(getClass().getResource("icons/key-on.png"));
		key_off = new ImageIcon(getClass().getResource("icons/key-off.png"));
		Color bg = new Color(50, 50, 50);
		for (int x = 0; x < 8; ++x) {
			JButton bn = new JButton();
			bn.setOpaque(false);
			bn.setText("");
			bn.setIcon(icn_off);
			bn.setPressedIcon(icn_on);
			bn.setBorderPainted(false);
			bn.setContentAreaFilled(false);
			bn.setFocusPainted(false);
			bn.addActionListener(this);
			bn.setMnemonic(0x1000 + x);
			btns[x] = bn;
			irq[x] = new RoundLED(LED.Colors.RED);
		}
		JCheckBox cb = new JCheckBox();
		cb.setOpaque(false);
		cb.setText("");
		cb.setSelectedIcon(icn_on);
		cb.setIcon(icn_off);
		cb.addActionListener(this);
		cb.setMnemonic(0x1000 + BOOT);
		btns[BOOT] = cb;
		JButton bn = new JButton();
		bn.setOpaque(false);
		bn.setText("");
		bn.setIcon(icn_off);
		bn.setPressedIcon(icn_on);
		bn.setBorderPainted(false);
		bn.setContentAreaFilled(false);
		bn.setFocusPainted(false);
		bn.addActionListener(this);
		bn.setMnemonic(0x1000 + RESET);
		btns[RESET] = bn;

		key = new JCheckBox();
		key.setOpaque(false);
		key.setText("");
		key.setSelectedIcon(key_on);
		key.setIcon(key_off);
		key.addActionListener(this);
		key.setMnemonic(0x1000 + PWR);

		pwr = new RoundLED(LED.Colors.RED);
		hlt = new RoundLED(LED.Colors.RED);
		run = new RoundLED(LED.Colors.RED);

		gc = new GridBagConstraints();
		gc.fill = GridBagConstraints.NONE;
		gc.gridx = 0;
		gc.gridy = 0;
		gc.weightx = 0;
		gc.weighty = 0;
		gc.gridwidth = 1;
		gc.gridheight = 1;
		gc.anchor = GridBagConstraints.CENTER;

		// start upper panel
		panel = new RackUnit(3);
		gb = new GridBagLayout();
		panel.setLayout(gb);

		JPanel pan;
		gc.gridwidth = 10;
		setGap(panel, RackUnit.WIDTH - 4, 10);
		gc.gridwidth = 1;
		gc.gridy = 1;
		gc.gridx = 2;
		setLabel(panel, "ON", 20);
		++gc.gridy;
		gc.gridx = 1;
		gc.gridheight = 2;
		setLabel(panel, "OFF", 20);
		++gc.gridx;
		gb.setConstraints(key, gc);
		panel.add(key);
		gc.gridheight = 1;
		++gc.gridx;
		setGap(panel, 10, 10);
		++gc.gridx;
		int left = gc.gridx;
		setLabel(panel, "PWR", 20);
		++gc.gridx;
		setGap(panel, 10, 10);
		++gc.gridx;
		setLabel(panel, "HALT", 20);
		++gc.gridx;
		setGap(panel, 10, 10);
		++gc.gridx;
		setLabel(panel, "RUN", 20);
		++gc.gridx;
		setGap(panel, 100, 10);
		++gc.gridy;
		gc.gridx = left;
		gb.setConstraints(pwr, gc);
		panel.add(pwr);
		gc.gridx += 2;
		gb.setConstraints(hlt, gc);
		panel.add(hlt);
		gc.gridx += 2;
		gb.setConstraints(run, gc);
		panel.add(run);
		++gc.gridy;
		gc.gridx = 0;
		setGap(panel, 10, 10);
		// end of upper panel

		// begin lower panel
		panel2 = new RackUnit(4);
		gb = new GridBagLayout();
		panel2.setLayout(gb);
		gc.gridx = 0;
		gc.gridy = 0;
		gc.gridwidth = 21;
		setGap(panel2, RackUnit.WIDTH - 4, 10);
		gc.gridwidth = 1;
		gc.gridx = 1;
		gc.gridy = 1;
		char[] l = new char[38];
		Arrays.fill(l, '\u2500');
		String line = new String(l);
		gc.gridwidth = 15;
		setLabel(panel2,
			"\u250c" + line +
			" INTERRUPTS " +
			line + "\u2510",
			230);
		gc.gridwidth = 1;
		++gc.gridy;
		int top = gc.gridy;
		for (int x = 7; x >= 0; --x) {
			gb.setConstraints(irq[x], gc);
			panel2.add(irq[x]);
			++gc.gridy;
			setLabel(panel2, String.format("%d", x), 20);
			++gc.gridy;
			gb.setConstraints(btns[x], gc);
			panel2.add(btns[x]);
			++gc.gridx;
			if (x > 0) {
				setGap(panel2, 5, 10);
				++gc.gridx;
			}
			gc.gridy = top;
		}
		setGap(panel2, 50, 10);
		++gc.gridx;
		++gc.gridy;
		top = gc.gridy;
		setLabel(panel2, "BOOT", 20);
		++gc.gridy;
		gb.setConstraints(btns[BOOT], gc);
		panel2.add(btns[BOOT]);
		++gc.gridx;
		setGap(panel2, 100, 10);
		++gc.gridx;
		gc.gridy = top;
		setLabel(panel2, "RESET", 20);
		++gc.gridy;
		gb.setConstraints(btns[RESET], gc);
		panel2.add(btns[RESET]);
		++gc.gridx;
		setGap(panel2, 100, 10);
		++gc.gridy;
		gc.gridx = 0;
		gc.gridwidth = 21;
		setGap(panel2, RackUnit.WIDTH - 4, 20);
		// end of lower panel

		gb = new GridBagLayout();
		frame.setLayout(gb);
		gc.gridx = 0;
		gc.gridy = 0;
		
		gb.setConstraints(panel, gc);
		frame.add(panel);
		++gc.gridy;
		gb.setConstraints(panel2, gc);
		frame.add(panel2);
		// reset for addPanel()...
		gc.gridx = 0;
		gc.gridy = 2;

		ioSrc = registerINT(3);
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

	public void addPanel(JPanel panel) {
		gb.setConstraints(panel, gc);
		main.add(panel);
		++gc.gridy;
	}

	public void setSys(MDS800 mds) {
		sys = mds;
		sys.addIntrController(this);
	}

	public void trigger1mS() {
		clk1ms = true;
//		raiseINT(1, src1ms);
	}

	private void setInt(int irq) {
		intState |= (1 << irq);
		this.irq[irq].set(true);
		// TODO: i8259: check priority and only raise if higher?
		if ((intState & ~intMask & priMask) != 0) {
			sys.raiseINT();
		}
	}

	private void __clearInt(int irq) {
		intState &= ~(1 << irq);
		this.irq[irq].set(false);
	}

	private void clearInt(int irq) {
		__clearInt(irq);
		if ((intState & ~intMask & priMask) == 0) {
			sys.lowerINT();
		}
	}

	// sets a new mask, some bits might unmask...
	private void maskInts(int msk) {
		intMask = msk;
		if ((intState & ~intMask & priMask) == 0) {
			sys.lowerINT();
		} else {
			sys.raiseINT();
		}
	}

	private int intrAck() {
		int irq = Integer.numberOfTrailingZeros(intState & ~intMask & priMask);
		if (irq > 7) {
			return -1;
		}
		i8259_cur = irq;
		priMask = (1 << irq) - 1;
		intService |= (1 << irq);
		__clearInt(irq);
		__lowerINT(irq, 0); // in case it came from us (switches)
		sys.lowerINT();
		return irq;
	}

	////////////////////////////////////
	// InterruptController interface
	public int readDataBus() {
		// this also serves as INTA
		// TODO: what does i8259 do here?
		int irq = intrAck();
		if (irq < 0) {
			return -1;
		}
		// TODO: support generic CALL to i8259_page?
		// if (i8259_page != 0) {
		//	adr = i8259_page + (irq * i8259_adi);
		//	0: return 0xcd
		//	1: return adr & 0xff
		//	2: return adr >> 8
		// } else {
		return (0xc7 | (irq << 3));
	}

	private void reset_i8259() {
		intState = 0;
		intService = 0;
		intMask = 0;
		priMask = 0xff;
		setIntrs(0);
		i8259_init = false;
		i8259_page = 0;
		i8259_cmd = 0;
		i8259_adi = 0;
		i8259_lev = 0;
		i8259_cur = 0; // TODO: need an invalid value?
		i8259_rsel = 0;
		sys.lowerINT();
	}

	public void addIOFrame(JFrame frame) {
		// create/add menu item for "frame.setVisible(true/false)"
	}

	// Stray port we need to own... I/O intr ctrl - reset int sources
	public void outF3(int port, int val) {
		ioIntEna = (val & Interruptor.IO_INT_ENA) != 0;
		ioInts &= ~val;
		if (ioInts == 0 || !ioIntEna) {
			lowerINT(3, ioSrc);
		} else {
			raiseINT(3, ioSrc);
		}
	}
	// LPT "owns" ports FA,FB... except input on FA (I/O intr status)
	public int inFA() {
		return ioInts;
	}

	////////////////////////////////////
	// IODevice interface
	// i8259 + sys ctl
	public void reset() {
		reset_i8259();
		run.set(false);
		hlt.set(false);
		clk1ms = false;
		ioIntEna = false;
		ioInts = 0;
	}

	public int getBaseAddress() {
		return 0xfc;
	}

	public int getNumPorts() {
		return 4;
	}

	public int in(int port) {
		int off = port & 0x03;
		int val = 0;
		switch (off) {
		case 0:
			val = intMask;
			break;
		case 1:
			if ((i8259_rsel & 0x04) != 0) { // POLL
				val = intrAck();
				if (val < 0) {
					val = 0;
				} else {
					val |= 0x80; // yes?
				}
			} else if ((i8259_rsel & 0x02) != 0) { // RR
				if ((i8259_rsel & 0x01) != 0) { // RIS
					val = intService;
				} else {
					val = intState;
				}
			}
			break;
		case 2:
			// not present?
			break;
		case 3:
			val = (clk1ms ? 1 : 0) |
				(bootOn() ? 2 : 0);
			clk1ms = false;
			break;
		}
		return val;
	}

	public void out(int port, int value) {
		int off = port & 0x03;
		switch (off) {
		case 0:
			if (i8259_init) { // ICW2
				i8259_init = false;
				i8259_page |= (value << 8);
			} else {	// OCW1
				maskInts(value);
			}
			break;
		case 1:
			// TODO: restore intr state ("pop")
			// 0x12: init?
			// 0x20: "restore operating level"
			if ((value & 0x10) == 0x10) { // ICW1
				reset_i8259();
				i8259_init = true;
				// TODO: validate other bits, reject/report unsupp?
				// assume SINGLE
				// assume EDGE TRIGGERED
				// not used (yet):
				i8259_page = (value & 0xe0);
				i8259_adi = (value & 0x04);
				if (i8259_adi == 0) i8259_adi = 8;
			} else if ((value & 0x08) == 0x00) { // OCW2
				i8259_cmd = (value & 0xe0) >> 5;
				i8259_lev = (value & 0x07);
				if (i8259_cmd == 1) { // EOI
					intService &= ~(1 << i8259_cur);
					priMask = 0xff;
					if ((intState & ~intMask & priMask) != 0) {
						sys.raiseINT();
					}
				}
			} else {	// OCW3
				i8259_rsel = value & 0x07;
				if ((value & 0x20) != 0) { // RESET/SET spcl mask
					// TODO: not supported
					i8259_smm = ((value & 0x10) != 0);
				}
			}
			break;
		case 2:
			// not present?
			break;
		case 3:
			// no function? some sort of reset?
			break;
		}
	}

	public String getDeviceName() {
		return "MDS_FP";
	}

	public String dumpDebug() {
		String ret = String.format("MDS Front Panel port %02x\n", getBaseAddress());
		ret += String.format("INT state=%02x mask=%02x svc=%02x\n",
				intState, intMask, intService);
		ret += String.format("i8259 init=%s page=%04x adi=%d cmd=%x/%x\n",
				i8259_init, i8259_page, i8259_adi,
				i8259_cmd, i8259_lev);
		ret += String.format("      cur=%d rr=%x smm=%s\n",
				i8259_cur, i8259_rsel, i8259_smm);
		ret += String.format("BOOT=%s  1mS=%s\n", bootOn(), clk1ms);
		return ret;
	}

	public void setIntr(int src, boolean on) {
		if (src < 0 || src > irq.length) {
			return;
		}
		irq[src].set(on);
	}

	public void setIntrs(int intrs) {
		int x;

		for (x = 0; x < irq.length; ++x) {
			setIntr(x, (intrs & (1 << x)) != 0);
		}
	}

	public void setPower(boolean on) {
		pwr.set(on);
		sys.setPower(on);
	}

	public void setHalt(boolean on) {
		hlt.set(on);
	}

	public void setRun(boolean on) {
		run.set(on);
	}

	public void update() {
		run.set(sys.isRunning());
		hlt.set(sys.isHalted());
	}

	public void run() {
		sys.sendCommand("reset");
	}

	public boolean bootOn() {
		return btns[BOOT].isSelected();
	}

	private void doIntSw(int act) {
		raiseINT(act, 0);
	}

	////////////////////////////////////
	// Interruptor interface
	public int registerINT(int irq) {
		int val = intRegistry[irq & 7]++;
		return val;
	}

	public void raiseINT(int irq, int src) {
		irq &= 7;
		intLines[irq] |= (1 << src);
		setInt(irq);
	}

	private void __lowerINT(int irq, int src) {
		irq &= 7;
		intLines[irq] &= ~(1 << src);
	}

	public void lowerINT(int irq, int src) {
		__lowerINT(irq, src);
		if (intLines[irq] == 0) {
			clearInt(irq);
		}
	}

	public void triggerIOInt(int dev) {
		ioInts |= dev;
		if (ioInts != 0 && ioIntEna) {
			raiseINT(3, ioSrc);
		}
	}

	public void actionPerformed(ActionEvent e) {
		AbstractButton btn = (AbstractButton)e.getSource();
		int act = btn.getMnemonic();
		if (act < 0x1000) {
			return;
		}
		act &= 0xfff;
		if (act > PWR) {
			return;
		}
		if (act < 8) {
			doIntSw(act);
			return;
		}
		switch (act) {
		case BOOT:
			break;
		case RESET:
			sys.reset();
			break;
		case PWR:
			setPower(key.isSelected());
			break;
		}
	}
}
