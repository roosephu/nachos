#include "syscall.h"

int main() {
	int i;
	for(i = 0; i < 10; i ++)
		printf("from child_2, %d\n",i);
	exit(1);
}
