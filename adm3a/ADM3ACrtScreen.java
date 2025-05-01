// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Properties;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;

public class ADM3ACrtScreen extends JPanel
		implements MouseListener, MouseMotionListener {
	String[] lines = new String[24];
	private FontMetrics _fm;
	private int _fa; //, _fd;
	private int _fw, _fh;
	float _fz;
	int curs_x;
	int curs_y;
	Dimension _dim;
	int bd_width;
	boolean drag = false;
	Point dragStart;
	Point dragStop;
	static final Color highlight = new Color(100, 100, 120);

	public Dimension getNormSize() {
		return _dim;
	}

	public ADM3ACrtScreen(Properties props) {
		String f = "fonts/ADM3A.ttf";
		_fz = 18f;
		Color fc = Color.green;
		String s = props.getProperty("adm3a_font");
		if (s != null) {
			f = s;
		}
		s = props.getProperty("adm3a_font_size");
		if (s != null) {
			_fz = Float.valueOf(s);
		}
		s = props.getProperty("adm3a_font_color");
		if (s != null) {
			fc = new Color(Integer.valueOf(s, 16));
		}
		setForeground(fc);
		Font font = null;
		try {
			File ff = new File(f);
			java.io.InputStream ttf;
			if (ff.exists()) {
				ttf = new FileInputStream(ff);
			} else {
				ttf = this.getClass().getResourceAsStream(f);
			}
			if (ttf != null) {
				font = Font.createFont(Font.TRUETYPE_FONT, ttf);
				font = font.deriveFont(_fz);
			}
		} catch (Exception ee) {
			font = null;
		}
		if (font == null) {
			System.err.println("Missing font \"" +
					f + "\", using default");
			font = new Font("Monospaced", Font.PLAIN, 10);
		}
		setFont(font);
		clearScreen();
		setBackground(new Color(50,50,50, 255));
		setOpaque(true);
		bd_width = 3;
		addMouseListener(this);
	}

	public void setFont(Font f) {
		super.setFont(f);
		_fm = getFontMetrics(f);
		_fa = _fm.getAscent();
		_fw = _fm.charWidth('M');
		_fh = _fm.getHeight();

		_fh = (int)Math.floor(_fz);
		// leave room for borders...
		// TODO: need to fudge width/height for some reason
		_dim = new Dimension(_fw * 80 + 2 * bd_width + 8,
				_fh * 24 + 2 * bd_width + 5);
		super.setPreferredSize(_dim);
	}

	private void paintHighlight(Graphics2D g2d) {
		int x0 = (int)dragStart.getX();
		int y0 = (int)dragStart.getY();
		int x1 = 80;
		int y1 = (int)dragStop.getY();
		g2d.setColor(highlight);
		for (int y = y0; y < y1; ++y) {
			if (y + 1 == y1) {
				x1 = (int)dragStop.getX();
			}
			g2d.fillRect(x0 * _fw + bd_width, y * _fh + bd_width,
					(x1 - x0) * _fw, _fh);
			x0 = 0;
		}
		g2d.setColor(getForeground());
	}

	public void paint(Graphics g) {
		super.paint(g);
		Graphics2D g2d = (Graphics2D)g;
		if (drag) {
			paintHighlight(g2d);
		}
		int y;
		for (y = 0;  y < 24; ++y) {
			if (y == curs_y) {
				int cc = lines[y].charAt(curs_x);
				cc &= 0x7f;
				cc |= 0x80;
				g2d.drawString(String.format("%-80s",
					lines[y].substring(0, curs_x) + (char)cc +
					lines[y].substring(curs_x + 1)), bd_width,
					y * _fh + _fa + bd_width);
			} else {
				g2d.drawString(lines[y], bd_width,
					y * _fh + _fa + bd_width);
			}
		}
	}

	private void _scrollUp(int y) {
		for (int x = y; x < 23; ++x) {
			lines[x] = lines[x + 1];
		}
		lines[23] = String.format("%80s", "");
	}

	private void _scrollUp(Point p) {
		if (p.y > 0) {
			--p.y;
		} else {
			p.x = 0;
		}
	}

	public void clearScreen() {
		for (int x = 0; x < 24; ++x) {
			lines[x] = String.format("%80s", "");
		}
		repaint();
	}

	public void putChar(int c, int x, int y) {
		// Ugh...
		c &= 0x7f;
		if (c < ' ') c += 0x100;
		lines[y] = String.format("%-80s",
			lines[y].substring(0, x) + (char)c +
			lines[y].substring(x + 1));
		repaint();
	}

	public void scrollUp() {
		// TODO: try to scroll selection?
		_scrollUp(0);
		if (drag) {
			_scrollUp(dragStart);
			_scrollUp(dragStop);
			copyToClipboard();
		}
		repaint();
	}

	public void setCursor(int x, int y) {
		if (curs_x != x || curs_y != y) {
			curs_x = x;
			curs_y = y;
			repaint();
		}
	}

	// This currently neutralizes reverse video and graphics,
	// eliminating those attributes completely.
	private String normalize(String line) {
		byte[] str = new byte[line.length()];
		for (int x = 0; x < line.length(); ++x) {
			int c = line.charAt(x);
			c &= 0x7f; // enough?
			str[x] = (byte)c;
		}
		return new String(str);
	}

	private String getRegion(Point p0, Point p1) {
		String s = "";
		int x0 = (int)p0.getX();
		int y0 = (int)p0.getY();
		int x1 = 80;
		int y1 = (int)p1.getY();
		for (int y = y0; y < y1; ++y) {
			if (y + 1 == y1) {
				x1 = (int)p1.getX();
			}
			if (!s.isEmpty()) {
				s += '\n';
			}
			// Until we support 360-degree drag...
			try {
				s += normalize(lines[y].substring(x0, x1).replaceAll("\\s+$",""));
			} catch (Exception ee) {}
			x0 = 0;
		}
		return s;
	}

	public String getScreen(int line) {
		String str = "";
		if (line >= lines.length) {
			return str;
		}
		int b = 0;
		int e = 23;
		if (line >= 0) {
			b = e = line;
		}
		for (int y = b; y <= e; ++y) {
			str += normalize(lines[y]);
		}
		return str;
	}

	public String dumpScreen(int line) {
		String str = "";
		if (line >= lines.length) {
			return str;
		}
		int b = 0;
		int e = 23;
		if (line >= 0) {
			b = e = line;
		}
		for (int y = b; y <= e; ++y) {
			str += normalize(lines[y].replaceAll("\\s+$","")) + '\n';
		}
		return str;
	}

	private Point charStart(Point mp) {
		Point p = new Point();
		p.x = (int)Math.floor((mp.getX() - bd_width) / _fw);
		p.y = (int)Math.floor((mp.getY() - bd_width) / _fh);
		return p;
	}

	private Point charEnd(Point mp) {
		Point p = new Point();
		p.x = (int)Math.ceil((mp.getX() - bd_width) / _fw);
		p.y = (int)Math.ceil((mp.getY() - bd_width) / _fh);
		return p;
	}

	private void copyToClipboard() {
		if (dragStart.equals(dragStop)) {
			drag = false;
			return;
		}
		StringSelection ss = new StringSelection(getRegion(dragStart, dragStop));
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
	}

	public String getSelectedText() {
		if (dragStart.equals(dragStop)) {
			return null;
		}
		return getRegion(dragStart, dragStop);
	}

	public void mouseClicked(MouseEvent e) { }
	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e) { }
	public void mousePressed(MouseEvent e) {
		if (e.getButton() != MouseEvent.BUTTON1) {
			return;
		}
		drag = true;
		dragStop = dragStart = charStart(e.getPoint());
		addMouseMotionListener(this);
	}
	public void mouseReleased(MouseEvent e) {
		if (e.getButton() != MouseEvent.BUTTON1) {
			return;
		}
		repaint();
		removeMouseMotionListener(this);
		copyToClipboard();
	}

	public void mouseDragged(MouseEvent e) {
		dragStop = charEnd(e.getPoint());
		repaint();
	}
	public void mouseMoved(MouseEvent e) { }
}
