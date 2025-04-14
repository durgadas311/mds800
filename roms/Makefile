# The monitor ROM uses inverted address lines, and thus the dump in bitsavers
# is byte-reversed from the logical image (as seen by the CPU).
# The HEX produced by the disassmbly produces the normal logical order.
# Reconstructed mds800_roms.hex from 1702_MDS.hex and a normalized
# version of intel_monitor.bin/hex.
#
# objcopy -I binary -O ihex --change-addresses 0xf800 mds800.bin out-file

all: mds800.hex

%.hex: %.asm
	zmac --dri -i -8 -c -s -n --od . --oo lst,hex $<
