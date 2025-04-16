// Copyright (c) 2016 Douglas Miller <durgadas311@gmail.com>

public interface Interruptor {
	boolean bootOn();
	int registerINT(int irq);
	void raiseINT(int irq, int src);
	void lowerINT(int irq, int src);
	void blockInts(int mask);
	void unblockInts(int mask);
	void triggerRESET();	// system RESET when safe to do
	void triggerNMI();
	void timeout2ms();
	void addClockListener(ClockListener lstn);
	void addTimeListener(TimeListener lstn);
	void addIntrController(InterruptController ctrl);
	void waitCPU();
	boolean isTracing();
	void startTracing(int cy);
	void stopTracing();
}
