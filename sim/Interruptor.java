// Copyright (c) 2016 Douglas Miller <durgadas311@gmail.com>

public interface Interruptor {
	static final int TTY_OUT_INT = 0x01;
	static final int TTY_INP_INT = 0x02;
	static final int PTP_OUT_INT = 0x04;
	static final int PTR_INP_INT = 0x08;
	static final int CRT_OUT_INT = 0x10;
	static final int CRT_INP_INT = 0x20;
	static final int LPT_OUT_INT = 0x40;
	static final int IO_INT_ENA = 0x80;

	int registerINT(int irq);
	void raiseINT(int irq, int src);
	void lowerINT(int irq, int src);
	void triggerIOInt(int dev);
}
