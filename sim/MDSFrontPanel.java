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

public class MDSFrontPanel implements IODevice, ActionListener {
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
	int ints;

	public MDSFrontPanel(JFrame frame, Properties props) {
		JPanel panel;
		JPanel panel2;
		JLabel lab;
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
		panel = new JPanel();
		panel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
			Color.white, Color.gray));
		gb = new GridBagLayout();
		panel.setLayout(gb);
		panel.setOpaque(true);
		panel.setBackground(Color.black);

		JPanel pan;
		gc.gridwidth = 9;
		setGap(panel, 1000, 10);
		gc.gridwidth = 1;
		gc.gridx = 1;
		gc.gridy = 1;
		gc.gridheight = 2;
		gb.setConstraints(key, gc);
		panel.add(key);
		gc.gridheight = 1;
		++gc.gridx;
		setGap(panel, 10, 10);
		++gc.gridx;
		setLabel(panel, "PWR");
		++gc.gridx;
		setGap(panel, 10, 10);
		++gc.gridx;
		setLabel(panel, "HALT");
		++gc.gridx;
		setGap(panel, 10, 10);
		++gc.gridx;
		setLabel(panel, "RUN");
		++gc.gridx;
		setGap(panel, 100, 10);
		++gc.gridy;
		gc.gridx = 3;
		gb.setConstraints(pwr, gc);
		panel.add(pwr);
		gc.gridx = 5;
		gb.setConstraints(hlt, gc);
		panel.add(hlt);
		gc.gridx = 7;
		gb.setConstraints(run, gc);
		panel.add(run);
		++gc.gridy;
		gc.gridx = 0;
		setGap(panel, 10, 10);
		// end of upper panel

		// begin lower panel
		panel2 = new JPanel();
		panel2.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
			Color.white, Color.gray));
		gb = new GridBagLayout();
		panel2.setLayout(gb);
		panel2.setOpaque(true);
		panel2.setBackground(Color.black);
		gc.gridx = 0;
		gc.gridy = 0;
		gc.gridwidth = 21;
		setGap(panel2, 1000, 10);
		gc.gridwidth = 1;
		gc.gridx = 1;
		gc.gridy = 1;
		for (int x = 7; x >= 0; --x) {
			gb.setConstraints(irq[x], gc);
			panel2.add(irq[x]);
			++gc.gridy;
			setLabel(panel2, String.format("%d", x));
			++gc.gridy;
			gb.setConstraints(btns[x], gc);
			panel2.add(btns[x]);
			++gc.gridx;
			if (x > 0) {
				setGap(panel2, 10, 10);
			}
			gc.gridy = 1;
		}
		setGap(panel2, 50, 10);
		++gc.gridx;
		++gc.gridy;
		setLabel(panel2, "BOOT");
		++gc.gridy;
		gb.setConstraints(btns[BOOT], gc);
		panel2.add(btns[BOOT]);
		++gc.gridx;
		setGap(panel2, 100, 10);
		++gc.gridx;
		gc.gridy = 2;
		setLabel(panel2, "RESET");
		++gc.gridy;
		gb.setConstraints(btns[RESET], gc);
		panel2.add(btns[RESET]);
		++gc.gridx;
		setGap(panel2, 100, 10);
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
	}

	private void setGap(JPanel panel, int x, int y) {
		JPanel pan = new JPanel();
		pan.setPreferredSize(new Dimension(x, y));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		panel.add(pan);
	}

	private void setLabel(JPanel panel, String l) {
		JLabel lab = new JLabel(l);
		lab.setFont(lesstiny);
		lab.setOpaque(false);
		lab.setForeground(Color.white);
		gb.setConstraints(lab, gc);
		panel.add(lab);
	}

	public void setSys(MDS800 mds) {
		sys = mds;
	}

	public void trigger1mS() {
		clk1ms = true;
	}

	////////////////////////////
	// IODevice interface
	public void reset() {
		ints = 0;
		setIntrs(0);
		// reset 1mS clock?
	}

	public int getBaseAddress() {
		return 0xff;
	}

	public int getNumPorts() {
		return 1;
	}

	public int in(int port) {
		int val = (clk1ms ? 1 : 0) |
			(bootOn() ? 2 : 0);
		clk1ms = false;
		return val;
	}

	public void out(int port, int value) {
	}

	public String getDeviceName() {
		return "MDS FP";
	}

	public String dumpDebug() {
		return "";
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
		ints |= (1 << act);
		// notify core...
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
			pwr.set(key.isSelected());
			break;
		}
	}
}
