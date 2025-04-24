MDS_CP = z80cpu/z80core.jar:z80cpu/z80debug.jar
CLASS_PATH = .:$(MDS_CP):$(HOME)/Downloads/jSerialComm-2.9.3.jar

JAVAS = $(wildcard *.java)
CLASSES = $(subst .java,.class,$(JAVAS))
JARS = VirtualMDS800.jar
SHIPS = $(SOURCE) $(JARS)

all: z80cpu/z80core.jar $(CLASSES)

%.class: %.java
	javac -cp $(CLASS_PATH) $?

jar: $(JARS)

build: jar src
	rm -rf temp2 temp

ship: jar
	rsync -Wuv $(JARS) ../bin

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

__FRC__:
