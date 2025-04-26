// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;
import java.util.Arrays;

public class PaperTapePositioner extends JFrame
		implements KeyListener, MouseWheelListener, WindowListener {
	RandomAccessFile tape;
	long savedFP;
	byte[] tapeBuf;
	int idx;
	int tot;
	int win;
	int buf;
	int beg;
	int end;

	class PaperTape extends JPanel {
		static final int cell = 15; // spacing of each dot (H/V)
		static final Color buff = new Color(250,240,200);
		int tapew;
		int tapeh;
		int marg;
		int curs;
		int l, t, b, bb, m;	// for paint()

		public PaperTape() {
			super();
			tapew = 9 * cell + 5;
			tapeh = win * cell;
			curs = buf * cell - 1;
			marg = 20;	// total horiz margins (10+10)
			setPreferredSize(new Dimension(tapew + marg, tapeh));
			setBackground(Color.gray);
		}

		// hole order: 0 1 2 s 3 4 5 6 7 8
		public void paint(Graphics g) {
			super.paint(g);
			Graphics2D g2d = (Graphics2D)g;
			g2d.addRenderingHints(new RenderingHints(
				RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON));
			t = (win - end) * cell;
			b = (win - beg) * cell;
			m = marg - 5;
			g2d.setColor(buff);	// paper tape
			// only draw amount of tape present
			g2d.fillRect(marg / 2, t, tapew, (end - beg) * cell);
			g2d.setColor(Color.red); // cursor - fixed position
			g2d.drawRect(0, curs, tapew + marg - 1, cell);
			g2d.setColor(Color.black);
			// draw tapeBuf backwards... End is at top...
			l = end - 1;
			for (int y = t + 3; y < b && l >= beg; y += cell) {
				bb = 0;
				for (int x = 0; x < 9; ++x) {
					if (x == 3) {
						g2d.fillOval(m + (x * cell) + 2, y + 2,
							4, 4);
						continue;
					}
					if ((tapeBuf[l] & (1 << bb)) != 0) {
						g2d.fillOval(m + (x * cell), y, 8, 8);
					}
					++bb;
				}
				--l;
			}
		}
	}

	public PaperTapePositioner(WindowListener lstr, RandomAccessFile tape, int zone) {
		super("PTR");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // ...or live view?
		addWindowListener(this);
		addWindowListener(lstr);
		// TODO: allow save/cancel?
		this.tape = tape;
		buf = zone; // chars before/after cursor
		win = buf * 2 + 1;
		tapeBuf = new byte[win];
		try {
			savedFP = tape.getFilePointer();
			tot = (int)tape.length();
		} catch (Exception ee) {
			// fatal error... ???
			ee.printStackTrace();
			System.exit(1);
		}
		idx = -1;	// never equals savedFP
		add(new PaperTape());
		addKeyListener(this);
		addMouseWheelListener(this);
		pack();
		setVisible(true);
		cacheTape((int)savedFP);
	}

	// newIdx already validated (0 <= newIdx < tot)
	private void cacheTape(int newIdx) {
		int pos = newIdx - buf;
		int _beg = 0;
		int _end = win;

		if (newIdx == idx) {
			return;
		}
		if (pos < 0) {
			_beg = -pos;
			pos = 0;
		}
		if (pos + _end > tot) {
			_end = tot - pos;
		}
		try {
			tape.seek(pos);
			tape.read(tapeBuf, _beg, _end - _beg);
			idx = newIdx;
			beg = _beg;
			end = _end;
			repaint();
		} catch (Exception ee) {}
	}

	public void keyTyped(KeyEvent e) { }
	public void keyPressed(KeyEvent e) {
		int c = e.getKeyCode();
		int _idx = idx;
		if (c == KeyEvent.VK_UP) { // move tape UP... towards 0
			--_idx;
		} else if (c == KeyEvent.VK_DOWN) { // move tape down - towards 0
			++_idx;
		} else if (c == KeyEvent.VK_PAGE_UP) {
			_idx -= buf;
		} else if (c == KeyEvent.VK_PAGE_DOWN) {
			_idx += buf;
		} else if (c == KeyEvent.VK_HOME) {
			_idx = 0;
		} else if (c == KeyEvent.VK_END) {
			_idx = tot - 1;;
		} else if (c == KeyEvent.VK_ENTER) {
			dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
		} else {
			return;
		}
		if (_idx < 0) _idx = 0;
		if (_idx >= tot) _idx = tot - 1;
		cacheTape(_idx);
	}
	public void keyReleased(KeyEvent e) { }

	public void mouseWheelMoved(MouseWheelEvent e) {
		int clicks = e.getWheelRotation();
		int _idx = idx;
		_idx += clicks;
		if (_idx >= tot) _idx = tot - 1;
		if (_idx < 0) _idx = 0;
		cacheTape(_idx);
	}

	public void windowActivated(WindowEvent e) { }
	public void windowClosed(WindowEvent e) { }
	public void windowIconified(WindowEvent e) { }
	public void windowOpened(WindowEvent e) { }
	public void windowDeiconified(WindowEvent e) { }
	public void windowDeactivated(WindowEvent e) { }
	public void windowClosing(WindowEvent e) {
		try {
			tape.seek(idx);
		} catch (Exception ee) {}
	}
}
