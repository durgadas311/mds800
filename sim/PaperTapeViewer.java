// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.awt.*;
import javax.swing.*;

class PaperTapeViewer extends JPanel {
	static final Color buff = new Color(250,240,200);
	int cell = 15; // spacing of each dot (H/V), 0.1"
	int data = 11;	// @0.072/0.1 = 10.8/15
	int sprk = 7;	// @0.046/0.1 = 6.9/15
	public byte[] tapeBuf;
	public int win;
	public int buf;
	public int beg;
	public int end;
	int tapew;
	int tapeh;
	int marg;
	int curs;
	int l, t, b, bb, m;	// for paint()

	private void setup(int zone, int size, boolean top) {
		cell = (int)Math.round((float)size / 10f);
		data = (int)Math.round(0.72f * (float)cell);
		sprk = (int)Math.round(0.46f * (float)cell);
		buf = zone; // chars before/after cursor
		win = buf * 2 + 1;
		tapeBuf = new byte[win];
		tapew =  10 * cell;
		tapeh = win * cell;
		if (top) {
			curs = 0;
		} else {
			curs = buf * cell;
		}
		marg = 20;	// total horiz margins (10+10)
		setPreferredSize(new Dimension(tapew + marg, tapeh));
		setBackground(Color.gray);
	}

	public PaperTapeViewer(int zone) {
		super();
		// "standard" size, tape width 150 pixels
		setup(zone, 150, false);
	}

	// 'size' is desired width of tape in pixels.
	// 'top' true to place cursor at top, for punch.
	public PaperTapeViewer(int zone, int size, boolean top) {
		super();
		setup(zone, size, top);
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
		m = marg / 2 + cell - data / 2;
		g2d.setColor(buff);	// paper tape
		// only draw amount of tape present
		g2d.fillRect(marg / 2, t, tapew, (end - beg) * cell);
		g2d.setColor(Color.red); // cursor - fixed position
		g2d.drawRect(0, curs, tapew + marg - 1, cell);
		g2d.setColor(Color.black);
		// draw tapeBuf backwards... End is at top...
		l = end - 1;
		for (int y = t + 2; y < b && l >= beg; y += cell) {
			bb = 0;
			for (int x = 0; x < 9; ++x) {
				int xx = m + (x * cell);
				if (x == 3) {
					g2d.fillOval(xx + 2, y + 2, sprk, sprk);
					continue;
				}
				if ((tapeBuf[l] & (1 << bb)) != 0) {
					g2d.fillOval(xx, y, data, data);
				}
				++bb;
			}
			--l;
		}
	}
}
