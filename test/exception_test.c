#include "syscall.h"

void AssertEq(int a,int b,int line){
	if (a!=b){
		printf("assert fail in line %d in exception_test %d!=%d\n",line,a,b);
	}
}

void open_nofile_test() {
	AssertEq(open("file_not_exist"), -1, __LINE__);
}

void file_limit_test() {
	AssertEq(creat("2"), 2, __LINE__);
	AssertEq(creat("3"), 3, __LINE__);
	AssertEq(creat("4"), 4, __LINE__);
	AssertEq(creat("5"), 5, __LINE__);
	AssertEq(creat("6"), 6, __LINE__);
	AssertEq(creat("7"), 7, __LINE__);
	AssertEq(creat("8"), 8, __LINE__);
	AssertEq(creat("9"), 9, __LINE__);
	AssertEq(creat("10"), 10, __LINE__);
	AssertEq(creat("11"), 11, __LINE__);
	AssertEq(creat("12"), 12, __LINE__);
	AssertEq(creat("13"), 13, __LINE__);
	AssertEq(creat("14"), 14, __LINE__);
	AssertEq(creat("15"), 15, __LINE__);
	AssertEq(creat("16"), -1, __LINE__);
}


void halt_from_invalid() {
	int child_id = exec("halt.coff", 0, (char*)0);
	int status;
	int exit_status = join(child_id, &status);
	AssertEq(exit_status, 1, __LINE__);
	// Non-exist process id, should cause a exception.
	exit_status = join(child_id+10, &status);
	AssertEq(exit_status, -1, __LINE__);
}

void join_test() {
	int process_id = exec("child_1.coff", 0, (char*)0);
	int status;
	int exit_status = join(process_id, &status); // The exit code of child_1 is the process id of child_3.
	int new_status;
	exit_status = join(status, &new_status);
	// child_3 is not owned by this process, could not join and return -1, cause an exception.
	AssertEq(exit_status, -1, __LINE__);
	
	//****************************************  ATTENTION!!!
	//****************************************  ATTENTION!!!
	// Join child_1 again, return -1 (I think here should return -1 according to syscall.h).
	exit_status = join(process_id, &new_status);
	AssertEq(exit_status, -1, __LINE__);
}

void close_stdin_test() {

	int status = unlink("stdin");
	AssertEq(status, -1, __LINE__);
	status = unlink("2");
	AssertEq(status, 0, __LINE__);
	status = unlink("file_not_exist");
	AssertEq(status, -1, __LINE__);
	status = unlink("");
	AssertEq(status, -1, __LINE__);
	char* empty;
	status = unlink(empty);
	AssertEq(status, -1, __LINE__);
	
	status = close(0);
	AssertEq(status, 0, __LINE__);
	//****************************************  ATTENTION!!!
	//****************************************  ATTENTION!!!
	// If join(commented now), everything is good, if no join, everything is strange.
	// No join, output is random code, file desciptor is wrong. 
	// If comment close(0), nothing strange, but why is stdin related?
	int child_id = exec("halt.coff", 0, (char*)0);
	//AssertEq(join(child_id, &status), 1, __LINE__);
	status = unlink("halt.coff");
	AssertEq(status, 0, __LINE__);	
}

int main() {
	//halt_from_invalid();
	//join_test();
	
	close_stdin_test();
	file_limit_test();
	open_nofile_test();
	
}	