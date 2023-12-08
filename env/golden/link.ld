OUTPUT_ARCH( "riscv" )
ENTRY(_init_start)

SECTIONS
{
  . = 0xF0000000;
  .init : { *(.init) }
  . = 0x80000000;
  .text : { *(.text) }
  . = ALIGN(0x1000);
  .data : { *(.data) }
  .bss : { *(.bss) }
  . = 0xFF000000;
  .tohost : { *(.tohost) }
  _end = .;
}

