#include <stdlib.h>
#include "VCore.h"
#include "verilated.h" 
#include "verilated_vcd_c.h" 

/*
 * Current simulation time
 * This is a 64-bit integer to reduce wrap over issues and
 * allow modulus.  You can also use a double, if you wish.
 */
vluint64_t main_time = 0;

/*
 * Called by $time in Verilog
 * converts to double, to match
 * what SystemC does
 */
double sc_time_stamp(void)
{
	return main_time;      
}

#if VM_TRACE
VerilatedVcdC *tfp;
#endif

void tick(VCore *top)
{
	top->clock = 1;
	top->eval();
#if VM_TRACE
	if (tfp)
		tfp->dump((double) main_time);
#endif
	main_time++;

	top->clock = 0;
	top->eval();
#if VM_TRACE
	if (tfp)
		tfp->dump((double) main_time);
#endif
	main_time++;
}

void uart_tx(unsigned char tx);
unsigned char uart_rx(void);

int main(int argc, char **argv)
{
	Verilated::commandArgs(argc, argv);

	// init top verilog instance
	VCore* top = new VCore;

#if VM_TRACE
	// init trace dump
	Verilated::traceEverOn(true);
	tfp = new VerilatedVcdC;
	top->trace(tfp, 99);
	tfp->open("Core.vcd");
#endif

	// Reset
	top->reset = 1;
	for (unsigned long i = 0; i < 5; i++)
		tick(top);
	top->reset = 0;

	while(!Verilated::gotFinish()) {
		tick(top);

		//VL_PRINTF("NIA  %" VL_PRI64 "x\n", top->Core__DOT__executeNia);

		uart_tx(top->io_tx);
		top->io_rx = uart_rx();

		if (top->io_terminate) {
			for (unsigned long j = 0; j < 32; j++)
				VL_PRINTF("GPR%d %016" VL_PRI64 "X\n", j, top->Core__DOT__regFile__DOT__regs[j]);

			VL_PRINTF("CR 00000000%01X%01X%01X%01X%01X%01X%01X%01X\n",
				top->Core__DOT__conditionRegister_0,
				top->Core__DOT__conditionRegister_1,
				top->Core__DOT__conditionRegister_2,
				top->Core__DOT__conditionRegister_3,
				top->Core__DOT__conditionRegister_4,
				top->Core__DOT__conditionRegister_5,
				top->Core__DOT__conditionRegister_6,
				top->Core__DOT__conditionRegister_7);

			VL_PRINTF("LR %016" VL_PRI64 "X\n",
				  top->Core__DOT__linkRegister);
			VL_PRINTF("CTR %016" VL_PRI64 "X\n",
				  top->Core__DOT__countRegister);

			/*
			 * We run for one more tick to allow any debug
			 * prints in this cycle to make it out.
			 */
			tick(top);
#if VM_TRACE
			tfp->close();
#endif
			exit(1);
		}
	}

#if VM_TRACE
	tfp->close();
	delete tfp;
#endif

	delete top;
}
