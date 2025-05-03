// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class SimResource {
	public static String path; // user beware!

	public static InputStream open(Object thus, String obj) throws Exception {
		Exception ex = null;
		InputStream is;
		try {
			is = new FileInputStream(obj);
			path = "file://" + obj;
			return is;
		} catch (Exception ee) {
			ex = ee;
		}
		try {
			// This returns null if not found!
			InputStream fi = thus.getClass().getResourceAsStream(obj);
			if (fi != null) {
				path = "jar://" + obj;
				return fi;
			}
		} catch (Exception ee) {
			ex = ee;
		}
		if (ex != null) {
			throw ex;
		}
		throw new FileNotFoundException(obj);
	}
}
