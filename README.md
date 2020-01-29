# Chiselwatt

A tiny POWER Open ISA soft processor written in Chisel.

## Simulation using verilator

* Chiselwatt uses verilator for simulation. Either install this from your
distro or build it. Chisel uses sbt (the scala build tool), but unfortunately
most of the distros package an ancient version. On Fedora you can install an
upstream version using:

```
sudo dnf remove sbt
sudo curl https://bintray.com/sbt/rpm/rpm | sudo tee /etc/yum.repos.d/bintray-sbt-rpm.repo
sudo dnf --enablerepo=bintray--sbt-rpm install sbt
```

Next build chiselwatt:

```
git clone https://github.com/antonblanchard/chiselwatt
cd chiselwatt
make
```

* A micropython image is included in the repo. To use it, link the memory image into chiselwatt:

```
ln -s micropython/firmware.hex insns.hex
```

* Now run chiselwatt:

```
./chiselwatt
```

## Building micropython from scratch

* You can also build micropython from scratch. If you aren't building natively on a ppc64le box you will need a cross compiler. This may be available on your distro, otherwise grab the the powerpc64le-power8 toolchain from https://toolchains.bootlin.com/. If you are cross compiling, point CROSS_COMPILE at the toolchain. In the example below I installed it in usr/local/powerpc64le-power8--glibc--bleeding-edge-2018.11-1/bin/ and the tools begin with powerpc64-linux-

```
git clone https://github.com/micropython/micropython.git
cd micropython
cd ports/powerpc
make CROSS_COMPILE=/usr/local/powerpc64le-power8--glibc--bleeding-edge-2018.11-1/bin/powerpc64le-linux- -j$(nproc)
cd ../../../
```

* Build chiselwatt, import the the micropython image and run it:

```
cd chiselwatt
make
scripts/bin2hex.py ../micropython/ports/powerpc/build/firmware.bin > insns.hex
./chiselwatt
```

## Synthesis

Synthesis on FPGAs is supported with yosys/nextpnr. It uses Docker images, so no software other
than Docker needs to be installed. If you prefer podman you can use that too.

Edit Makefile to configure your FPGA, JTAG device etc. You will also need to configure the
amount of block RAM your FPGA supports, by editing `src/main/scala/Core.scala`. Here we are using
128kB of block RAM:

```
  chisel3.Driver.execute(Array[String](), () => new Core(64, 128*1024, "insns.hex", 0x0))
```

Unfortunately due to an issue in yosys/nextpnr, dual port RAMs are not working. This means we use
twice as much block RAM as you would expect. This also means Micropython likely won't fit (it needs
384 kB).

hello_world should run everywhere, so start with it. Edit `src/main/scala/Core.scala` and set memory
to `8*1024`. Then copy in the hello_world image:

```
cp hello_world/hello_world.hex insns.hex
```

To build:

```
make chiselwatt.bit
```

and to program the FPGA:

```
make prog
```

## Issues
Now that it is functional, we have a number of things to add
- A few instructions
- Wishbone interconnect
- Caches
- Pipelining and bypassing
