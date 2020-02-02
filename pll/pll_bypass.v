module pll(
  input  clki,
  output clko,
  output lock
);

  always @* begin
    lock <= 1;
    clko <= clki;
  end

endmodule
