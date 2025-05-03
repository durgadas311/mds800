// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.io.*;
import java.util.Properties;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.border.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

public class ASR33 extends JFrame implements KeyListener, MouseListener,
			ActionListener, WindowListener, Runnable {
	private static final int BEL = 0x07;
	private static final int DC1 = 0x11;	// ^Q - XON
	private static final int DC2 = 0x12;	// ^R - P-ON
	private static final int DC3 = 0x13;	// ^S - XOFF
	private static final int DC4 = 0x14;	// ^T - P-OFF
	private static final int WRU = 0x05;	// ^E (ENQ)
	private static final int RDR = 0x0f;	// ^O - new: advance reader once

	static final String title = "Virtual ASR33 Teletype";
	static final String[] sufx = { "txt" };
	static final String[] sufd = { "Text" };
	static final Color lighted = new Color(255, 255, 200);

	ASR33Container fe;
	InputStream idev;
	OutputStream odev;
	Thread thrd;
	JTextArea text;
	FontMetrics fm;
	int fh;
	int fw;
	JScrollPane scroll;
	int carr;
	int col;
	int bol;
	int eol;
	File _last = null;
	GenericHelp _help;

	JCheckBox local;
	JCheckBox pun;
	JLabel pun_cnt;
	int pun_bytes;
	JCheckBox rdr;
	JButton rdr_start;
	boolean rdr_busy;
	JLabel rdr_cnt;
	int rdr_bytes;
	JMenuItem pun_mi;
	JMenuItem rdr_mi;
	JMenuItem rdr_pos;
	OutputStream pun_out;
	RandomAccessFile rdr_in;
	JLabel spinner;
	int spinning;
	static final String[] spins = new String[]{ "/", "\u2013", "\\", "|" };

	byte[] ansbak;
	int paste_delay;
	int paste_cr_delay;
	int rdr_view;
	Reader reader;
	Paster paster;
	int rdr_adv_char;
	Bell bell;

	class Paster implements Runnable {
		Thread thr;
		java.util.concurrent.LinkedBlockingDeque<String> fifo;
		boolean cancel;

		public Paster() {
			fifo = new java.util.concurrent.LinkedBlockingDeque<String>();
			thr = new Thread(this);
			thr.start();
		}

		public void addText(byte[] txt) {
			String s = new String(txt);
			fifo.add(s);
		}

		public void addText(String txt) {
			fifo.add(txt);
		}

		public synchronized void cancel() {
			cancel = true;
			fifo.clear();
		}

		public void run() {
			String s;
			while (true) {
				try {
					s = fifo.take();
					synchronized (this) {
						cancel = false; // assume stale
					}
				} catch (Exception ee) {
					ee.printStackTrace();
					break;
				}
				for (int x = 0; x < s.length(); ++x) {
					synchronized (this) {
						if (cancel) break;
					}
					typeChar((int)s.charAt(x));
					try {
						Thread.sleep(paste_delay);
					} catch (Exception ee) {}
				}
			}
		}
	};

	class Reader implements Runnable {
		Thread thr;
		boolean running;
		Semaphore wait;

		public Reader() {
			wait = new Semaphore(0);
			thr = new Thread(this);
			thr.start();
		}

		private synchronized void setRunning(boolean b) { running = b; }

		public synchronized boolean isRunning() { return running; }

		public void start() {
			setRunning(true);
			wait.release();
		}

		public void stop() {
			setRunning(false);
		}

		public void run() {
			int c;

			while (true) {
				while (!isRunning()) {
					try {
						wait.acquire();
					} catch (Exception ee) {
						ee.printStackTrace();
						break;
					}
				}
				try {
					c = rdr_in.read();
				} catch (Exception ee) {
					c = -1;
				}
				if (c < 0) {
					// notify ASR33...
					rdrStop();
					continue;
				}
				rdr_cnt.setText(String.format("%4d", ++rdr_bytes));
				typeChar(c);
				try {
					Thread.sleep(paste_delay);
				} catch (Exception ee) {}
			}
		}
	};

	class Bell implements Runnable {
		java.util.concurrent.LinkedBlockingDeque<Integer> fifo;
		SourceDataLine line;
		byte[] buf;
		int frame;

		public Bell(Properties props) {
			fifo = new java.util.concurrent.LinkedBlockingDeque<Integer>();
			String s = props.getProperty("asr33_bell");
			if (s == null) {
				s = "ding.wav";
			} else if (s.length() == 0) {
				line = null;
				return;
			}
			String bell_wav = s;
			try {
				InputStream is = SimResource.open(this, bell_wav);
				AudioInputStream wav =
					AudioSystem.getAudioInputStream(
						new BufferedInputStream(is));
				AudioFormat format = wav.getFormat();
				//frame = (int)wav.getFrameLength();
				buf = new byte[wav.available()];
				wav.read(buf);
				wav.close();

				DataLine.Info info = new DataLine.Info(
					SourceDataLine.class, format);
				line = (SourceDataLine)AudioSystem.getLine(info);
				line.open(format);
				frame = format.getFrameSize();
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
			int volume = 50;
			s = props.getProperty("asr33_bell_volume");
			if (s != null) {
				volume = Integer.valueOf(s);
				if (volume < 0) volume = 0;
				if (volume > 100) volume = 100;
			}
			FloatControl vol = null;
			if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
				vol = (FloatControl)line.getControl(FloatControl.Type.MASTER_GAIN);
			} else if (line.isControlSupported(FloatControl.Type.VOLUME)) {
				vol = (FloatControl)line.getControl(FloatControl.Type.VOLUME);
			}
			if (vol != null) {
				float min = vol.getMinimum();
				float max = vol.getMaximum();
				float gain = (float)(min + ((max - min) * (volume / 100.0)));
				vol.setValue(gain);
			} else {
				System.err.format("ASR33:Bell: no volume control\n");
			}
			Thread t = new Thread(this);
			t.start();
		}

		public void ding() {
			if (line == null) return;
			fifo.add(0);
		}

		public void run() {
			int idx;
			int max = buf.length - frame;
			int n;

			while (true) {
				try {
					idx = fifo.take(); // usually/always "0"
					// at most we block for 1 frame
					while (fifo.size() == 0 && idx < max) {
						n = frame;
						line.write(buf, idx, n);
						if (idx == 0) {
							line.start();
						}
						idx += n;
					}
					line.stop();
					line.flush();
				} catch (Exception ee) {
					ee.printStackTrace();
				}
			}
		}
	}

	class BlockCaret extends DefaultCaret {
		static final Color shadow = new Color(50, 50, 50, 100);
		public BlockCaret() {}

		public void paint(Graphics g) {
			JTextComponent comp = getComponent();
			Rectangle2D r = null;
			try {
				r = comp.modelToView2D(getDot());
			} catch(Exception ee) { }
			if (r == null) return;
			int x = (int)r.getX();
			int y = (int)r.getY();
			g.setColor(shadow);
			g.fillRect(x, y, fw - 1, fh);
		}

		@Override
		public void setDot(int dot, Position.Bias dotBias) {
			// prevent cursor keys from changing caret
			if (dot < carr) return;
			super.setDot(dot, dotBias);
		}

		// prevent mouse from changing caret
		@Override
		protected void positionCaret(MouseEvent e) { }
	};

	public ASR33(Properties props, ASR33Container fe) {
		super(title + " - " + fe.getTitle());
		this.fe = fe;
		idev = fe.getInputStream();
		odev = fe.getOutputStream();
		ansbak = new byte[20];
		paste_delay = 100; // mS, 10 char/sec
		paste_cr_delay = 1000; // mS, wait after CR
		rdr_adv_char = RDR;
		paster = new Paster();
		reader = new Reader();
		bell = new Bell(props);
		_last = new File(System.getProperty("user.dir"));
		getContentPane().setName("ASR33 Emulator");
		getContentPane().setBackground(new Color(100, 100, 100));

		String s = props.getProperty("asr33_ansbak");
		if (s != null) {
			setAnswerBack(s);
		}
		s = props.getProperty("asr33_delay");
		if (s != null) {
			paste_delay = Integer.valueOf(s);
		}
		s = props.getProperty("asr33_cr_delay");
		if (s != null) {
			paste_cr_delay = Integer.valueOf(s);
		}
		s = props.getProperty("asr33_rdr_adv_char");
		if (s == null) {
			s = props.getProperty("tty_rdr_adv_char");
		}
		if (s != null) {
			rdr_adv_char = Integer.decode(s) & 0xff;
		}
		rdr_view = 8;
		s = props.getProperty("asr33_rdr_view");
		if (s != null) {
			rdr_view = Integer.valueOf(s);
			if (rdr_view < 1) rdr_view = 1;
			if (rdr_view > 30) rdr_view = 30; // what is practical...
		}

		java.net.URL url;
		url = this.getClass().getResource("asr33_doc/help.html");
		_help = new GenericHelp("ASR33 Teletype Help", url);

		setLayout(new BorderLayout()); // allow resizing
		text = new JTextArea(24, 81); // a little wider for breathing room
		text.setEditable(false); // this prevents caret... grrr.
		text.setBackground(Color.white);
		Font font = new Font("Monospaced", Font.PLAIN, 12);
		setupFont(font);
		text.setCaret(new BlockCaret());
		text.addKeyListener(this);
		text.addMouseListener(this);
		scroll = new JScrollPane(text);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		add(scroll, BorderLayout.CENTER); // allow resizing

		JMenuBar mb = new JMenuBar();
		JMenu mu = new JMenu("Print");
		JMenu main = mu;
		JMenuItem mi = new JMenuItem("Copy ", KeyEvent.VK_C);
		mi.setAccelerator(KeyStroke.getKeyStroke('C', InputEvent.ALT_DOWN_MASK));
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mi = new JMenuItem("Paste", KeyEvent.VK_V);
		mi.setAccelerator(KeyStroke.getKeyStroke('V', InputEvent.ALT_DOWN_MASK));
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mi = new JMenuItem("Save", KeyEvent.VK_S);
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mi = new JMenuItem("Tear Off", KeyEvent.VK_T);
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mb.add(mu);
		mu = new JMenu("PTape");
		mi = new JMenuItem("Punch", KeyEvent.VK_P);
		pun_mi = mi;
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mi = new JMenuItem("Reader", KeyEvent.VK_R);
		rdr_mi = mi;
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mi = new JMenuItem("Rdr Position", KeyEvent.VK_Z);
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		rdr_pos = mi;
		mb.add(mu);
		fe.addMenus(mb, main, this);
		rdr_pos.setEnabled(false); // until tape in reader...

		JPanel pn = new JPanel();
		pn.setPreferredSize(new Dimension(5, 30));
		pn.setOpaque(false);
		mb.add(pn);
		spinner = new JLabel(spins[0]);
		spinner.setFont(font);
		spinner.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		spinning = 0;
		mb.add(spinner);

		pn = new JPanel();
		pn.setPreferredSize(new Dimension(5, 30));
		pn.setOpaque(false);
		mb.add(pn);
		local = new JCheckBox("LOCAL");
		local.setFocusable(false);
		//local.addActionListener(this);
		local.setOpaque(false);
		mb.add(local);
		pn = new JPanel();
		pn.setPreferredSize(new Dimension(10, 30));
		pn.setOpaque(false);
		mb.add(pn);
		pun = new JCheckBox("PUNCH");
		pun.setFocusable(false);
		//pun.addActionListener(this);
		pun.setEnabled(false);
		pun.setOpaque(false);
		mb.add(pun);
		pun_cnt = new JLabel("    ");
		pun_cnt.setFont(font);
		pun_cnt.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		mb.add(pun_cnt);
		pn = new JPanel();
		pn.setPreferredSize(new Dimension(10, 30));
		pn.setOpaque(false);
		mb.add(pn);
		rdr = new JCheckBox("READER");
		rdr.setFocusable(false);
		rdr.addActionListener(this);
		rdr.setEnabled(false);
		rdr.setOpaque(false);
		mb.add(rdr);
		rdr_start = new JButton("start");
		rdr_start.setFocusable(false);
		rdr_start.addActionListener(this);
		rdr_start.setEnabled(false);
		rdr_start.setOpaque(false);
		mb.add(rdr_start);
		rdr_cnt = new JLabel("    ");
		rdr_cnt.setFont(font);
		rdr_cnt.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		mb.add(rdr_cnt);

		pn = new JPanel();
		pn.setPreferredSize(new Dimension(10, 30));
		pn.setOpaque(false);
		mb.add(pn);

		mu = new JMenu("Help");
		mi = new JMenuItem("Show Help", KeyEvent.VK_H);
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mb.add(mu);

		setJMenuBar(mb);

		tearOff();
		// bug in openjdk? does not remember current position
		setLocationByPlatform(true);

		restart();
	}

	private void setupFont(Font f) {
		text.setFont(f);
		fm = text.getFontMetrics(f);
		//fa = fm.getAscent();
		fw = fm.charWidth('M');
		fh = fm.getHeight();
	}

	private synchronized void restart() {
		setTitle(title + " - " + fe.getTitle());
		idev = fe.getInputStream();
		odev = fe.getOutputStream();
		if (idev != null) {
			if (thrd != null && thrd.isAlive()) {
				// now we have a problem, and java doesn't help
				thrd.interrupt(); // probably does nothing
				// we have to hope it noticed closed InputStream
			}
			thrd = new Thread(this);
			thrd.start();
		} else if (thrd != null) {
			if (thrd.isAlive()) {
				thrd.interrupt(); // probably does nothing
			}
			// we have to hope it noticed closed InputStream
			thrd = null;
		}
	}

	public void setFocus() {
		text.requestFocus();
	}

	private void setAnswerBack(String s) {
		byte[] n = s.getBytes();
		int y = 0;
		for (int x = 0; x < n.length && y < 20; ++x) {
			byte c = n[x];
			if (c == '\\') {
				if (x + 1 < n.length) {
					c = n[++x];
					switch (c) {
					case 'r': c = '\r'; break;
					case 'n': c = '\n'; break;
					case 'b': c = '\b'; break;
					case 't': c = '\t'; break;
					case 'f': c = '\f'; break;
					case '\'': c = '\''; break;
					case '"': c = '"'; break;
					}
				}
			}
			ansbak[y++] = c;
		}
	}

	private void newLine() {
		bol = eol = carr;
	}

	// '\n' has been appended, and carr updated. col not changed.
	private void lineFeed() {
		bol = carr;
		eol = bol + col;
		if (col == 0) {
			return;
		}
		byte[] b = new byte[col];
		Arrays.fill(b, (byte)' ');
		text.append(new String(b));
		carr += col;
	}

	// printable characters only
	private void addChar(int c) {
		String s = new String(new char[]{(char)c});
		if (carr < eol) {
			text.replaceRange(s, carr, carr+1);
		} else {
			text.append(s);
			++eol;
		}
		++carr;
		if (++col >= 80) {
			col = 0;
			text.append("\n"); // in this case, we want CR/LF
			++carr;
			newLine();
		}
		text.setCaretPosition(carr);
	}

	private void tearOff() {
		text.setText("");
		carr = 0;
		col = 0;
		newLine();
	}

	private void rdrStart() {
		rdr_start.setBackground(lighted);
		reader.start();
	}

	private void rdrStop() {
		rdr_start.setBackground(null);
		reader.stop();
	}

	private void punChar(int c) {
		if (pun_out != null && pun.isSelected()) {
			try {
				pun_out.write(c);
				pun_cnt.setText(String.format("%4d", ++pun_bytes));
			} catch (Exception ee) {}
		}
	}

	private void ctrlChar(int c) {
		if (c == rdr_adv_char) {
			// special char to implement TTY paper tape read 1 char
			if (rdr_in != null && rdr.isSelected()) {
				try {
					int rc = rdr_in.read();
					if (rc >= 0) {
						rdr_cnt.setText(String.format("%4d", ++rdr_bytes));
						typeChar(rc);
					}
				} catch (Exception ee) {}
			}
			return;
		}
		switch (c) {
		case '\n':	// LF
			text.append("\n");
			if (carr < eol) {
				carr = eol;
			}
			++carr;
			lineFeed();
			text.setCaretPosition(carr);
			break;
		case '\r':	// CR
			carr = bol;
			col = 0;
			text.setCaretPosition(carr);
			break;
		case 0x07:	// BEL
			if (bell != null) {
				bell.ding();
			}
			break;
		case DC2:	// P-ON
			pun.setSelected(true);
			break;
		case DC1:	// X-ON
			if (rdr.isSelected()) {
				rdrStart();
			}
			break;
		case DC4:	// P-OFF
			pun.setSelected(false);
			break;
		case DC3:	// X-OFF
			if (rdr.isSelected()) {
				rdrStop();
			}
			break;
		case WRU:
			paster.addText(ansbak);
			break;
		}
	}

	private void printChar(int c) {
		if (pun.isSelected()) {
			punChar(c);
		}
		if (c < ' ') {
			ctrlChar(c);
			return;
		}
		// redundant, depending on how we got here.
		if (c >= '`' && c < 0x7f) {
			c &= 0x5f;
		}
		if (c > 0x5f) {
			return;
		}
		addChar(c);
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

	public void envChanged() {
		restart();
	}

	public void run() {
		while (true) {
			int c;
			try {
				c = idev.read();
			} catch (Exception ee) {
				//ee.printStackTrace();
				break;
			}
			if (c < 0) {
				//System.err.format("Thread dying from input error\n");
				break;
			}
			if (!local.isSelected()) {
				printChar(c);
			}
		}
		fe.failing(); // notify container of our predicament
		// might race with server going to listen
		setTitle(title + " - " + fe.getTitle());
	}

	protected void typeChar(int c) {
		++spinning;
		spinner.setText(spins[spinning & 3]);
		if (local.isSelected()) {
			printChar(c);
		} else {
			try {
				odev.write((byte)c);
			} catch (Exception ee) {
				// TODO: anything?
			}
		}
	}

	public void keyTyped(KeyEvent e) {}
	public void keyPressed(KeyEvent e) {
		int c = (int)e.getKeyChar();
		int k = e.getKeyCode();
		int m = e.getModifiersEx();

		//System.err.format("keyPressed %02x %04x %04x\n", c, k, m);
		if (k == KeyEvent.VK_F12) {
			paster.addText(ansbak);
			return;
		}
		if (k == KeyEvent.VK_F1) {
			paster.cancel();
			return;
		}
		if ((m & InputEvent.ALT_DOWN_MASK) != 0) {
			// none of these should be sent, but least of all
			// Alt-C and Alt-V for copy/paste.
			return;
		}
		if (c == 0xffff) { // just meta keys
			return;
		}
		// Assume if CTRL is down, must be ^J not ENTER...
		if (k == KeyEvent.VK_ENTER && (m & InputEvent.CTRL_DOWN_MASK) == 0) {
			c = '\r';
		}
		if (k == KeyEvent.VK_DELETE) {
			c = 0x7f;
		}
		// The keyboard can only produce upper case
		if (c >= '`' && c < 0x7f) {
			c &= 0x5f;
		}
		typeChar(c);
	}
	public void keyReleased(KeyEvent e) {}

	private void copyFromTty() {
		String s = text.getSelectedText();
		if (s == null) {
			return;
		}
		StringSelection ss = new StringSelection(s);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
	}

	private void pasteToTty() {
		Transferable t = Toolkit.getDefaultToolkit().
				getSystemClipboard().getContents(null);
		try {
			if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
				String text = (String)t.getTransferData(DataFlavor.stringFlavor);
				paster.addText(text);
			}
		} catch (Exception ee) {
		}
	}

	public void mouseClicked(MouseEvent e) {
		if (e.getButton() != MouseEvent.BUTTON2) {
			return;
		}
		pasteToTty();
	}
	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e) { }
	public void mousePressed(MouseEvent e) { }
	public void mouseReleased(MouseEvent e) { }
	public void mouseDragged(MouseEvent e) { }
	public void mouseMoved(MouseEvent e) { }

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() instanceof JButton) {
			if (e.getSource() == rdr_start) {
				if (reader.isRunning()) {
					rdrStop();
				} else {
					rdrStart();
				}
			}
		}
		if (e.getSource() instanceof JCheckBox) {
			if (e.getSource() == rdr) {
				if (rdr_busy) {
					rdr.setSelected(false);
					return;
				}
				if (!rdr.isSelected()) {
					rdrStop();
				}
				rdr_start.setEnabled(rdr.isSelected());
			}
			return;
		}
		if (!(e.getSource() instanceof JMenuItem)) {
			return;
		}
		JMenuItem m = (JMenuItem)e.getSource();
		if (!m.getActionCommand().equals(".")) {
			if (!fe.menuActions(m)) {
				return; // no changes for us
			}
			envChanged();
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_C) {
			copyFromTty();
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_V) {
			pasteToTty();
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_S) {
			File sav = pickFile("Save");
			if (sav != null) {
				try {
					FileOutputStream fo = new FileOutputStream(sav);
					fo.write(text.getText(0, carr).getBytes());
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
		if (m.getMnemonic() == KeyEvent.VK_P) {
			if (pun_out != null) try {
				pun_out.close();
			} catch (Exception ee) {}
			pun_cnt.setText("    ");
			pun_bytes = 0;
			pun_mi.setText("Punch");
			pun_out = null;
			pun.setSelected(false);
			pun.setEnabled(false);
			File file = pickFile("Punch");
			if (file == null) {
				return;
			}
			try {
				pun_out = new FileOutputStream(file);
				pun_mi.setText("Punch - " + file.getName());
				pun.setEnabled(true);
				pun_cnt.setText("   0");
				pun_bytes = 0;
			} catch (Exception ee) {
				System.err.println(ee.getMessage());
			}
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_R) {
			if (rdr_in != null) try {
				rdr_in.close();
			} catch (Exception ee) {}
			rdr_cnt.setText("    ");
			rdr_bytes = 0;
			rdr_mi.setText("Reader");
			rdr_in = null;
			rdr.setSelected(false);
			rdr.setEnabled(false);
			rdr_pos.setEnabled(false);
			rdr_start.setEnabled(false);
			File file = pickFile("Reader");
			if (file == null) {
				return;
			}
			try {
				rdr_in = new RandomAccessFile(file, "r");
				rdr_mi.setText("Reader - " + file.getName());
				rdr_cnt.setText("   0");
				rdr_bytes = 0;
				rdr.setEnabled(true);
				rdr_pos.setEnabled(true);
			} catch (Exception ee) {
				System.err.println(ee.getMessage());
			}
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_Z) {
			if (rdr_in == null) {
				return;
			}
			rdrStop(); // just in case
			rdr.setSelected(false);
			rdr_start.setEnabled(false);
			JFrame jf = new PaperTapePositioner(this, rdr_in, 8, this);
			// cannot use reader until this finishes...
			rdr_busy = true;
		}
		if (m.getMnemonic() == KeyEvent.VK_H) {
			if (_help != null) {
				_help.setVisible(true);
			}
			return;
		}
	}

	public void windowActivated(WindowEvent e) { }
	public void windowClosed(WindowEvent e) { }
	public void windowIconified(WindowEvent e) { }
	public void windowOpened(WindowEvent e) { }
	public void windowDeiconified(WindowEvent e) { }
	public void windowDeactivated(WindowEvent e) { }
	public void windowClosing(WindowEvent e) {
		// rdr_in has new position set
		try {
			rdr_bytes = (int)rdr_in.getFilePointer();
		} catch (Exception ee) {}
		rdr_cnt.setText(String.format("%4d", rdr_bytes));
		rdr_busy = false;
	}
}
