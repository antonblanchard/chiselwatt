module toplevel(
	input clock,
	input reset,
	output io_tx,
	input io_rx,
	output io_terminate,
	output io_ledB,
	output io_ledC
);

wire clock_out;
logic reset_out;
wire lock;

pll_ecp5_evn pll(
	.clki(clock),
	.clko(clock_out),
	.lock(lock)
);

Core core(
	.clock(clock_out),
	.reset(reset_out),
	.io_tx(io_tx),
	.io_rx(io_rx),
	.io_terminate(io_terminate),
	.io_ledB(io_ledB),
	.io_ledC(io_ledC)
);

logic [21:0] cnt = ~0;

always_ff@(posedge clock)
begin
	if (~lock || ~reset)
	begin
		cnt <= ~0;
	end
	else if (cnt != 0)
	begin
		cnt <= cnt - 1;
	end

	reset_out <= |cnt;
end

endmodule
