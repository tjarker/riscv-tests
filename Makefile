
EXCLUDE:=isa/rv32ui/fence_i.S isa/rv32ui/fence.S isa/rv32ui/ma_data.S

TEST_SRCS:=$(filter-out $(EXCLUDE),$(wildcard isa/rv32ui/*.S))
TEST_NAMES:=$(notdir $(TEST_SRCS))

all: tests traces


tests: $(TEST_NAMES:%.S=build/tests/%.S)
test_bins: $(TEST_NAMES:%.S=build/test_bins/%.bin)
logs: $(TEST_NAMES:%.S=build/logs/%.log)

traces: logs
	@mkdir -p build/traces
	cd spike2csv && sbt "run -a $(foreach name, $(basename $(TEST_NAMES)), ../build/logs/$(name).log ../build/traces/$(name).csv)"

build/tests/%.S: isa/rv32ui/%.S
	@echo "Generating test $@"
	@mkdir -p $(dir $@)
	riscv32-unknown-elf-gcc \
		-E \
		-I env/ \
		-I env/student/ \
		-I isa/macros/scalar \
		-o $@ \
		$<

build/test_bins/%.bin: isa/rv32ui/%.S
	@mkdir -p $(dir $@)
	riscv32-unknown-elf-gcc \
		-march=rv32izicsr \
		-mabi=ilp32 \
		-nostartfiles \
		-nostdlib \
		-I env/ \
		-I env/golden/ \
		-I isa/macros/scalar \
		-T env/golden/link.ld \
		-o $@ \
		$<

build/logs/%.log: build/test_bins/%.bin
	@mkdir -p $(dir $@)
	spike --isa=RV32Izicsr -l --log=$@ --log-commits $<

clean:
	rm -rf build


dhrystone:
	riscv32-unknown-elf-gcc -march=rv32izicsr -mabi=ilp32 -nostartfiles -nostdlib -T benchmarks/common/test.ld -I benchmarks/dhrystone -I benchmarks/common -I env -o build/bench.out benchmarks/dhrystone/dhrystone_main.c