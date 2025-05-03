# This must build both plugin and standalone app.
# would like to avoid having duplicates of classes
# in ../sim, but standalone JAR needs them copied.
# Perhaps 'jdeps' can help with this.
CLASS_PATH = .:../sim

JAVAS = ADM3A.java ADM3ACrtScreen.java ADM3Atelnet.java ADM3ASerial.java \
	BezelRoundedRectangle.java
CLASSES = $(subst .java,.class,$(JAVAS))
JARS = ADM3Atelnet.jar ADM3Aplugin.jar
SIM_CLASS = VirtualUART.class GenericHelp.class SimResource.class

AUXS = $(wildcard adm3a_doc/*) $(wildcard fonts/*.ttf) $(wildcard *.wav)

all: $(CLASSES)

%.class: %.java
	javac -cp $(CLASS_PATH) $?

%.java:: ../sim/%.java
	cp $? $@

jar: $(JARS)

# Need to exclude ADM3ASerial.class...
ADM3Atelnet.jar: $(CLASSES) $(SIM_CLASS) $(AUXS)
	echo "Main-Class: ADM3Atelnet" >Manifest.txt
	jar -cmf Manifest.txt $@ *.class $(AUXS)

# Need to exclude ADM3Atelnet.class...
ADM3Aplugin.jar: $(CLASSES) $(AUXS)
	jar -cMf $@ ADM3A*.class BezelRoundedRectangle.class $(AUXS)

ship: jar
	rsync -Wuv ADM3Atelnet.jar ../bin

__FRC__:
