#include "syscall.h"

void AssertEq(int a,int b,int line){
	if (a!=b){
		printf("assert fail in line %d in child_1 %d!=%d\n",line,a,b);
	}
}

int main() {
	int process_id = exec("child_2.coff", 0, "");
	int status;
	int exit_status = join(process_id, &status);
	AssertEq(status, 1, __LINE__);
	int i;
	for(i = 0; i < 10; i ++)
		printf("from child_1, %d\n",i);
	process_id = exec("child_3.coff", 0, "");
	exit(process_id);
}