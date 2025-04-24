
JAVAS = ASR33.java ASR33telnet.java
CLASSES = $(subst .java,.class,$(JAVAS))
JARS = ASR33telnet.jar

all: $(CLASSES)

%.class: %.java
	javac $?

jar: $(JARS)

ASR33telnet.jar: all
	echo "Main-Class: ASR33telnet" >Manifest.txt
	jar -cmf Manifest.txt ASR33telnet.jar *.class doc

ship: jar
	rsync -Wuv $(JARS) ../bin

__FRC__:
