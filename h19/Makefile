# This is not buildable without access to this repository:
H19_SIM = $(HOME)/git/heathkit/h19/h19-sim

CLASS_PATH = .:../sim:$(H19_SIM)

JAVAS = $(wildcard *.java)
CLASS = $(subst .java,.class,$(JAVAS))

all: $(H19_SIM) $(CLASS)

%.class: %.java
	javac -cp $(CLASS_PATH) $?

# TODO: rule should include all other classes...
# but that requires this be iterative...
h19-sim.deps: $(CLASS)
	jdeps -cp $(CLASS_PATH) -verbose:class -filter:none -R $(CLASS)|\
		awk '$$NF=="h19-sim"{gsub(/\$$/,"\\$$",$$3);print $$3 ".class";}'|\
		sort -u >$@

jar: $(H19_SIM) H19plugin.jar

# stupid "jar -C" is stupid - and can't no wildcards
H19plugin.jar: $(CLASS) h19-sim.deps
	jar -cMf $(PWD)/$@ *.class
	cd $(H19_SIM); jar -uf $(PWD)/$@ $(shell cat h19-sim.deps) *.wav *.png *.ttf
