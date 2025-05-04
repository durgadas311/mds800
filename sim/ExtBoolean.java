// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

public class ExtBoolean {
	public static boolean parseBoolean(String bool) {
		return (bool != null &&
			(bool.equalsIgnoreCase("true") ||
			bool.equalsIgnoreCase("yes") ||
			bool.equalsIgnoreCase("on") ||
			bool.equals("1")));
	}

	public static Boolean valueOf(String bool) {
		return parseBoolean(bool);
	}
}
