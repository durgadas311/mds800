// Copyright (c) 2020 Douglas Miller <durgadas311@gmail.com>

public interface Rom {
	int length();
	int read(int address);
	void write(int address, int value);	// some are writable...
	void writeEnable(boolean we);		//
	String dumpDebug();
}
