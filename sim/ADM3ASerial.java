// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.util.Properties;
import java.util.List;
import java.util.Arrays;
import java.util.Vector;

public class ADM3ASerial implements TermContainer, PeripheralContainer, SerialDevice {
	private JFrame front_end;
	private java.util.concurrent.LinkedBlockingDeque<Integer> fifo;
	private boolean running;
	private String title;
	private String name;
	private InputStream fin;
	private OutputStream fout;

	static int index = 1;

	List<String> boolArgs = Arrays.asList();
	String[] seqArgs = new String[]{ "name" };

	public ADM3ASerial(Properties props, Vector<String> argv, VirtualUART uart) {
		// argv.get(0) is this class name... omit it...
		String[] args = argv.subList(1, argv.size()).toArray(new String[0]);
		ADM3A.processArgs(props, args, boolArgs, seqArgs);
		String nm = props.getProperty("adm3a_name");
		if (nm == null) {
			nm = String.format("%d", index++);
		}
		title = nm;
		name = "ADM3A_" + nm.toUpperCase();
		fifo = new java.util.concurrent.LinkedBlockingDeque<Integer>();
		fin = new ADM3ASerialInputStream(uart);
		fout = new ADM3ASerialOutputStream(uart);

		front_end = new ADM3A(props, this);

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
		String ret = String.format("ADM3ASerial %s running=%s fifo=%d\n",
				title, running, fifo.size());
		return ret;
	}

	private class ADM3ASerialInputStream extends InputStream {
		public ADM3ASerialInputStream(VirtualUART uart) {
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

	private class ADM3ASerialOutputStream extends OutputStream {
		private VirtualUART uart;
		public ADM3ASerialOutputStream(VirtualUART uart) {
			this.uart = uart;
		}
		public void write(int b) {
			uart.put(b, false);
		}
	}
}
