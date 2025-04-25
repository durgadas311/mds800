CLASS_PATH = .:../sim

JAVAS = BezelRoundedRectangle.java \
	H19FrontSide.java \
	H19Serial.java \
	RoundedRectangle.java

CLASS = $(subst .java,.class,$(JAVAS))

all: $(CLASS)

%.class: %.java
	javac -cp $(CLASS_PATH) $?

jar: H19plugin.jar

H19plugin.jar: all
	jar -cMf $@ *.class *.wav *.png *.ttf
