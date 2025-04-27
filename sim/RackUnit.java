// Copyright (c) 2018 Douglas Miller <durgadas311@gmail.com>

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

public class RackUnit extends JPanel {
	static final int WIDTH = 1024;
	static final int HEIGHT = 45;	// 1U height...
	static final Color BG = new Color(75, 75, 75);
	static final Color BLUE = new Color(60, 160, 255);
	static final Color FRAME  = new Color(238, 238, 238);
	static final Color HILITE = new Color(46, 124, 200);
	static final Color SHADOW = new Color(34, 93, 150);
	private int width;
	private int height;

	public RackUnit(int u) {
		super();
		width = WIDTH;
		height = u * HEIGHT;
		setOpaque(true);
		setBackground(BG);
		setPreferredSize(new Dimension(width, height));
		setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
				HILITE, HILITE, SHADOW, SHADOW));
	}

	public int getWidth() { return width; }
	public int getHeight() { return height; }
}
