# This must build both plugin and standalone app.
# would like to avoid having duplicates of classes
# in ../sim, but standalone JAR needs them copied.
# Perhaps 'jdeps' can help with this.
CLASS_PATH = .:../sim

JAVAS = ASR33.java ASR33telnet.java ASR33Serial.java
CLASSES = $(subst .java,.class,$(JAVAS))
JARS = ASR33telnet.jar ASR33plugin.jar

all: $(CLASSES)

%.class: %.java
	javac -cp $(CLASS_PATH) $?

jar: $(JARS)

# Need to exclude ASR33serial.class...
ASR33telnet.jar: all
	echo "Main-Class: ASR33telnet" >Manifest.txt
	jar -cmf Manifest.txt $@ *.class doc

# Need to exclude ASR33telnet.class...
ASR33plugin.jar: all
	jar -cMf $@ ASR33*.class doc

ship: jar
	rsync -Wuv ASR33telnet.jar ../bin

__FRC__:
