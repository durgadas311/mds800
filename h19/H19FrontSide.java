// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.Properties;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;
import javax.swing.border.*;

public class H19FrontSide extends JPanel {
	RoundedRectangle _nameshape;
	BezelRoundedRectangle _crtshape;
	int gaps;
	float rounding;
	int width, height;
	int offset;

	public H19FrontSide(JFrame main, JPanel crt,
			Properties props, Object nameplate) {
		super();
		if (props == null) {}
		setBackground(main.getContentPane().getBackground());
		setOpaque(true);
		Dimension dim = crt.getPreferredSize();
		gaps = dim.width / 50;
		if (gaps < 5) gaps = 5;
		offset = dim.width / 40;
		rounding = offset * 2;
		width = Math.round(dim.width + 2 * offset);
		height = Math.round(dim.height + 2 * offset);

		GridBagLayout gridbag = new GridBagLayout();
		setLayout(gridbag);
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
		gc.gridwidth = 5;
		gridbag.setConstraints(pan, gc);
		gc.gridwidth = 1;
		add(pan);
		++gc.gridy;

		pan = new JPanel();
		pan.setPreferredSize(new Dimension(gaps, gaps));
		pan.setOpaque(false);
		gc.gridx = 0;
		gridbag.setConstraints(pan, gc);
		add(pan);

		_crtshape = new BezelRoundedRectangle(crt.getBackground(),
			main.getContentPane().getBackground(), true,
			0f, 0f, width, height,
			rounding, rounding);
		GridBagLayout gb = new GridBagLayout();
		_crtshape.setLayout(gb);
		_crtshape.setOpaque(false);
		gridbag.setConstraints(crt, gc);
		_crtshape.add(crt);
		gc.gridx = 1;
		gridbag.setConstraints(_crtshape, gc);
		add(_crtshape);

		pan = new JPanel();
		pan.setPreferredSize(new Dimension(gaps, gaps));
		pan.setOpaque(false);
		gc.gridx = 2;
		gridbag.setConstraints(pan, gc);
		add(pan);

		int nmwid = width / 3;
		int nmhgh = height;
		_nameshape = new RoundedRectangle(new Color(220,220,220, 255), false,
				0f, 0f, nmwid, height,
				rounding, rounding);
		_nameshape.setOpaque(false);
		FlowLayout fl = new FlowLayout();
		fl.setVgap((dim.height * 1) / 8);
		_nameshape.setLayout(fl);

		pan = new JPanel();
		pan.setOpaque(false);
		pan.setPreferredSize(new Dimension(nmwid - 20, (dim.height * 5) / 8));
		_nameshape.add(pan);

		JLabel lab;
		if (nameplate instanceof Icon) {
			lab = new JLabel((Icon)nameplate);
		} else if (nameplate instanceof String) {
			lab = new JLabel((String)nameplate);
			//lab.setText(nameplate);
		} else {
			lab = new JLabel();
		}
		lab.setFont(new Font("Sans-serif", Font.PLAIN, dim.height / 20));
		lab.setForeground(Color.black);
		Border lb = BorderFactory.createBevelBorder(BevelBorder.LOWERED);
		lab.setBorder(lb);
		_nameshape.add(lab);
		gc.gridx = 3;
		gridbag.setConstraints(_nameshape, gc);
		add(_nameshape);

		pan = new JPanel();
		pan.setPreferredSize(new Dimension(gaps, gaps));
		pan.setOpaque(false);
		gc.gridx = 4;
		gridbag.setConstraints(pan, gc);
		add(pan);

		++gc.gridy;
		gc.gridx = 0;

		pan = new JPanel();
		pan.setPreferredSize(new Dimension(gaps, gaps));
		pan.setOpaque(false);
		gc.gridwidth = 5;
		gridbag.setConstraints(pan, gc);
		gc.gridwidth = 1;
		add(pan);
	}
}
