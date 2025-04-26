// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Vector;
import java.util.Properties;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;

// MDS High Speed Paper Tape
public class PaperTape extends RackUnit implements IODevice,
				WindowListener, ActionListener {
	// by mutual agreement with any TTY-attached peripheral
	private static final int RDR = 0x0f;	// ^O - new: advance reader once

	private Interruptor intr;
	INS8251 tty;
	private int irq;
	private int src;
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

	private int ptrChar;
	private int ptpChar;
	private boolean ptrRdy;
	private boolean ptpRdy;
	private int tty_rdr_adv_char;
	private int rdr_view;

	private ReaderDevice reader;
	private PunchDevice punch;
	static final Color norm = RackUnit.BG;
	static final Color actv = RackUnit.BG.brighter();
	static final Color normHi = actv.brighter();
	static final Color normLo = Color.black;
	static final String[] sufx = { "txt" };
	static final String[] sufd = { "Paper Tape" };
	File last = null;

	abstract class DeviceButton extends JLabel implements MouseListener {
		int id;
		ActionListener lstr;
		ActionListener plstr; // re-positioning listener (if any)

		public DeviceButton(String name, int id, ActionListener lstr) {
			super(name);
			setHorizontalAlignment(SwingConstants.CENTER);
			this.lstr = lstr;
			this.id = id;
			setOpaque(true);
			setBackground(norm);
			setForeground(Color.white);
			setPreferredSize(new Dimension(350, 50));
			setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED,
				normHi, normHi, normLo, normLo));
			addMouseListener(this);
		}

		public void setPositioningListener(ActionListener lstr) {
			plstr = lstr;
		}

		public int getId() { return id; }

		abstract public void setFile(File f);

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
			if (e.getButton() == MouseEvent.BUTTON1) {
				if (lstr != null) {
					lstr.actionPerformed(new ActionEvent(this,
						e.getID(), "pt"));
				}
			} else if (e.getButton() == MouseEvent.BUTTON3) {
				if (plstr != null) {
					plstr.actionPerformed(new ActionEvent(this,
						e.getID(), "ptp"));
				}
			}
		}
	}

	class ReaderDevice extends DeviceButton {
		public File file;
		public RandomAccessFile in;
		public boolean busy;

		public ReaderDevice(String name, int id, ActionListener lstr) {
			super(name, id, lstr);
			setFile(null);
		}

		public void setFile(File f) {
			file = f;
			if (f == null) {
				setToolTipText("(none)");
				in = null;
				return;
			}
			setToolTipText(file.getName());
			try {
				in = new RandomAccessFile(file, "r");
			} catch (Exception e) { }
		}
	}

	class PunchDevice extends DeviceButton {
		public File file;
		public OutputStream out;

		public PunchDevice(String name, int id, ActionListener lstr) {
			super(name, id, lstr);
			setFile(null);
		}

		public void setFile(File f) {
			file = f;
			if (f == null) {
				setToolTipText("(none)");
				out = null;
				return;
			}
			setToolTipText(file.getName());
			try {
				out = new FileOutputStream(file);
			} catch (Exception e) { }
		}
	}

	public PaperTape(Properties props, int base, int irq,
			Interruptor intr, INS8251 tty) {
		super(2);	// nothing fancy, yet
		setBackground(RackUnit.BLUE);
		name = "PTR_PTP";
		this.intr = intr;
		this.irq = irq;
		this.tty = tty;
		tty_rdr_adv_char = RDR;
		src = intr.registerINT(irq);
		last = new File(System.getProperty("user.dir"));
		basePort = base;
		reader = new ReaderDevice("READER", 1, this);
		reader.setPositioningListener(this);
		punch = new PunchDevice("PUNCH", 2, this);
		String s;
		s = props.getProperty("ptp_att");
		if (s != null) {
			punch.setFile(new File(s));
		}
		s = props.getProperty("ptr_att");
		if (s != null) {
			reader.setFile(new File(s));
		}
		s = props.getProperty("mds800_rdr_adv_char");
		if (s == null) {
			s = props.getProperty("tty_rdr_adv_char");
		}
		if (s != null) {
			tty_rdr_adv_char = Integer.decode(s) & 0xff;
		}
		rdr_view = 8;
		s = props.getProperty("ptp_rdr_view");
		if (s != null) {
			rdr_view = Integer.valueOf(s);
			if (rdr_view < 1) rdr_view = 1;
			if (rdr_view > 30) rdr_view = 30; // what is practical...
		}
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
		gc.gridx = 1;
		gc.gridy = 1;
		gb.setConstraints(reader, gc);
		add(reader);
		++gc.gridx;
		JPanel pan = new JPanel();
		pan.setOpaque(false);
		pan.setPreferredSize(new Dimension(60, 20));
		gb.setConstraints(pan, gc);
		add(pan);
		++gc.gridx;
		gb.setConstraints(punch, gc);
		add(punch);

		reset();
	}

	// Conditions affecting interrupts have changed, ensure proper signal.
	private void chkIntr() {
		// TOD: implement interrupts
	}

	private int getStatus() {
		int val = 0;
		if (reader.in != null && !reader.busy) { // file ready to read
			val |= STS_PTR_RDY;
		}
		if (punch.out != null) {	// file ready to punch
			val |= STS_PTP_RDY;
		}
		if (ptrRdy) {
			val |= STS_PTR_RXD;
		}
		if (ptpRdy) {
			val |= STS_PTP_TXE;
		}
		return val;
	}

	///////////////////////////////
	/// Interfaces for IODevice ///
	public int in(int port) {
		int off = port - basePort;
		int val = 0;
		switch(off) {
		case 0: // Rx Data
			// TODO: forces ptrRdy off?
			val = ptrChar;
			break;
		case 1:
			val = getStatus();
			break;
		}
		return val;
	}

	public void out(int port, int val) {
		int off = port - basePort;
		val &= 0xff; // necessary?
		switch(off) {
		case 0: // Tx Data
			ptpChar = val;
			break;
		case 1:
			if ((val & CTL_TTY_ADV) != 0) {
				if (tty != null) {
					tty.out(tty.getBaseAddress(), tty_rdr_adv_char);
				}
			}
			if ((val & CTL_PTR_DRV) != 0) {
				// TODO: handle direction?
				ptrRdy = false;
				try {
					if (reader.in != null && !reader.busy) {
						int c = reader.in.read();
						if (c >= 0) {
							ptrChar = c;
							ptrRdy = true; // instantly ready
						}
					}
				} catch (Exception e) {}
			}
			if ((val & CTL_PTP_ADV) != 0) {
				// TODO: handle direction?
				ptpRdy = false;
				if (punch.out != null) {
					try {
						punch.out.write((byte)ptpChar);
					} catch (Exception e) {}
					ptpRdy = true; // instantly ready
				}
			}
			break;
		}
	}

	public void reset() {
		ptrRdy = false; // until strobed
		ptpRdy = (punch.out != null);
		chkIntr();
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
		ret += String.format("port %02x, status %02x\n",
			basePort, getStatus());
		ret += String.format("punch %s, reader %s\n",
			punch.file != null ? punch.file.getName() : "(none)",
			reader.file != null ? reader.file.getName() : "(none)");
	
		return ret;
	}

	private File pickFile(String purpose) {
		File file = null;
		SuffFileChooser ch = new SuffFileChooser(purpose, sufx, sufd, last, null);
		int rv = ch.showDialog(this);
		if (rv == JFileChooser.APPROVE_OPTION) {
			file = ch.getSelectedFile();
		}
		return file;
	}

	public void actionPerformed(ActionEvent e) {
		DeviceButton fd = (DeviceButton)e.getSource();
		String act = e.getActionCommand();
		if (act.equals("ptp")) {
			if (reader.in == null) {
				return;
			}
			reader.busy = true;
			JFrame jf = new PaperTapePositioner(this, reader.in, rdr_view, this);
			return;
		}
		fd.setToolTipText("(none)");
		File f = pickFile("Mount Paper Tape");
		if (f == null) {
			if (fd.getId() == 1) {
				ptrRdy = false;
			} else {
				ptpRdy = false;
			}
			fd.setFile(null);
			return;
		}
		fd.setFile(f);
	}

	public void windowActivated(WindowEvent e) { }
	public void windowClosed(WindowEvent e) { }
	public void windowIconified(WindowEvent e) { }
	public void windowOpened(WindowEvent e) { }
	public void windowDeiconified(WindowEvent e) { }
	public void windowDeactivated(WindowEvent e) { }
	public void windowClosing(WindowEvent e) {
		reader.busy = false;
	}
}
