# Chiselwatt

A tiny POWER Open ISA soft processor written in Chisel.

## Simulation using verilator

* Chisel uses `sbt` (the scala build tool), but unfortunately most of the
distros package an ancient version. On Fedora you can install an upstream
version using:

```sh
$ sudo dnf remove sbt
$ sudo curl https://bintray.com/sbt/rpm/rpm | sudo tee /etc/yum.repos.d/bintray-sbt-rpm.repo
$ sudo dnf --enablerepo=bintray--sbt-rpm install sbt
```

* Chiselwatt uses `verilator` for simulation. Either install this from your
distro or build it. On Fedora you can install the distro version using:

```sh
$ sudo dnf install verilator
```

* Next build chiselwatt:

```sh
$ git clone https://github.com/antonblanchard/chiselwatt
$ cd chiselwatt
$ make
```

* A micropython image is included in the repo. To use it, link the memory image
into chiselwatt:

```sh
$ ln -s micropython/firmware.hex insns.hex
```

* Now run chiselwatt:

```sh
$ ./chiselwatt
```

## Building micropython from scratch

* You can also build micropython from scratch. If you aren't building natively
on a ppc64le box you will need a cross compiler. This may be available on your
distro, otherwise grab the the powerpc64le-power8 toolchain from [bootlin](https://toolchains.bootlin.com).
If you are cross compiling, point `CROSS_COMPILE` at the toolchain. In the
example below I installed it in usr/local/powerpc64le-power8--glibc--bleeding-edge-2018.11-1/bin/
and the tools begin with `powerpc64-linux-*`:

```sh
$ git clone https://github.com/micropython/micropython.git
$ cd micropython
$ cd ports/powerpc
$ make CROSS_COMPILE=/usr/local/powerpc64le-power8--glibc--bleeding-edge-2018.11-1/bin/powerpc64le-linux- -j$(nproc)
$ cd ../../../
```

* Build chiselwatt, import the the micropython image and run it. We use
bin2hex.py to convert a binary file into a series of 64 bit hex values
expected by the tools:

```sh
$ cd chiselwatt
$ make
$ scripts/bin2hex.py ../micropython/ports/powerpc/build/firmware.bin > insns.hex
$ ./chiselwatt
```

## Synthesis using Open Source tools (yosys/nextpnr)

Synthesis on FPGAs is supported with yosys/nextpnr. At the moment the tools support
Lattice ECP5 FPGAs.  It uses Docker images, so no software other than Docker needs
to be installed. If you prefer podman you can use that too, just adjust it in
`Makefile`, `DOCKER=podman`.

Edit `Makefile` to configure your FPGA, JTAG device etc.

### hello_world

hello_world should run everywhere, so start with it. Edit `src/main/scala/Core.scala`
and set memory to 16 kB (`16*1024`):

```
chisel3.Driver.execute(Array[String](), () => new Core(64, 16*1024, "insns.hex", 0x0))
```

Then link in the hello_world image:

```sh
$ ln -s hello_world/hello_world.hex insns.hex
```

To build:

```sh
$ make chiselwatt.bit
```

and to program the FPGA:

```sh
$ make prog
```

If you connect to the serial port of the FPGA at 115200 8n1, you should see "Hello World"
and after that all input will be echoed to the output. On Linux, picocom can be used.
Another option below is a simple python script.

### Micropython

Unfortunately due to an issue in yosys/nextpnr, dual port RAMs are not
working. More details can be found in https://github.com/YosysHQ/yosys/issues/1101.
This means we use twice as much block RAM as you would expect. This also means
Micropython won't fit on an ECP5 85F, because the ~400kB of available BRAM is halved
to ~200k. Micropython requires 384 kB.

Once this is fixed, edit `src/main/scala/Core.scala` and set memory to 384 kB (`384*1024`):

```
chisel3.Driver.execute(Array[String](), () => new Core(64, 384*1024, "insns.hex", 0x0))
```

Then link in the micropython image:

```sh
$ ln -s micropython/firmware.hex insns.hex
```

To build:

```sh
$ make chiselwatt.bit
```

and to program the FPGA:

```sh
$ make prog
```

## Simple Python script for reading USB serial port

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

## Issues

Now that it is functional, we have a number of things to add
- A few instructions
- Wishbone interconnect
- Caches
- Pipelining and bypassing
