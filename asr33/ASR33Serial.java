// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.awt.*;
import javax.swing.*;
import java.io.*;
import java.util.Properties;
import java.util.Vector;

public class ASR33Serial implements ASR33Container, SerialDevice {
	private JFrame front_end;
	private java.util.concurrent.LinkedBlockingDeque<Integer> fifo;
	private boolean running;
	private String title;
	private InputStream fin;
	private OutputStream fout;

	public ASR33Serial(Properties props, Vector<String> argv, VirtualUART uart) {
		String name = "?";
		if (argv.size() > 1) {
			name = argv.get(1);
		}
		title = name;
		fifo = new java.util.concurrent.LinkedBlockingDeque<Integer>();
		fin = new ASR33SerialInputStream(uart);
		fout = new ASR33SerialOutputStream(uart);

		front_end = new ASR33(props, this);
		// hide, not close?
		front_end.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		// TODO: need hide/raise mechanics...

		front_end.pack();
		front_end.setVisible(true);

		uart.attachDevice(this);
		uart.setModem(VirtualUART.SET_CTS |
				VirtualUART.SET_DSR |
				VirtualUART.SET_DCD);
	}

	public String getTitle() { return title; }
	public InputStream getInputStream() { return fin; }
	public OutputStream getOutputStream() { return fout; }
	public boolean hasConnection() { return false; }
	public void disconnect() { title = "dead"; }
	public int reconnect() { return -1; }

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
