// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

// Implements overall ADM3A and Kayboard

import java.io.*;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.border.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import javax.sound.sampled.*;

public class ADM3A extends JFrame implements KeyListener, MouseListener,
			ActionListener, Runnable {
	static final String title = "Virtual ADM3A Terminal";

	ASR33Container fe;
	InputStream idev;
	OutputStream odev;
	Thread thrd;
	GenericHelp help;

	byte[] ansbak;
	boolean loopback;
	int paste_delay;
	int paste_cr_delay;
	File _last;
	Paster paster;

	ADM3ACrtScreen crt;
	int curs_x;
	int curs_y;

	modes mode;

	enum modes {
		NORMAL,
		ESC,
		SETCURS1,
		SETCURS2,
	};

	Bell bell;
	OutputStream log = null;

	boolean auto_nl;
	boolean uc_only;
	boolean uc_disp;
	boolean clr_scr;
	boolean ndspace;
	boolean parity;
	boolean even;

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
	}

	class Bell implements ActionListener {
		Clip beep;
		private javax.swing.Timer timer;

		public Bell(Properties props) {
			timer = new javax.swing.Timer(250, this);
			String s = props.getProperty("adm3a_beep");
			if (s == null) {
				s = "adm3a_beep.wav";
			} else if (s.length() == 0) {
				beep = null;
				return;
			}
			String beep_wav = s;
			try {
				InputStream is = SimResource.open(this, beep_wav);
				AudioInputStream wav =
					AudioSystem.getAudioInputStream(
						new BufferedInputStream(is));
				AudioFormat format = wav.getFormat();
				DataLine.Info info = new DataLine.Info(Clip.class, format);
				beep = (Clip)AudioSystem.getLine(info);
				beep.open(wav);
				//beep.setLoopPoints(0, loop);
			} catch (Exception ee) {
				ee.printStackTrace();
				beep = null;
				return;
			}
			int volume = 50;
			s = props.getProperty("adm3a_beep_volume");
			if (s != null) {
				volume = Integer.valueOf(s);
				if (volume < 0) volume = 0;
				if (volume > 100) volume = 100;
			}
			FloatControl vol = null;
			if (beep.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
				vol = (FloatControl)beep.getControl(FloatControl.Type.MASTER_GAIN);
			} else if (beep.isControlSupported(FloatControl.Type.VOLUME)) {
				vol = (FloatControl)beep.getControl(FloatControl.Type.VOLUME);
			}
			if (vol != null) {
				float min = vol.getMinimum();
				float max = vol.getMaximum();
				float gain = (float)(min + ((max - min) * (volume / 100.0)));
				vol.setValue(gain);
			} else {
				System.err.format("ADM3A:Bell: no volume control\n");
			}
		}

		// TODO: race condition: ding() and actionPerformed()
		// race such that ding() restarts timer and audio but then
		// actionPerformed() cancels it.
		public synchronized void ding() {
			timer.removeActionListener(this);
			timer.addActionListener(this);
			timer.restart();
			if (!beep.isActive()) {
				beep.loop(Clip.LOOP_CONTINUOUSLY);
			}
		}

		public void actionPerformed(ActionEvent e) {
			if (e.getSource() != timer) {
				return;
			}
			synchronized(this) {
				timer.removeActionListener(this);
				beep.stop();
				beep.flush();
				beep.setFramePosition(0);
			}
		}
	}

	public ADM3A(Properties props, ASR33Container fe) {
		super(title + " - " + fe.getTitle());
		this.fe = fe;
		idev = fe.getInputStream();
		odev = fe.getOutputStream();
		ansbak = new byte[20];
		paste_delay = 100; // mS, 10 char/sec
		paste_cr_delay = 1000; // mS, wait after CR
		_last = new File(System.getProperty("user.dir"));
		paster = new Paster();
		bell = new Bell(props);

		getContentPane().setName("ADM3A Emulator");
		getContentPane().setBackground(new Color(100, 100, 100));
		setFocusTraversalKeysEnabled(false); // for TAB

		String s = props.getProperty("adm3a_ansbak");
		if (s != null) {
			setAnswerBack(s);
		}
		loopback = (props.getProperty("adm3a_loopback") != null);
		s = props.getProperty("adm3a_log");
		if (s != null) {
			String[] ss = s.split("\\s");
			boolean append = false;
			for (int x = 1; x < ss.length; ++x) {
				if (ss[x].equalsIgnoreCase("a")) {
					append = true;
				}
			}
			try {
				log = new FileOutputStream(ss[0], append);
			} catch (Exception ee) {
				System.err.format("Failed to setup adm3a_log file %s\n",
						ss[0]);
			}
		}
		s = props.getProperty("adm3a_delay");
		if (s != null) {
			paste_delay = Integer.valueOf(s);
		}
		s = props.getProperty("adm3a_cr_delay");
		if (s != null) {
			paste_cr_delay = Integer.valueOf(s);
		}
		// various terminal options
		auto_nl = true;
		uc_only = false;	// keyboard codes (alpha only)
		uc_disp = false;	// displayed characters (alpha only)
		clr_scr = true;
		ndspace = false;	// non-destructive space
		parity = false;
		even = false;	// default to SPACE if no parity
		s = props.getProperty("adm3a_auto_nl"); // "wrap" at EOL
		if (s != null) auto_nl = ExtBoolean.parseBoolean(s);
		s = props.getProperty("adm3a_uc_only"); // keyboard gen only UC
		if (s != null) uc_only = ExtBoolean.parseBoolean(s);
		s = props.getProperty("adm3a_uc_disp"); // display only UC
		if (s != null) uc_disp = ExtBoolean.parseBoolean(s);
		s = props.getProperty("adm3a_clr_scr"); // enable CLEAR SCREEN
		if (s != null) clr_scr = ExtBoolean.parseBoolean(s);
		s = props.getProperty("adm3a_ndspace"); // non-destructive space (*)
		if (s != null) ndspace = ExtBoolean.parseBoolean(s);
		s = props.getProperty("adm3a_pe");
		if (s != null) parity = ExtBoolean.parseBoolean(s);
		if (parity) {
			even = true;	// default to EVEN parity
		}
		s = props.getProperty("adm3a_ep");
		if (s != null) even = ExtBoolean.parseBoolean(s);

		JMenuBar mb = new JMenuBar();
		JMenu mu = new JMenu("Screen");
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
		mi = new JMenuItem("Copy All", KeyEvent.VK_S);
		mi.setAccelerator(KeyStroke.getKeyStroke('A', InputEvent.ALT_DOWN_MASK));
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mb.add(mu);

		java.net.URL url;
		url = this.getClass().getResource("adm3a_doc/help.html");
		help = new GenericHelp("ADM3A Terminal Help", url);
		mu = new JMenu("Help");
		mi = new JMenuItem("Show Help", KeyEvent.VK_H);
		mi.addActionListener(this);
		mi.setActionCommand(".");
		mu.add(mi);
		mb.add(mu);

		fe.addMenus(mb, main, this);
		setJMenuBar(mb);

		// bug in openjdk? does not remember current position
		setLocationByPlatform(true);
		addKeyListener(this);

		crt = new ADM3ACrtScreen(props);
		//crt.addKeyListener(this);
		crt.addMouseListener(this);
		JPanel pan = setupScreen();
		add(pan);

		pack();

		reset();
		restart();
	}

	private JPanel setupScreen() {
		int gaps;
		float rounding;
		int width, height;
		int offset;

		JPanel pnl = new JPanel();
		pnl.setBackground(getContentPane().getBackground());
		pnl.setOpaque(true);
		Dimension dim = crt.getPreferredSize();
		gaps = dim.width / 50;
		if (gaps < 5) gaps = 5;
		offset = dim.width / 40;
		rounding = offset * 2;
		width = Math.round(dim.width + 2 * offset);
		height = Math.round(dim.height + 2 * offset);

		GridBagLayout gridbag = new GridBagLayout();
		pnl.setLayout(gridbag);
		GridBagConstraints gc = new GridBagConstraints();
		gc.fill = GridBagConstraints.NONE;
		gc.gridx = 0;
		gc.gridy = 0;
		gc.weightx = 0;
		gc.weighty = 0;
		gc.gridwidth = 1;
		gc.gridheight = 1;
		gc.anchor = GridBagConstraints.NORTH;
		JPanel pan;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(gaps, gaps));
		pan.setOpaque(false);
		gc.gridx = 0;
		gc.gridwidth = 3;
		gridbag.setConstraints(pan, gc);
		gc.gridwidth = 1;
		pnl.add(pan);
		++gc.gridy;

		pan = new JPanel();
		pan.setPreferredSize(new Dimension(gaps, gaps));
		pan.setOpaque(false);
		gc.gridx = 0;
		gridbag.setConstraints(pan, gc);
		pnl.add(pan);

		BezelRoundedRectangle tube = new BezelRoundedRectangle(crt.getBackground(),
			getContentPane().getBackground(), true,
			0f, 0f, width, height,
			rounding, rounding);
		GridBagLayout gb = new GridBagLayout();
		tube.setLayout(gb);
		tube.setOpaque(false);
		gridbag.setConstraints(crt, gc);
		tube.add(crt);
		gc.gridx = 1;
		gridbag.setConstraints(tube, gc);
		pnl.add(tube);

		pan = new JPanel();
		pan.setPreferredSize(new Dimension(gaps, gaps));
		pan.setOpaque(false);
		gc.gridx = 2;
		gridbag.setConstraints(pan, gc);
		pnl.add(pan);

		++gc.gridy;
		gc.gridx = 0;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(gaps, gaps));
		pan.setOpaque(false);
		gc.gridwidth = 3;
		gridbag.setConstraints(pan, gc);
		gc.gridwidth = 1;
		pnl.add(pan);

		return pnl;
	}

	// is this ever called after ctor?
	private void reset() {
		curs_x = 0;
		curs_y = 0;
		crt.setCursor(curs_x, curs_y);
		crt.clearScreen();
		mode = modes.NORMAL;
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
		crt.requestFocus();
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

	public void envChanged() {
		restart();
	}

	public void set_curs_xy(int x, int y) {
		set_curs_y(y);
		set_curs_x(x);
		crt.setCursor(curs_x, curs_y);
	}

	private void set_curs_y(int c) {
		if (c < 24) {
			curs_y = c;
		}
	}

	private void set_curs_x(int c) {
		if (c >= 80) c = 79;
		curs_x = c;
	}

	private void doLinefeed() {
		if (curs_y < 23) {
			++curs_y;
		} else {
			crt.scrollUp();
		}
	}

	// Note: these are ASCII codes, not KeyPress events...
	public void process_keychar(int c) {
		c &= 0x7f; // TODO: check parity? what if wrong?
		if (c == 0x1b) {	// ^[, ESC
			mode = modes.ESC;
			return;
		}
		if (mode == modes.ESC) {
			if (c == '=') {
				mode = modes.SETCURS1;
				return;
			}
			mode = modes.NORMAL;
			return;
		} else if (mode == modes.SETCURS1) {
			set_curs_y((c - ' ') & 0xff);
			mode = modes.SETCURS2;
			return;
		} else if (mode == modes.SETCURS2) {
			set_curs_x((c - ' ') & 0xff);
			crt.setCursor(curs_x, curs_y);
			mode = modes.NORMAL;
			return;
		} else if (c == 0x0a) {	// ^J, LF
			doLinefeed();
		} else if (c == 0x0d) {	// ^M, CR
			curs_x = 0;
		} else if (c == 0x09) {	// ^I, TAB - ignored?!
			if (curs_x < 72) {
				curs_x = curs_x + (8 - (curs_x & 7));
			} else {
				curs_x = 79;
			}
		} else if (c == 0x08) { // ^H, BS
			if (curs_x > 0) {
				--curs_x;
			}
		} else if (c == 0x07) {	// ^G, BEL
			bell.ding();
		} else if (c == 0x0b) {	// ^K, VT (up)
			if (curs_y > 0) {
				--curs_y;
			}
		} else if (c == 0x0c) {	// ^L, FF (right)
			if (curs_x < 79) {
				++curs_x;
			}
		} else if (c == 0x1a && clr_scr) {	// ^Z, SUB (clear)
			crt.clearScreen();
		} else if (c == 0x1c) {	// ^\, RS (home)
			curs_x = 0;
			curs_y = 0;
		} else if (c == 0x05) {	// ^E, ENQ (WRU) - send answerback
			paster.addText(ansbak);
			return;
		} else if (c < ' ') {
			// TODO: handle other Ctrl chars?
			return;
		} else {
			if (uc_disp && c >= 'a' && c <= 'z') {
				c &= 0x5f;
			}
			crt.putChar(c, curs_x, curs_y);
			// TODO: beep at column 72, except if baud > 2400
			if (curs_x < 79) {
				++curs_x;
			} else if (auto_nl) {
				curs_x = 0;
				doLinefeed();
			}
		}
		crt.setCursor(curs_x, curs_y);
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
			// TODO: log char if enabled
			if (log != null) try {
				log.write((byte)c);
			} catch (Exception ee) {}
			process_keychar(c);
		}
		fe.failing(); // notify container of our predicament
		// might race with server going to listen
		setTitle(title + " - " + fe.getTitle());
	}

	private void copyIt(String s) {
		if (s == null) {
			return;
		}
		StringSelection ss = new StringSelection(s);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
	}

	private void copyScreen() {
		String s = crt.dumpScreen(-1);
		copyIt(s);
	}

	private void copyFromCrt() {
		String s = crt.getSelectedText();
		copyIt(s);
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

	private void typeChar(int c) {
		if (loopback) {
			process_keychar(c);
			return;
		}
		if (odev == null) {
			return;
		}
		try {
			odev.write((byte)c);
			odev.flush();
		} catch (Exception ee) {
			ee.printStackTrace();
			// handle? probably means back-end is dead...
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
			copyFromCrt();
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_V) {
			pasteToTty();
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_S) {
			copyScreen();
			return;
		}
		if (m.getMnemonic() == KeyEvent.VK_H) {
			if (help != null) {
				help.setVisible(true);
			}
			return;
		}
	}

        public void keyTyped(KeyEvent e) { }
        public void keyPressed(KeyEvent e) {
                int c = e.getKeyChar();
                int k = e.getKeyCode();
		int m = e.getModifiersEx();
//System.err.format("KEY %02x (%x : %d : %d)\n", (int)c, m, l, k);
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
		// Assume if CTRL is down, must be ^J not ENTER...
		if (k == KeyEvent.VK_ENTER && (m & InputEvent.CTRL_DOWN_MASK) == 0) {
			c = '\r';
		}
		if (k == KeyEvent.VK_BACK_SPACE && (m & InputEvent.SHIFT_DOWN_MASK) != 0) {
			c = 0x7f;
		}
		if (k == KeyEvent.VK_DOWN) {
			c = '\n';
		}
		if (k == KeyEvent.VK_UP) {
			c = 0x0b;	// VT, ^K, '\v' in C
		}
		if (k == KeyEvent.VK_LEFT) {
			c = '\b';
		}
		if (k == KeyEvent.VK_RIGHT) {
			c = '\f';
		}
		if (k == KeyEvent.VK_HOME) {
			c = 0x1c;	// ^\
		}
		if (k == KeyEvent.VK_DELETE && (m & InputEvent.SHIFT_DOWN_MASK) != 0) {
			c = 0x1a;	// ^Z
		}
		if (c == 0xffff) { // special or meta keys
			return;
		}
		if (c < 0x80) {
			if (uc_only && c >= 'a' && c <= 'z') {
				c &= 0x5f;
			}
			if (parity) {
				c = ParityGenerator.parity(c, even);
			} else {
				c = ParityGenerator.noParity(c, even);
			}
			typeChar(c);
			e.consume();
		}
        }
        public void keyReleased(KeyEvent e) { }
}
