// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.net.*;

public class ASR33telnet implements ASR33Container, Runnable {
	private ASR33 front_end;
	private Socket sok;
	private ServerSocket listen;
	private String title;
	private String host = null;
	private int port = -1;
	private boolean server; // listening vs. connecting
	private Semaphore sema;
	private Thread thrd;

	private Object[] newcon_btns;
	private JTextField newcon_host;
	private JTextField newcon_port;
	private JPanel newcon_pn;
	private static final int OPTION_CANCEL = 0;
	private static final int OPTION_YES = 1;

	public ASR33telnet(String[] args) {
		// First existing file in args is config file...
		// Other args are host and optional port...
		String rc = System.getenv("ASR33_CONFIG");
		if (rc == null) {
			File f = new File("./asr33rc");
			if (f.exists()) {
				rc = f.getAbsolutePath();
			}
		}
		if (rc == null) {
			rc = System.getProperty("user.home") + "/.asr33rc";
		}
		Properties props = new Properties();
		try {
			FileInputStream cfg = new FileInputStream(rc);
			props.load(cfg);
			cfg.close();
			//System.err.format("Using config in %s\n", rc);
			props.setProperty("configuration", rc);
		} catch(Exception ee) {
			//System.err.format("No config file\n");
		}
	
		String s;
		for (String arg : args) {
			File f = new File(arg);
			if (f.exists()) {
				continue;
			}
			if (arg.indexOf("=") >= 0) {
				String[] ss = arg.split("=", 2);
				props.setProperty("asr33_" + ss[0], ss[1]);
			} else if (host == null) {
				host = arg;
			} else if (port <= 0) {
				port = Integer.decode(arg);
			}
		}
		if (host == null) {
			s = props.getProperty("asr33_host");
			if (s == null) {
				System.err.format("Usage: ASR33telnet [conf] host [port]\n");
				System.exit(1);
			}
			host = s;
		}
		if (port <= 0) {
			s = props.getProperty("asr33_port");
			if (s != null) {
				port = Integer.decode(s);
			} else {
				port = 23;	// standard telnet port
			}
		}
		//System.err.format("ASR33telnet %s %d\n", host, port);
		server = (props.getProperty("asr33_server") != null);
		if (server) {
			setupListening();
		} else {
			reconnect();
		}

		front_end = new ASR33(props, this);
		front_end.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		front_end.pack();
		if (!server) {
			setupNewCon();
		}
		front_end.setVisible(true);
	}

	private void setupNewCon() {
		newcon_pn = new JPanel();
		newcon_pn.setLayout(new BoxLayout(newcon_pn, BoxLayout.Y_AXIS));
		newcon_btns = new Object[2];
		newcon_btns[OPTION_YES] = "Accept";
		newcon_btns[OPTION_CANCEL] = "Cancel";
		newcon_host = new JTextField();
		newcon_host.setPreferredSize(new Dimension(200, 20));
		newcon_port = new JTextField();
		newcon_port.setPreferredSize(new Dimension(200, 20));
		JPanel pn = new JPanel();
		pn.add(new JLabel("Host:"));
		pn.add(newcon_host);
		newcon_pn.add(pn);
		pn = new JPanel();
		pn.add(new JLabel("Port:"));
		pn.add(newcon_port);
		newcon_pn.add(pn);
	}

	////////////////////////////////////////
	// ASR33Container
	public synchronized String getTitle() { return title; }
	public InputStream getInputStream() {
		if (sok == null) return null;
		try {
			return sok.getInputStream();
		} catch (Exception ee) {
			return null;
		}
	}
	public OutputStream getOutputStream() {
		if (sok == null) return null;
		try {
			return sok.getOutputStream();
		} catch (Exception ee) {
			return null;
		}
	}

	public void addMenus(JMenuBar mb, JMenu main, ActionListener lstr) {
		JMenuItem mi;
		main.addSeparator();
		if (!server) {
			mi = new JMenuItem("Reconnect", KeyEvent.VK_R);
			mi.setActionCommand("ext"); // just to differentiate
			mi.addActionListener(lstr);
			main.add(mi);
		}
		mi = new JMenuItem("Hangup", KeyEvent.VK_H);
		mi.setActionCommand("ext"); // just to differentiate
		mi.addActionListener(lstr);
		main.add(mi);
		if (!server) {
			mi = new JMenuItem("New Connection", KeyEvent.VK_N);
			mi.setActionCommand("ext"); // just to differentiate
			mi.addActionListener(lstr);
			main.add(mi);
		}
	}

	// client understands it must envChanged() itself
	public boolean menuActions(JMenuItem me) {
		if (me.getMnemonic() == KeyEvent.VK_R) {
			return (reconnect() != 0);
		}
		if (me.getMnemonic() == KeyEvent.VK_H) {
			disconnect();
			return true;
		}
		if (me.getMnemonic() == KeyEvent.VK_N) {
			return newConnection();
		}
		return false;
	}

	// client understands it must envChanged() itself
	public void failing() {
		if (server) {
			drop(); // notifies front_end
		} else {
			disconnect();
		}
	}
	////////////////////////////////////////

	private void disconnect() {
		Socket _sok = sok;
		synchronized(this) {
			sok = null;
			title = "no connection";
		}
		if (_sok != null) {
			try {
				_sok.close();
			} catch (Exception ee) {}
		}
	}

	// must be last in all change steps - notifies front_end
	private synchronized void changeTitle(String str) {
		title = str;
		if (front_end != null) {
			front_end.envChanged();
		}
	}

	// for server mode - drop the client and restart listening.
	// can only happen after front_end exists.
	private void drop() {
		disconnect();	// sets "no connection" just in case, no notification
		front_end.envChanged();
		sema.release();	// wake up listener thread
	}

	private int reconnect() {
		if (sok != null && sok.isConnected() && sok.isBound() && !sok.isClosed() &&
				!sok.isInputShutdown() && !sok.isOutputShutdown()) {
			//System.err.format("socket still up\n");
			return 0; // nothing done
		}
		disconnect(); // no front_end notification
		try {
			InetAddress ia = InetAddress.getByName(host);
			sok = new Socket(ia, port);
			sok.setKeepAlive(true);
			// This is either early or front_end already knows
			title = String.format("telnet  %s %d", host, port);
			return 1; // connection good
		} catch (Exception ee) {
			// System.err.println(ee.getMessage());
		}
		return -1; // no connection
	}

	// only called on client, from menuActions()
	private boolean newConnection() {
		// TODO:
		newcon_host.setText(host);
		newcon_port.setText(String.format("%d", port));
		int res = JOptionPane.showOptionDialog(front_end, newcon_pn,
				"New Connection", JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE, null,
				newcon_btns, newcon_btns[OPTION_YES]);
		front_end.setFocus();
		if (res != OPTION_YES) {
			return false;
		}
		try {
			port = Integer.decode(newcon_port.getText());
		} catch (Exception ee) {
			// TODO: pop-up error
			return false;
		}
		host = newcon_host.getText();
		disconnect();
		reconnect();
		// front_end.envChanged();
		return true; // something changed, but may not be successful
	}

	// only called on server
	private void setupListening() {
		sema = new Semaphore(0);
		try {
			sok = null;
			InetAddress ia = InetAddress.getByName(host);
			listen = new ServerSocket(port, 1, ia);
			// this is before front_end exists...
			title = "ready";
			thrd = new Thread(this);
			thrd.start();
		} catch (Exception ee) {
			System.err.println(ee.getMessage());
			System.exit(1);
		}
	}

	// only called on server
	private void tryConn(Socket nc) {
		sok = nc;
		try {
			InetAddress ia = sok.getInetAddress();
			changeTitle(String.format("remote %s %d",
					ia.getCanonicalHostName(),
					sok.getLocalPort()));
		} catch (Exception ee) {
			sok = null;
		}
	}

	// only used on server
	public void run() {
		while (listen != null) {
			while (sok != null) {
				try { sema.acquire(); } catch (Exception ee) {}
			}
			try {
				changeTitle("listening...");
				Socket rem = listen.accept();
				tryConn(rem);
			} catch (Exception ee) {
				// how fatal is this
				break;
			}
		}
		changeTitle("dead");
		thrd = null;
	}

	public static void main(String[] args) {
		new ASR33telnet(args);
	}
}
