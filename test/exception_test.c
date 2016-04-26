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
	// ****************************************  ATTENTION!!!
	// Should not return 0 here in creat(16). Take care of what to do when open file exists 16.
	AssertEq(creat("16"), -1, __LINE__);
}


void halt_from_invalid() {
	int child_id = exec("halt.coff", 0, "");
	int status;
	int exit_status = join(child_id, &status);
	// ****************************************  ATTENTION!!!
	// Run the halt process which is not a root, the halt process should return immediately according
	// to the halt definition in syscall.h, however the command after halt is executed.
	AssertEq(exit_status, 1, __LINE__);
	// Non-exist process id, should cause a exception.
	exit_status = join(child_id+10, &status);
	AssertEq(exit_status, -1, __LINE__);
}

void join_test() {
	int process_id = exec("child_1.coff", 0, "");
	int status;
	int exit_status = join(process_id, &status); // The exit code of child_1 is the process id of child_3.
	int new_status;
	exit_status = join(status, &new_status);
	// child_3 is not owned by this process, could not join and return -1, cause an exception.
	AssertEq(exit_status, -1, __LINE__);
	//****************************************  ATTENTION!!!
	// Join child_1 again, return -1 (I think here should return -1 according to syscall.h).
	exit_status = join(process_id, &new_status);
	AssertEq(exit_status, -1, __LINE__);
}

int main() {
	halt_from_invalid();
	join_test();
	//****************************************  ATTENTION!!!
	// If you comment the two procedure above, the output of the following two test 
	// will be different, haven't figure out the reason, cause there's no file operation
	// in above two procedures.
	file_limit_test();
	open_nofile_test();
	
}	