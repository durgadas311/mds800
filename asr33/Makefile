
JAVAS = ASR33.java ASR33telnet.java ASR33Serial.class
CLASSES = $(subst .java,.class,$(JAVAS))
JARS = ASR33telnet.jar ASR33plugin.jar

all: $(CLASSES)

%.class: %.java
	javac $?

jar: $(JARS)

# Need to exclude ASR33serial.class...
ASR33telnet.jar: all
	echo "Main-Class: ASR33telnet" >Manifest.txt
	jar -cmf Manifest.txt $@ *.class doc

# Need to exclude ASR33telnet.class...
ASR33plugin.jar: all
	jar -cMf $@ ASR33*.class doc

ship: jar
	rsync -Wuv $(JARS) ../bin

__FRC__:
