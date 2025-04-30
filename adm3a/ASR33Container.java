// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.io.*;
import java.awt.event.*;
import javax.swing.*;

public interface ASR33Container {
	String getTitle();
	InputStream getInputStream();
	OutputStream getOutputStream();
	void addMenus(JMenuBar mb, JMenu main, ActionListener lstr);
	boolean menuActions(JMenuItem me); // true if state has changed
	void failing(); // notification of terminal condition(s)
};
