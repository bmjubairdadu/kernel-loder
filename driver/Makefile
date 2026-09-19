obj-m += kloader_driver.o

# Default: build against running kernel (for local test)
# For daisy 4.9.337 cross-build use: make -C <kernel_dir> M=$(PWD) ARCH=arm64 ...
all:
	$(MAKE) -C $(KDIR) M=$(PWD) modules

clean:
	$(MAKE) -C $(KDIR) M=$(PWD) clean
