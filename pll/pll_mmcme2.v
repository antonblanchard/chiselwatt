module pll_ecp5_evn(input clki, output clko, output lock);

wire clkfb;

MMCME2_BASE #(
        .BANDWIDTH("OPTIMIZED"),
        .CLKFBOUT_MULT_F(50.0),
        .CLKIN1_PERIOD(83.33),
        .CLKOUT0_DIVIDE_F(12.0),
        .DIVCLK_DIVIDE(1),
        .STARTUP_WAIT("FALSE")
    )
    MMCME2_BASE_inst (
        .CLKOUT0(clko),
        .CLKFBOUT(clkfb),
        .LOCKED(lock),
        .CLKIN1(clki),
        .PWRDWN(1'b0),
        .RST(1'b0),
        .CLKFBIN(clkfb)
    );
endmodule
