MDS_CP = z80cpu/z80core.jar:z80cpu/z80debug.jar
CLASS_PATH = .:$(MDS_CP):$(HOME)/Downloads/jSerialComm-2.9.3.jar
WWW_DIR = /var/www/html

JAVAS = $(wildcard *.java)
CLASSES = $(subst .java,.class,$(JAVAS))
SOURCE = mds800.tgz
JARS = VirtualMDS800.jar
SHIPS = $(SOURCE) $(JARS)

all: z80cpu/z80core.jar $(CLASSES)

%.class: %.java
	javac -cp $(CLASS_PATH) $?

jar: $(JARS)

src: $(SOURCE)

build: jar src
	rm -rf temp2 temp

ship: __FRC__
	rsync -Wuv LICENSE $(SHIPS) z80cpu/z80cpu.tgz \
		durgadas.com:$(WWW_DIR)/sebhc/mds800
	rsync -rWuv www/downloads/. durgadas.com:$(WWW_DIR)/sebhc/mds800/.

www: __FRC__
	rsync -Wuv www/*.* durgadas.com:$(WWW_DIR)/mds800/
	rsync -Wuv np-*.png durgadas.com:$(WWW_DIR)/mds800/

VirtualMDS800.jar: all temp
	echo "Main-Class: VirtualMDS800" >Manifest.txt
	jar -cmf Manifest.txt VirtualMDS800.jar *.class \
		icons/*.png *.rom docs/*.html -C temp .

temp: __FRC__
	mkdir -p temp
	rm -rf temp/*
	cd temp; jar -xf ../z80cpu/z80core.jar
	rm -rf temp/META-INF
	cd temp; jar -xf ../z80cpu/z80debug.jar
	rm -rf temp/META-INF

# ...
#	mkdir -p temp/docs
#	./mkAbout >temp/docs/About.html
#	cp www/*.html temp/docs/
#	cd temp; jar -xf ../z80cpu/z80core.jar
#	rm -rf temp/META-INF
#	cd temp; jar -xf ../z80cpu/z80debug.jar
#	rm -rf temp/META-INF

mds800.tgz: __FRC__
	cd ..; tar -czf sim/mds800.tgz sim/Makefile sim/mkAbout \
		sim/*.java sim/*.rom sim/www/*.*

__FRC__:
