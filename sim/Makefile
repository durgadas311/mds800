MDS_CP = z80cpu/z80core.jar:z80cpu/z80debug.jar
AUX_CP = asr33/ASR33plugin.jar:adm3a/ADM3Aplugin.jar
CLASS_PATH = .:$(MDS_CP):$(AUX_CP)

JAVAS = $(wildcard *.java)
CLASSES = $(subst .java,.class,$(JAVAS))
JARS = VirtualMDS800.jar
SHIPS = $(SOURCE) $(JARS)
DOCS = $(wildcard doc/*.html)

all: z80cpu/z80core.jar $(CLASSES)

%.class: %.java
	javac -cp $(CLASS_PATH) $?

jar: $(JARS)

build: jar src
	rm -rf temp

ship: jar
	rsync -Wuv $(JARS) ../bin

VirtualMDS800.jar: all $(DOCS) temp
	echo "Main-Class: VirtualMDS800" >Manifest.txt
	jar -cmf Manifest.txt VirtualMDS800.jar *.class \
		icons/*.png *.rom docs/*.html -C temp .

ASR33_RM = temp/PaperTape*.class
ADM3A_RM =

temp: __FRC__
	mkdir -p temp
	rm -rf temp/*
	cd temp; jar -xf ../z80cpu/z80core.jar
	rm -rf temp/META-INF
	cd temp; jar -xf ../z80cpu/z80debug.jar
	rm -rf temp/META-INF
	cd temp; jar -xf ../asr33/ASR33plugin.jar
	rm -rf temp/META-INF $(ASR33_RM)
	cd temp; jar -xf ../adm3a/ADM3Aplugin.jar
	rm -rf temp/META-INF $(ADM3A_RM)

__FRC__:
