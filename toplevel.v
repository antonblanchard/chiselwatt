module toplevel #(
  parameter RESET_LOW = 1
) (
  input clock,
  input reset,
  output io_tx,
  input io_rx,
  output io_terminate,
  output io_ledB,
  output io_ledC
);

  wire clock_out;
  reg reset_out;
  wire lock;

  Chiselwatt_pll chiselwatt_pll(
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

  reg [21:0] cnt = ~0;

  always@(posedge clock) begin
    if (~lock || (reset ^ RESET_LOW)) begin
      cnt <= ~0;
    end else if (cnt != 0) begin
      cnt <= cnt - 1;
    end

    reset_out <= |cnt;
  end

endmodule
