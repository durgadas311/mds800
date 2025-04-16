// Copyright (c) 2016 Douglas Miller <durgadas311@gmail.com>

import java.util.Vector;

public interface DiskController extends IODevice {
	GenericRemovableDrive findDrive(String name);
	Vector<GenericRemovableDrive> getDiskDrives();
}
