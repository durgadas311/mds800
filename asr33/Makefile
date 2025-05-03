# This must build both plugin and standalone app.
# would like to avoid having duplicates of classes
# in ../sim, but standalone JAR needs them copied.
# Perhaps 'jdeps' can help with this.
CLASS_PATH = .:../sim

JAVAS = ASR33.java ASR33telnet.java ASR33Serial.java
CLASSES = $(subst .java,.class,$(JAVAS))
JARS = ASR33telnet.jar ASR33plugin.jar
SIM_CLASS = GenericHelp.class \
	PaperTapePositioner.class \
	SerialDevice.class \
	SuffFileChooser.class \
	SuffFileFilter.class \
	VirtualUART.class

AUXS = $(wildcard asr33_doc/*) $(wildcard *.wav)

all: $(CLASSES)

%.class: %.java
	javac -cp $(CLASS_PATH) $?

%.java:: ../sim/%.java
	cp $? $@

jar: $(JARS)

# Need to exclude ASR33serial.class...
ASR33telnet.jar: $(CLASSES) $(SIM_CLASS) $(AUXS)
	echo "Main-Class: ASR33telnet" >Manifest.txt
	jar -cmf Manifest.txt $@ *.class $(AUXS)

# Need to exclude ASR33telnet.class...
ASR33plugin.jar: $(CLASSES) $(AUXS)
	jar -cMf $@ ASR33*.class $(AUXS)

ship: jar
	rsync -Wuv ASR33telnet.jar ../bin

__FRC__:
