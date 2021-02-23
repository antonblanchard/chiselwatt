# Hello world
MEMORY_SIZE = 16384
RAM_INIT_FILE = samples/binaries/hello_world/hello_world.hex

# Micropython
#MEMORY_SIZE = 393216
#RAM_INIT_FILE = samples/binaries/micropython/firmware.hex

BITS = 64
RESET_ADDR = 0x0
CLOCK_FREQ = 50000000

# Use Docker images for synthesis and verilator
DOCKER=docker
#DOCKER=podman

PWD = $(shell pwd)
USBDEVICE ?= /dev/bus/usb
DOCKERARGS = run --rm -v $(PWD):/src -w /src
VERILATORARGS = run --name verilator --hostname verilator --rm -it --entrypoint= -v $(PWD):/work -w /work

YOSYS          = $(DOCKER) $(DOCKERARGS) ghdl/synth:beta yosys
NEXTPNR        = $(DOCKER) $(DOCKERARGS) ghdl/synth:nextpnr-ecp5 nextpnr-ecp5
ECPPACK        = $(DOCKER) $(DOCKERARGS) ghdl/synth:trellis ecppack
OPENOCD_DEF    = $(DOCKER) $(DOCKERARGS) --privileged --device $(USBDEVICE):/dev/bus/usb ghdl/synth:prog openocd
OPENOCD_ULX3S  = $(DOCKER) $(DOCKERARGS) --privileged --device $(USBDEVICE):/dev/bus/usb alpin3/ulx3s openocd
VERILATOR      = $(DOCKER) $(VERILATORARGS) verilator/verilator

# Uncomment to use local tools for synthesis
#YOSYS     = yosys
#NEXTPNR   = nextpnr-ecp5
#ECPPACK   = ecppack
#OPENOCD   = openocd
#VERILATOR = verilator

scala_files = $(wildcard src/main/scala/*scala)

verilog_files = Core.v MemoryBlackBox.v

verilator_binary = chiselwatt

tests = $(sort $(patsubst tests/%.out,%,$(wildcard tests/*.out)))

# Define board parameters
ifeq ($(ECP5_BOARD),evn)
# ECP5-EVN
LPF=constraints/ecp5-evn.lpf
PLL=pll/pll_ehxplll.v
PACKAGE=CABGA381
NEXTPNR_FLAGS=--um5g-85k --freq 12
OPENOCD=$(OPENOCD_DEF)
OPENOCD_JTAG_CONFIG=openocd/ecp5-evn.cfg
OPENOCD_DEVICE_CONFIG=openocd/LFE5UM5G-85F.cfg
else ifeq ($(ECP5_BOARD),ulx3s)
# Radiona ULX3S with ECP5-85F
LPF=constraints/ecp5-ulx3s.lpf
PLL=pll/pll_ehxplll_25MHz.v
PACKAGE=CABGA381
NEXTPNR_FLAGS=--85k --freq 25
OPENOCD=$(OPENOCD_ULX3S)
OPENOCD_JTAG_CONFIG=openocd/ft231x.cfg
OPENOCD_DEVICE_CONFIG=openocd/LFE5U-85F.cfg
else ifeq ($(ECP5_BOARD),orangecrab)
# OrangeCrab with ECP85
LPF=constraints/orange-crab.lpf
PLL=pll/pll_bypass.v
PACKAGE=CSFBGA285
NEXTPNR_FLAGS=--um5g-85k --freq 50
OPENOCD=$(OPENOCD_DEF)
OPENOCD_JTAG_CONFIG=openocd/olimex-arm-usb-tiny-h.cfg
OPENOCD_DEVICE_CONFIG=openocd/LFE5UM5G-85F.cfg
else ifeq ($(ECP5_BOARD),colorlight)
# Colorlight 5A-75B
LPF=constraints/colorlight_5A-75B.lpf
PLL=pll/pll_ehxplll_25MHz.v
PACKAGE=CABGA256
NEXTPNR_FLAGS=--25k --freq 25
OPENOCD=$(OPENOCD_DEF)
OPENOCD_JTAG_CONFIG=openocd/olimex-arm-usb-tiny-h.cfg
OPENOCD_DEVICE_CONFIG=openocd/LFE5U-25F.cfg
else
endif

# Targets

all: chiselwatt

$(verilog_files): $(scala_files)
	scripts/mill chiselwatt.run $(BITS) $(MEMORY_SIZE) $(RAM_INIT_FILE) $(RESET_ADDR) $(CLOCK_FREQ)

$(verilator_binary): $(verilog_files) chiselwatt.cpp uart.c
# Warnings disabled until we fix the Chisel issues
#$(VERILATOR) verilator -O3 -Wall --assert --cc Core.v --exe chiselwatt.cpp uart.c #--trace
	$(VERILATOR) verilator -O3 --assert --cc Core.v --exe chiselwatt.cpp uart.c -o $@ #--trace
	$(VERILATOR) make -C obj_dir -f VCore.mk
	@cp -f obj_dir/chiselwatt chiselwatt

scala_tests: $(verilator_binary)
	scripts/mill chiselwatt.test

check: scala_tests $(tests)

$(tests): $(verilator_binary)
	@./scripts/run_test.sh $@

dockerlator: chiselwatt
	@echo "To execute chiselwatt Verilator binary, run ./chiselwatt at the prompt."
# Mask exit code from verilator on Make
	@$(VERILATOR) bash || true

synth: check-board-vars chiselwatt.bit

check-board-vars:
	@test -n "$(LPF)" || (echo "If synthesizing or programming, use \"synth\" or \"prog\" targets with ECP5_BOARD variable to either \"evn\", \"ulx3s\", \"orangecrab\", \"colorlight\"\n" ; exit 1)

chiselwatt.json: insns.hex $(verilog_files) $(PLL) toplevel.v
	$(YOSYS) -p "read_verilog -sv $(verilog_files) $(PLL) toplevel.v; synth_ecp5 -json $@ -top toplevel"

chiselwatt_out.config: chiselwatt.json $(LPF)
	$(NEXTPNR) --json $< --lpf $(LPF) --textcfg $@ $(NEXTPNR_FLAGS) --package $(PACKAGE)

chiselwatt.bit: chiselwatt_out.config
	$(ECPPACK) --svf chiselwatt.svf $< $@

chiselwatt.svf: chiselwatt.bit

prog: check-board-vars chiselwatt.svf
	$(OPENOCD) -f $(OPENOCD_JTAG_CONFIG) -f $(OPENOCD_DEVICE_CONFIG) -c "transport select jtag; init; svf chiselwatt.svf; exit"

apps_dir = ./samples

hello_world:
	docker run -it --rm -w /build -v $(PWD):/build carlosedp/crossbuild-ppc64le make -C $(apps_dir)/hello_world
	@scripts/bin2hex.py $(apps_dir)/hello_world/hello_world.bin > ./insns.hex

micropython:
	@if [ ! -d "$(apps_dir)/micropyton/ports/powerpc" ] ; then \
		rm -rf $(apps_dir)/micropyton; \
		echo "Cloning micropython repo into $(apps_dir)/micropyton"; \
		git clone https://github.com/micropython/micropython.git $(apps_dir)/micropyton; \
	else \
		echo "Micropython repo exists, updating..."; \
		cd "$(apps_dir)/micropyton"; \
		git pull; \
	fi
	@docker run -it --rm -v $(PWD):/build carlosedp/crossbuild-ppc64le make -C $(apps_dir)/micropyton/ports/powerpc
	@scripts/bin2hex.py $(apps_dir)/micropyton/ports/powerpc/build/firmware.bin > ./insns.hex

clean:
	@rm -f Core.fir firrtl_black_box_resource_files.f Core.v Core.anno.json MemoryBlackBox.v
	@rm -rf obj_dir test_run_dir target project
	@rm -f chiselwatt
	@rm -f *.bit *.json *.svf *.config
	@rm -f LoadStoreInsns.hex MemoryBlackBoxInsns.hex
	@make -C $(apps_dir)/hello_world clean

.PHONY: clean prog hello_world micropython
.PRECIOUS: chiselwatt.json chiselwatt_out.config chiselwatt.bit

