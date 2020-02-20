all: chiselwatt

scala_files = $(wildcard src/main/scala/*scala)

verilog_files = Core.v MemoryBlackBox.v

verilator_binary = chiselwatt

$(verilog_files): $(scala_files)
	sbt 'runMain CoreObj'

$(verilator_binary): $(verilog_files) chiselwatt.cpp uart.c
	# Warnings disabled until we fix the Chisel issues
	#verilator -O3 -Wall --assert --cc Core.v --exe chiselwatt.cpp uart.c #--trace
	verilator -O3 --assert --cc Core.v --exe chiselwatt.cpp uart.c -o $@ #--trace
	make -C obj_dir -f VCore.mk
	@cp -f obj_dir/chiselwatt chiselwatt

clean:
	@rm -f Core.fir firrtl_black_box_resource_files.f Core.v Core.anno.json MemoryBlackBox.v
	@rm -rf obj_dir test_run_dir target project
	@rm -f chiselwatt
	@rm -f *.bit *.json *.svf *.config
	@rm -f LoadStoreInsns.hex MemoryBlackBoxInsns.hex

scala_tests: $(verilator_binary)
	sbt testOnly

tests = $(sort $(patsubst tests/%.out,%,$(wildcard tests/*.out)))

check: scala_tests $(tests)

$(tests): $(verilator_binary)
	@./scripts/run_test.sh $@

# Use local tools for synthesis
#YOSYS     = yosys
#NEXTPNR   = nextpnr-ecp5
#ECPPACK   = ecppack
#OPENOCD    = openocd

# Use Docker images for synthesis
DOCKER=docker
#DOCKER=podman
#
PWD = $(shell pwd)
DOCKERARGS = run --rm -v $(PWD):/src -w /src
#
YOSYS     = $(DOCKER) $(DOCKERARGS) ghdl/synth:beta yosys
NEXTPNR   = $(DOCKER) $(DOCKERARGS) ghdl/synth:nextpnr-ecp5 nextpnr-ecp5
ECPPACK   = $(DOCKER) $(DOCKERARGS) ghdl/synth:trellis ecppack
OPENOCD   = $(DOCKER) $(DOCKERARGS) --device /dev/bus/usb ghdl/synth:prog openocd


# OrangeCrab with ECP85
#LPF=constraints/orange-crab.lpf
#PLL=pll/pll_bypass.v
#PACKAGE=CSFBGA285
#NEXTPNR_FLAGS=--um5g-85k --freq 50
#OPENOCD_JTAG_CONFIG=openocd/olimex-arm-usb-tiny-h.cfg
#OPENOCD_DEVICE_CONFIG=openocd/LFE5UM5G-85F.cfg

# ECP5-EVN
LPF=constraints/ecp5-evn.lpf
PLL=pll/pll_ehxplll.v
PACKAGE=CABGA381
NEXTPNR_FLAGS=--um5g-85k --freq 12
OPENOCD_JTAG_CONFIG=openocd/ecp5-evn.cfg
OPENOCD_DEVICE_CONFIG=openocd/LFE5UM5G-85F.cfg

# Colorlight 5A-75B
#LPF=constraints/colorlight_5A-75B.lpf
#PLL=pll/pll_ehxplll_25MHz.v
#PACKAGE=CABGA256
#NEXTPNR_FLAGS=--25k --freq 25
#OPENOCD_JTAG_CONFIG=openocd/olimex-arm-usb-tiny-h.cfg
#OPENOCD_DEVICE_CONFIG=openocd/LFE5U-25F.cfg

synth: chiselwatt.bit

chiselwatt.json: insns.hex $(verilog_files) $(PLL) toplevel.v
	$(YOSYS) -p "read_verilog -sv $(verilog_files) $(PLL) toplevel.v; synth_ecp5 -json $@ -top toplevel"

chiselwatt_out.config: chiselwatt.json $(LPF)
	$(NEXTPNR) --json $< --lpf $(LPF) --textcfg $@ $(NEXTPNR_FLAGS) --package $(PACKAGE)

chiselwatt.bit: chiselwatt_out.config
	$(ECPPACK) --svf chiselwatt.svf $< $@

chiselwatt.svf: chiselwatt.bit

prog: chiselwatt.svf
	$(OPENOCD) -f $(OPENOCD_JTAG_CONFIG) -f $(OPENOCD_DEVICE_CONFIG) -c "transport select jtag; init; svf $<; exit"

.PHONY: clean prog
.PRECIOUS: chiselwatt.json chiselwatt_out.config chiselwatt.bit
