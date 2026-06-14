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
	PaperTapeViewer ptv;
	long savedFP;
	int idx;
	int tot;
	boolean top;

	public PaperTapePositioner(WindowListener lstr, RandomAccessFile tape, int zone,
				Component friend) {
		super("PTR");
		setup(lstr, tape, zone, friend, false);
	}

	public PaperTapePositioner(WindowListener lstr, RandomAccessFile tape, int zone,
			Component friend, String name, boolean punch) {
		super(name);
		setup(lstr, tape, zone, friend, punch);
	}

	private void setup(WindowListener lstr, RandomAccessFile tape, int zone,
			Component friend, boolean punch) {
		this.top = punch;	// active position is TOP for punches
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // ...or live view?
		setResizable(false);
		setLocationByPlatform(true);
		if (friend != null) {
			setLocationRelativeTo(friend);
		}
		if (!punch) {
			// Only readers act on close
			addWindowListener(this);
			addWindowListener(lstr);
		}
		this.tape = tape;
		try {
			savedFP = tape.getFilePointer();
			tot = (int)tape.length();
		} catch (Exception ee) {
			// fatal error... ???
			ee.printStackTrace();
			System.exit(1);
		}
		idx = -1;	// never equals savedFP
		ptv = new PaperTapeViewer(zone, -1, punch);
		if (top) {
			ptv.update(ptv.win - 1, ptv.win);
		}
		add(ptv);
		addKeyListener(this);
		addMouseWheelListener(this);
		pack();
		setVisible(true);
		cacheTape((int)savedFP);
	}

	// newIdx already validated (0 <= newIdx < tot)
	private void cacheTape(int newIdx) {
		int pos = newIdx - ptv.buf;
		int _beg = 0;		// assume full window
		int _end = ptv.win;		//

		if (newIdx == idx) {
			return;
		}
		if (top) {
			pos -= ptv.buf;
		}
		if (pos < 0) {
			_beg = -pos;
			pos = 0;
		}
		if (pos + (_end - _beg) > tot) {
			_end = _beg + (tot - pos);
		}
		try {
			tape.seek(pos);
			tape.read(ptv.tapeBuf, _beg, _end - _beg);
			idx = newIdx;
			ptv.update(_beg, _end);
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
			_idx -= ptv.buf;
		} else if (c == KeyEvent.VK_PAGE_DOWN) {
			_idx += ptv.buf;
		} else if (c == KeyEvent.VK_HOME) {
			_idx = 0;
		} else if (c == KeyEvent.VK_END) {
			_idx = tot - 1;;
		} else if (c == KeyEvent.VK_ENTER) {
			dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
		} else if (c == KeyEvent.VK_DELETE) {
			// restore original file position
			// (who knows where we are now)
			idx = (int)savedFP;
			dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
		} else {
			return;
		}
		if (_idx < 0) _idx = 0;
		if (_idx > tot) _idx = tot;
		cacheTape(_idx);
	}
	public void keyReleased(KeyEvent e) { }

	public void mouseWheelMoved(MouseWheelEvent e) {
		int clicks = e.getWheelRotation();
		int _idx = idx;
		_idx += clicks;
		if (_idx >= tot) _idx = tot;
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
