// Copyright (c) 2018 Douglas Miller <durgadas311@gmail.com>

public interface GenericRemovableDrive {
	void insertMedia(String[] media);
	String getDriveName();
	String getMediaName();
	int getMediaSize();
	boolean isRemovable();
	boolean isReady();
}
