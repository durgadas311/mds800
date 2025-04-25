// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.awt.*;
import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.Properties;

public class ASR33telnet implements ASR33Container {
	private static JFrame front_end;
	private static Socket sok;
	public OutputStream fout = null;
	public InputStream fin = null;
	private String title;
	private String host = null;
	private int port = -1;

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
		reconnect();

		front_end = new ASR33(props, this);

		front_end.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		front_end.pack();
		front_end.setVisible(true);
	}

	public String getTitle() { return title; }
	public InputStream getInputStream() { return fin; }
	public OutputStream getOutputStream() { return fout; }
	public boolean hasConnection() { return true; }

	public void disconnect() {
		if (sok != null) {
			try {
				fin.close();
			} catch (Exception ee) {}
			try {
				fout.close();
			} catch (Exception ee) {}
			try {
				sok.close();
			} catch (Exception ee) {}
		}
		sok = null;
		fin = null;
		fout = null;
		title = "no connection";
	}

	public int reconnect() {
		if (sok != null && sok.isConnected() && sok.isBound() && !sok.isClosed() &&
				!sok.isInputShutdown() && !sok.isOutputShutdown()) {
			//System.err.format("socket still up\n");
			return 0; // nothing done
		}
		disconnect();
		try {
			InetAddress ia = InetAddress.getByName(host);
			sok = new Socket(ia, port);
			sok.setKeepAlive(true);
			fin = sok.getInputStream();
			fout = sok.getOutputStream();
			title = String.format("telnet  %s %d", host, port);
			return 1; // connection good
		} catch (Exception ee) {
			System.err.format("%s\n", ee.getMessage());
		}
		return -1; // no connection
	}

	public static void main(String[] args) {
		new ASR33telnet(args);
	}
}
