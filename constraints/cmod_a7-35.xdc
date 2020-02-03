## Clock signal 12 MHz
set_property -dict { PACKAGE_PIN L17   IOSTANDARD LVCMOS33 } [get_ports { clock }];
create_clock -add -name sys_clk_pin -period 83.33 -waveform {0 41.66} [get_ports { clock }];

set_property -dict { PACKAGE_PIN A18   IOSTANDARD LVCMOS33 } [get_ports { reset }];

set_property -dict { PACKAGE_PIN J18   IOSTANDARD LVCMOS33 } [get_ports { io_tx }];
set_property -dict { PACKAGE_PIN J17   IOSTANDARD LVCMOS33 } [get_ports { io_rx }];

set_property -dict { PACKAGE_PIN A17   IOSTANDARD LVCMOS33 } [get_ports { io_terminate }];

set_property -dict { PACKAGE_PIN C16   IOSTANDARD LVCMOS33 } [get_ports { io_ledB }];
set_property -dict { PACKAGE_PIN B17   IOSTANDARD LVCMOS33 } [get_ports { io_ledC }];

set_property CONFIG_VOLTAGE 3.3 [current_design]
set_property CFGBVS VCCO [current_design]

set_property BITSTREAM.GENERAL.COMPRESS TRUE [current_design]
set_property BITSTREAM.CONFIG.CONFIGRATE 33 [current_design]
set_property CONFIG_MODE SPIx4 [current_design]
