// Copyright (c) 2016 Douglas Miller <durgadas311@gmail.com>

import java.util.Vector;

public interface DiskController extends IODevice {
	void setPower(boolean on);
	GenericRemovableDrive findDrive(String name);
	Vector<GenericRemovableDrive> getDiskDrives();
}
