// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.io.*;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

public class MDS_LPT extends JFrame implements IODevice, PeripheralContainer,
			ActionListener {
	private static final int STS_STATI = 0x02;
	private static final int STS_NBUSY = 0x01; // active low...

	static final String[] sufx = { "txt" };
	static final String[] sufd = { "Text" };

	MDSFrontPanel fp;
	int basePort;
	boolean visible;

	JTextArea text;
	JScrollPane scroll;
	int carr;
	int col;
	int last_lf;
	File _last = null;

	public MDS_LPT(Properties props, int base, MDSFrontPanel fp) {
		super("MDS Line Printer");
		this.fp = fp;
		basePort = base;
		_last = new File(System.getProperty("user.dir"));
		getContentPane().setName("LPT Emulator");
		getContentPane().setBackground(new Color(100, 100, 100));

		//String s = props.getProperty("lpt_xxx");

		setLayout(new BorderLayout()); // allow resizing
		text = new JTextArea(24, 132);
		text.setEditable(false);
		text.setBackground(Color.white);
		Font font = new Font("Monospaced", Font.PLAIN, 10);
		text.setFont(font);
		scroll = new JScrollPane(text);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		add(scroll, BorderLayout.CENTER); // allow resizing

		JMenuBar mb = new JMenuBar();
		JMenu mu = new JMenu("Paper");
		JMenuItem mi;
		mi = new JMenuItem("Save", KeyEvent.VK_S);
		mi.addActionListener(this);
		mu.add(mi);
		mi = new JMenuItem("Tear Off", KeyEvent.VK_T);
		mi.addActionListener(this);
		mu.add(mi);
		mb.add(mu);

		setJMenuBar(mb);

		tearOff();
		// bug in openjdk? does not remember current position
		setLocationByPlatform(true);
		// TODO: make visibility optional... transient
		pack();
		setVisible(false);
	}

	public JFrame getFrame() { return this; }
	public String getName() { return "MDS_LPT"; }

	public void reset() {}
	public int getBaseAddress() { return basePort; }
	public int getNumPorts() { return 2; }
	public int in(int port) {
		int off = port - basePort;
		int val = 0;
		switch (off) {
		case 0:	// all I/O intr status...
			val = fp.inFA();
			break;
		case 1:
			val = STS_STATI | STS_NBUSY;
			break;
		}
		return val;
	}

	public void out(int port, int value) {
		int off = port - basePort;
		switch (off) {
		case 0:
			value ^= 0xff; // data is inverted
			printChar(value);
			break;
		case 1:
			// unknown what this controls...
			break;
		}
	}

	public String getDeviceName() { return "MDS_LPT"; }

	public String dumpDebug() {
		String ret = String.format("MDS LPT port %02x visible=%s\n",
			basePort, visible);
		return ret;
	}

	private void tearOff() {
		text.setText("");
		carr = 0;
		col = 0;
		text.setCaretPosition(carr);
	}

	private void ctrlChar(int c) {
		switch (c) {
		case '\n':	// LF
			text.append("\n");
			++carr;
			text.setCaretPosition(carr);
			// TODO: for now, treat as CR
			col = 0;
			break;
		case '\r':	// CR
// TODO: work out how to do CR...
//			last_cr = carr;
//			carr = last_lf;
//			col = 0;
			break;
		}
	}

	private void printChar(int c) {
		if (!visible) {
			// set visible from menu doesn't register,
			// but at worst we do this once.
			setVisible(true);
		}
		if (c < ' ') {
			ctrlChar(c);
			return;
		}
		// redundant, depending on how we got here.
		if (c > 0x7f) {
			return;
		}
		String s = new String(new char[]{(char)c});
		text.append(s);
		++carr;
		if (++col >= 132) {
			col = 0;
			text.append("\n");
			++carr;
		}
		text.setCaretPosition(carr);
	}

	private File pickFile(String purpose) {
		File file = null;
		SuffFileChooser ch = new SuffFileChooser(purpose, sufx, sufd, _last, null);
		int rv = ch.showDialog(this);
		if (rv == JFileChooser.APPROVE_OPTION) {
			file = ch.getSelectedFile();
		}
		return file;
	}

	public void actionPerformed(ActionEvent e) {
		if (!(e.getSource() instanceof JMenuItem)) {
			return;
		}
		JMenuItem m = (JMenuItem)e.getSource();
		if (m.getMnemonic() == KeyEvent.VK_S) {
			File sav = pickFile("Save");
			if (sav != null) {
				try {
					FileOutputStream fo = new FileOutputStream(sav);
					fo.write(text.getText().getBytes());
					fo.close();
					_last = sav;
					// TODO: tear off?
				} catch (Exception ee) {
					// ...
				}
			}
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_T) {
			tearOff();
			return;
		}
	}

	@Override
	public void setVisible(boolean vis) {
		visible = vis;
		super.setVisible(vis);
	}
}
