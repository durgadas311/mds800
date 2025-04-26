// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import javax.swing.JFrame;

public interface PeripheralContainer {
	JFrame getFrame();	// may be null
	String getName();	// never null if getFrame() not null
};
