set_property -dict {PACKAGE_PIN R4 IOSTANDARD LVCMOS33} [get_ports clock]
create_clock -period 10.000 -name sys_clk_pin -waveform {0.000 5.000} -add [get_ports clock]

set_property -dict {PACKAGE_PIN G4 IOSTANDARD LVCMOS15} [get_ports reset]

set_property -dict {PACKAGE_PIN AA19 IOSTANDARD LVCMOS33} [get_ports io_tx]
set_property -dict {PACKAGE_PIN V18 IOSTANDARD LVCMOS33} [get_ports io_rx]

set_property -dict { PACKAGE_PIN T14   IOSTANDARD LVCMOS25 } [get_ports { io_terminate }]

set_property -dict { PACKAGE_PIN T15   IOSTANDARD LVCMOS25 } [get_ports { io_ledB }]
set_property -dict { PACKAGE_PIN T16   IOSTANDARD LVCMOS25 } [get_ports { io_ledC }]

set_property CONFIG_VOLTAGE 3.3 [current_design]
set_property CFGBVS VCCO [current_design]

set_property BITSTREAM.GENERAL.COMPRESS TRUE [current_design]
set_property BITSTREAM.CONFIG.CONFIGRATE 33 [current_design]
set_property CONFIG_MODE SPIx4 [current_design]
