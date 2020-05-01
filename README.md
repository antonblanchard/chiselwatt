# Chiselwatt

A tiny POWER Open ISA soft processor written in Chisel.

## Simulation using verilator

* Chiselwatt uses `verilator` for simulation. It is built by default and run in
a Docker container. To build with local verilator install, edit `Makefile`.

* First build chiselwatt:

```sh
git clone https://github.com/antonblanchard/chiselwatt
cd chiselwatt
make
```

* The micropython and hello_world sample images are included in the repo. To use
it, link the memory image into chiselwatt:

```sh
ln -s samples/binaries/micropython/firmware.hex insns.hex
# or to use the hello_world sample, run
ln -s samples/binaries/hello_world/hello_world.hex insns.hex
```

* Now run chiselwatt:

```sh
./chiselwatt
```

* If your operating system is not Linux, run chiselwatt inside a container:

```sh
make dockerlator
# Inside the container prompt, run:
./chiselwatt

# type "exit" to exit the container
exit
```

## Synthesizing for FPGAs using Open Source tools (yosys/nextpnr)

Synthesis on FPGAs is supported with yosys/nextpnr. At the moment the tools support
Lattice ECP5 FPGAs. The build process uses Docker images, so no software other
than Docker needs to be installed. If you prefer podman you can use that too,
just adjust it in `Makefile`, `DOCKER=podman`.

### hello_world

The `hello_world` example should run everywhere, so start with it.
Edit `src/main/scala/Core.scala` and set memory to 16 kB (`16*1024`):

```scala
chisel3.Driver.execute(Array[String](), () => new Core(64, 16*1024, "insns.hex", 0x0))
```

Then link in the hello_world image:

```sh
ln -s samples/binaries/hello_world/hello_world.hex insns.hex
```

### Building and programming the FPGA

The `Makefile` currently supports the following FPGA boards by defining the `ECP5_BOARD` parameter on make:

* Lattice [ECP5 Evaluation Board](http://www.latticesemi.com/ecp5-evaluation) - `evn`
* Radiona [ULX3S](https://radiona.org/ulx3s/) - `ulx3s`
* Greg Davill [Orangecrab](https://github.com/gregdavill/OrangeCrab) - `orangecrab`
* Q3k [Colorlight](https://github.com/q3k/chubby75/tree/master/5a-75b) - `colorlight`

For example, to build for the Evaluation Board, run:

```sh
make ECP5_BOARD=evn synth
```

and to program the FPGA:

```sh
make ECP5_BOARD=evn prog

# or if your USB device has a different path, pass it on USBDEVICE, like:

make ECP5_BOARD=evn USBDEVICE=/dev/tty.usbserial-120001 prog
```

Programming using OpenOCD on Docker does not work on Docker Desktop for Mac since the container is run in a Linux VM and can not see the physical devices connected to the MacOS.

For the ULX3S board, the current OpenOCD does not support ft232 protocol so to program it, download [ujprog](https://github.com/emard/ulx3s-bin/tree/master/usb-jtag) for your platform and program using `./ujprog chiselwatt.bit` or to persist in the flash, `./ujprog -j FLASH chiselwatt.bit`.

After programming, if you connect to the serial port of the FPGA at 115200 8n1, you should see "Hello World"
and after that all input will be echoed to the output. On Linux, picocom can be used.
Another option below is a simple python script.

### Micropython

Unfortunately due to an issue in yosys/nextpnr, dual port RAMs are not
working. More details can be found in <https://github.com/YosysHQ/yosys/issues/1101>.

This means we use twice as much block RAM as you would expect. This also means
Micropython won't fit on an ECP5 85F, because the ~400kB of available BRAM is halved
to ~200k. Micropython requires 384 kB.

**Once this is fixed**, edit `src/main/scala/Core.scala` and set memory to 384 kB (`384*1024`):

```scala
chisel3.Driver.execute(Array[String](), () => new Core(64, 384*1024, "insns.hex", 0x0))
```

Then link in the micropython image:

```sh
ln -s samples/binaries/micropython/firmware.hex insns.hex
```

For example, to build for the ULX3S, run:

```sh
make ECP5_BOARD=ulx3s synth`
```

and to program the FPGA:

```sh
make ECP5_BOARD=ulx3s prog
```

If you connect to the serial port of the FPGA at 115200 8n1, you should see "Hello World"
and after that all input will be echoed to the output. On Linux, picocom can be used.
Another option below is a simple python script.

```python
#!/usr/bin/python

import serial

# configure the serial connections
ser = serial.Serial(
    port='/dev/ttyUSB1',
    baudrate=115200,
    parity=serial.PARITY_NONE,
    stopbits=serial.STOPBITS_ONE,
    bytesize=serial.EIGHTBITS
)

# read from serial
while 1:
    while ser.inWaiting() > 0:
        byte = ser.read(1);
        print("%s" %(byte))
```

## Building from scratch

### Hello World

The hello world sample simply prints "Hello from Chiselwatt, an OpenPower processor!"
and echoes the input back to the terminal as a serial console.
The source is in `./samples/hello_world` and it can be built by `make hello_world`.
The Makefile generates the `insns.hex` file that will be used on the synthesized
core to be run on Verilator or loaded into FPGA.

### Micropython

You can also build micropython from scratch. As a convenience, there is a Makefile
target to cross-build on a Docker container and generate the firmware.
Just run `make micropython` and it will generate the `insns.hex` file that will
be used on the synthesized core to be run on Verilator or loaded into FPGA.

If running on a container is not an option, you need a ppc64le box or a cross compiler.
This may be available on your distro, otherwise grab the the powerpc64le-power8
toolchain from [bootlin](https://toolchains.bootlin.com).

If you are cross compiling, point `CROSS_COMPILE` at the toolchain. In the
example below I installed it in `usr/local/powerpc64le-power8--glibc--bleeding-edge-2018.11-1/bin/`
and the tools begin with `powerpc64-linux-*`:

```sh
git clone https://github.com/micropython/micropython.git
cd micropython
cd ports/powerpc
make CROSS_COMPILE=/usr/local/powerpc64le-power8--glibc--bleeding-edge-2018.11-1/bin/powerpc64le-linux- -j$(nproc)
cd ../../../
```

* Build chiselwatt, import the the micropython image and run it. We use
bin2hex.py to convert a binary file into a series of 64 bit hex values
expected by the tools:

```sh
cd chiselwatt
make
scripts/bin2hex.py ../micropython/ports/powerpc/build/firmware.bin > insns.hex
./chiselwatt
```

## Issues

Now that it is functional, we have a number of things to add:

* A few instructions
* Wishbone interconnect
* Caches
* Pipelining and bypassing
