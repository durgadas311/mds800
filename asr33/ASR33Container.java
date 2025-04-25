// Copyright (c) 2025 Douglas Miller <durgadas311@gmail.com>

import java.io.*;

public interface ASR33Container {
	String getTitle();
	InputStream getInputStream();
	OutputStream getOutputStream();
	boolean hasConnection();
	void disconnect();
	int reconnect();
};
