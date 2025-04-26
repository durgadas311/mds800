// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.awt.*;
import javax.swing.*;
import java.io.*;
import java.util.Properties;
import java.util.Vector;

public class H19Serial implements PeripheralContainer, SerialDevice, Runnable {
	private JFrame front_end;
	private H19Terminal term;
	private java.util.concurrent.LinkedBlockingDeque<Integer> fifo;
	private boolean running;
	private String name;

	static int index = 1;

	public H19Serial(Properties props, Vector<String> argv, VirtualUART uart) {
		String nm;
		if (argv.size() > 1) {
			nm = argv.get(1);
		} else {
			nm = String.format("%d", index++);
		}
		name = "H19_" + nm.toUpperCase();
		fifo = new java.util.concurrent.LinkedBlockingDeque<Integer>();
		CrtScreen screen = new CrtScreen(props);
		String title = String.format("Virtual H19 Terminal - %s", nm);

		front_end = new JFrame(title);
		front_end.getContentPane().setName("H19 Emulator");
		front_end.getContentPane().setBackground(new Color(100, 100, 100));
		front_end.setLocationByPlatform(true);
		// This allows TAB to be sent
		front_end.setFocusTraversalKeysEnabled(false);
		String s = props.getProperty("h19_nameplate");
		if (s == null) {
			s = "np-h19.png";
		}
		if (s.equals("none")) {
			front_end.add(screen);
		} else {
			Object obj = s;
			if (s.matches("np-.*\\.png")) try {
				obj = new ImageIcon(H19Serial.class.getResource(s));
			} catch (Exception ee) { }
			H19FrontSide front = new H19FrontSide(front_end, screen, props, obj);
			front_end.add(front);
		}

		H19Keyboard kbd = new H19Keyboard(new H19SerialOutputStream(uart), false);
		front_end.addKeyListener(kbd);
		screen.setPasteListener(kbd);
		term = new H19Terminal(props, screen, kbd, new H19SerialInputStream(uart));
		front_end.pack();
		//front_end.setVisible(true);
		uart.attachDevice(this);
		uart.setModem(VirtualUART.SET_CTS |
				VirtualUART.SET_DSR |
				VirtualUART.SET_DCD);
		Thread t = new Thread(this);
		t.start();
	}

	public JFrame getFrame() { return front_end; }
	public String getName() { return name; }

	// SerialDevice interface
	public void write(int b) {
		fifo.add(b);
	}
	public int read() { return 0; } // deprecated
	public int available() { return 0; } // deprecated
	public void rewind() {}
	public void modemChange(VirtualUART me, int mdm) {
		// do we care?
	}
	public int dir() { return SerialDevice.DIR_OUT; }
	public String dumpDebug() {
		String ret = String.format("H19Serial running=%s\n", running);
		return ret;
	}

	private class H19SerialInputStream extends InputStream {
		public H19SerialInputStream(VirtualUART uart) {
		}
		public int read() {
			try {
				return fifo.take();
			} catch (Exception ee) {
				return -1;
			}
		}
		public int available() {
			return fifo.size();
		}
	}

	private class H19SerialOutputStream extends OutputStream {
		private VirtualUART uart;
		public H19SerialOutputStream(VirtualUART uart) {
			this.uart = uart;
		}
		public void write(int b) {
			uart.put(b, false);
		}
	}

	public void run() {
		running = true;
		term.run();
		running = false;
		// TODO: tear-down? change title?
	}
}
