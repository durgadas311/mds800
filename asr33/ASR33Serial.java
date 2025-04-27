// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.util.Properties;
import java.util.Vector;

public class ASR33Serial implements ASR33Container, PeripheralContainer, SerialDevice {
	private JFrame front_end;
	private java.util.concurrent.LinkedBlockingDeque<Integer> fifo;
	private boolean running;
	private String title;
	private String name;
	private InputStream fin;
	private OutputStream fout;

	static int index = 1;

	public ASR33Serial(Properties props, Vector<String> argv, VirtualUART uart) {
		String nm;
		if (argv.size() > 1) {
			nm = argv.get(1);
		} else {
			nm = String.format("%d", index++);
		}
		title = nm;
		name = "ASR33_" + nm.toUpperCase();
		fifo = new java.util.concurrent.LinkedBlockingDeque<Integer>();
		fin = new ASR33SerialInputStream(uart);
		fout = new ASR33SerialOutputStream(uart);

		front_end = new ASR33(props, this);

		front_end.pack();
		//front_end.setVisible(true);

		uart.attachDevice(this);
		uart.setModem(VirtualUART.SET_CTS |
				VirtualUART.SET_DSR |
				VirtualUART.SET_DCD);
	}

	public JFrame getFrame() { return front_end; }
	public String getName() { return name; }

	public String getTitle() { return title; }
	public InputStream getInputStream() { return fin; }
	public OutputStream getOutputStream() { return fout; }
	public void addMenus(JMenuBar mb, JMenu main, ActionListener lstr) {}
	public boolean menuActions(JMenuItem me) { return false; }
	public void failing() { title = "dead"; }

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
		String ret = String.format("ASR33Serial %s running=%s fifo=%d\n",
				title, running, fifo.size());
		return ret;
	}

	private class ASR33SerialInputStream extends InputStream {
		public ASR33SerialInputStream(VirtualUART uart) {
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

	private class ASR33SerialOutputStream extends OutputStream {
		private VirtualUART uart;
		public ASR33SerialOutputStream(VirtualUART uart) {
			this.uart = uart;
		}
		public void write(int b) {
			uart.put(b, false);
		}
	}
}
