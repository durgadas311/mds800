// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>
import java.awt.*;
import javax.swing.*;
import java.io.*;
import java.util.Properties;
import java.lang.reflect.Field;
import java.util.Properties;

public class VirtualMDS800 {
	private static JFrame front_end;

	public static void main(String[] args) {
		String rc;
		rc = System.getenv("MDS800_CONFIG");
		if (rc == null) {
			File f = new File("./mds800rc");
			if (f.exists()) {
				rc = f.getAbsolutePath();
			}
		}
		if (rc == null) {
			rc = System.getProperty("user.home") + "/.mds800rc";
		}
		for (String arg : args) {
			File f = new File(arg);
			if (f.exists()) {
				rc = f.getAbsolutePath();
			}
		}
		Properties props = new Properties();
		try {
			FileInputStream cfg = new FileInputStream(rc);
			props.load(cfg);
			cfg.close();
			System.err.format("Using config in %s\n", rc);
			props.setProperty("configuration", rc);
		} catch(Exception ee) {
			System.err.format("No config file\n");
		}

		front_end = new JFrame("Virtual MDS-800 Computer");
		front_end.getContentPane().setName("MDS-800 Emulator");
		front_end.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		front_end.getContentPane().setBackground(new Color(100, 100, 100));
		MDSFrontPanel fp = new MDSFrontPanel(front_end, props);

		MDS800 mds = new MDS800(props, fp);
		// All LEDs should be registered now...
		MDS800Operator op = new MDS800Operator(front_end, props);
		op.setCommander(mds.getCommander());
		op.addFrames(mds.getFrames());

		front_end.pack();
		front_end.setVisible(true);

		// do not start until power turned on
		// mds.start(); // spawns its own thread... returns immediately
	}
}
