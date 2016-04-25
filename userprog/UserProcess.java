package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.LinkedList;

class FileDescriptorList {
    private OpenFile[] fd;

    public FileDescriptorList(int n) {
        fd = new OpenFile[n];
    }

    public int getFreeFD() {
        for (int i = 0; i < fd.length; ++i)
            if (fd[i] == null)
                return i;
//        SyscallException.check(false, "No Free File Description");
        return -1;
    }

    public OpenFile get(int id) {
//        Lib.assertTrue(fd[id] != null, "empty fd set when getting");
        return fd[id];
    }

    public void set(int id, OpenFile file) {
//        SyscallException.check(fd[id] == null, "non-empty fd when setting");
        fd[id] = file;
    }

    public void free(int id) {
//        SyscallException.check(fd[id] != null, "empty fd");
        fd[id] = null;
    }
}

class SyscallException extends Exception {
    private static boolean shouldPrintStackTrace = false;

    public void print() {
        if (shouldPrintStackTrace)
            printStackTrace();
    }

    public SyscallException(String message) {
        super(message);
    }

    public static void check(boolean b) throws SyscallException {
        if (!b) {
            throw new SyscallException("");
        }
    }

    public static void check(boolean b, String message) throws SyscallException {
        if (!b) {
            throw new SyscallException(message);
        }
    }
}

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 * <p>
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages];
//        for (int i = 0; i < numPhysPages; i++)
//            pageTable[i] = new TranslationEntry(i, i, true, false, false, false);

        fileDescriptorList = new FileDescriptorList(maxOpenedFile);
        fileDescriptorList.set(0, UserKernel.console.openForReading());
        fileDescriptorList.set(1, UserKernel.console.openForWriting());

        numProcesses += 1;
        processId = numProcesses;
        ++runningProcesses;
    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;

        UThread thread = new UThread(this);
        if (mainThread == null)
            mainThread = thread;
        thread.setName(name).fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param vaddr     the starting virtual address of the null-terminated
     *                  string.
     * @param maxLength the maximum number of characters in the string,
     *                  not including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     * found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data  the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to read.
     * @param data   the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to
     *               the array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
                                 int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // for now, just assume that virtual addresses equal physical addresses
        if (vaddr < 0 || vaddr >= memory.length)
            return 0;

        int bound = Math.min(length, memory.length - vaddr), amount = 0;
        for (int i = 0; i < bound; ++i) {
            int vpn = vaddr + i;
            TranslationEntry page = pageTable[vpn / pageSize];
            if (page == null || !page.valid) {
                Lib.debug('o', "Error when reading memory");
                break;
            }
            page.used = true;
            int ppn = page.ppn * pageSize + vpn % pageSize;
            data[i] = memory[ppn];
            amount = i + 1;
        }

//        System.arraycopy(memory, vaddr, data, offset, amount);

        return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data  the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to write.
     * @param data   the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to
     *               virtual memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
                                  int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // for now, just assume that virtual addresses equal physical addresses
        if (vaddr < 0 || vaddr >= memory.length)
            return 0;

        int bound = Math.min(length, memory.length - vaddr), amount = 0;
        for (int i = 0; i < bound; ++i) {
            int vpn = vaddr + i;
            TranslationEntry page = pageTable[vpn / pageSize];
            if (page == null || !page.valid || page.readOnly) {
                Lib.debug('o', "Error when writing memory");
                return -1;
            }
            page.used = true;
            int ppn = page.ppn * pageSize + vpn % pageSize;
            memory[ppn] = data[i];
            amount = i + 1;
        }
//        System.arraycopy(data, offset, memory, vaddr, amount);

        return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = UserKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        for (int i = 0; i < stackPages; ++i) {
            int vpn = numPages + i;
            int ppn = UserKernel.allocPage();
            pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, false, false);
        }
        numPages += stackPages;

        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        pageTable[numPages] = new TranslationEntry(numPages, UserKernel.allocPage(), true, false, false, false);
        numPages++;

        Lib.debug('o', String.format("numpages: %d", numPages));
        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
                    argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[]{0}) == 1);
            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        if (numPages > Machine.processor().getNumPhysPages()) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }

        // load sections
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                    + " section (" + section.getLength() + " pages)");

            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                int ppn = UserKernel.allocPage();
//                Lib.debug('o', String.format("allocating VP %d", vpn));

                pageTable[vpn] = new TranslationEntry(vpn, ppn, true, section.isReadOnly(), false, false);

                // for now, just assume virtual addresses=physical addresses
                section.loadPage(i, ppn);
            }
        }

        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        for (int i = 0; i < numPages; ++i) {
            int ppn = pageTable[i].ppn;
            UserKernel.freePage(ppn);
        }
        for (int i = 0; i < maxOpenedFile; ++i) {
            OpenFile file = fileDescriptorList.get(i);
            if (file != null) {
                file.close();
            }
        }
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {

        if (processId != 1) { // the first process
            return -1;
        }
        Machine.halt();

        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

    private int handleExit(int status) {
        int ret = -1;
//        try {
            exitCode = status;
            unloadSections();

//            for (UserProcess child : children) {
//                child.parent = null;
//            }

            --runningProcesses;
            if (runningProcesses == 0)
                Kernel.kernel.terminate();
            else
                UThread.finish();

            ret = 0;
//        } catch (SyscallException e) {
//            e.print();
//        }
        return ret;
    }

    void checkAddressValidity(int addr) throws SyscallException {
        boolean invalid = false;
        if (addr < 0 || addr / pageSize >= numPages)
            invalid = true;
        else {
            TranslationEntry page = pageTable[addr / pageSize];
        }

        if (invalid)
            throw new SyscallException("invalid address");
    }

    private int handleJoin(int pid, int retStatusAddr) {
        int ret = -1;
        try {
            checkAddressValidity(retStatusAddr);
            UserProcess childProcess = null;
            for (UserProcess child : children) {
                if (child.processId == pid) {
                    childProcess = child;
                    break;
                }
            }
            SyscallException.check(childProcess != null);
//            Lib.assertTrue(childProcess != null);
            childProcess.mainThread.join();

            byte[] exitBytes = Lib.bytesFromInt(childProcess.exitCode);
            SyscallException.check(writeVirtualMemory(retStatusAddr, exitBytes) == 4);

            if (childProcess.unexpectedException != NO_EXCEPTION)
                ret = 0;
            else
                ret = 1;
        } catch (SyscallException e) {
            e.print();
        }
        return ret;
    }

    private int handleExec(int nameAddr, int argc, int argvAddr) {
        int ret = -1;

        try {
            checkAddressValidity(nameAddr);
            checkAddressValidity(argvAddr);
            SyscallException.check(0 <= argc && argc < 256);

            String filename = readVirtualMemoryString(nameAddr, 256);
            String[] argv = new String[argc];
            for (int i = 0; i < argc; ++i) {
                byte[] curArg = new byte[4];
                readVirtualMemory(argvAddr + i * 4, curArg);

                int argAddress = Lib.bytesToInt(curArg, 0);

                argv[i] = readVirtualMemoryString(argAddress, 256);
            }
            UserProcess child = newUserProcess();
            children.add(child);
//            child.parent = this;
            if (!child.execute(filename, argv))
                ret = -1;
            else
                ret = child.processId;

        } catch (SyscallException e) {
            e.print();
        }
        return ret;
    }

    private int handleCreate(int filenameAddr) {
        int ret = -1;
        try {
            checkAddressValidity(filenameAddr);

            String filename = readVirtualMemoryString(filenameAddr, 256);
            int fd = fileDescriptorList.getFreeFD();
            SyscallException.check(fd != -1, "no more file descriptors");

            OpenFile file = UserKernel.fileSystem.open(filename, true);
            fileDescriptorList.set(fd, file);
            SyscallException.check(UserKernel.fileReference.open(filename));

            ret = -1;
        } catch (SyscallException e) {
            e.print();
        }
        return ret;
    }

    private int handleOpen(int filenameAddr) {
        int ret = -1;
        try {
            checkAddressValidity(filenameAddr);

            String filename = readVirtualMemoryString(filenameAddr, 256);
            int fd = fileDescriptorList.getFreeFD();
            SyscallException.check(fd != -1, "no more file descriptors");

            OpenFile file = UserKernel.fileSystem.open(filename, false);
            fileDescriptorList.set(fd, file);
            SyscallException.check(UserKernel.fileReference.open(filename));

            ret = fd;
        } catch (SyscallException e) {
            e.print();
        }
        return ret;
    }

    private int handleRead(int fd, int bufferAddr, int size) {
        int ret = -1;
        try {
            SyscallException.check(0 <= fd && fd < maxOpenedFile);
            checkAddressValidity(bufferAddr);
            if (size > 0)
                checkAddressValidity(bufferAddr + size - 1);
            OpenFile file = fileDescriptorList.get(fd);
            SyscallException.check(file != null, "invalid file descriptor");

            byte[] buffer = new byte[size];
            int bytesReadFromFile = file.read(buffer, 0, size);
            SyscallException.check(writeVirtualMemory(bufferAddr, buffer) == bytesReadFromFile);
            ret = bytesReadFromFile;
        } catch (SyscallException e) {
            e.print();
        }
        return ret;
    }

    private int handleWrite(int fd, int bufferAddr, int size) {
        int ret = -1;
        try {
            SyscallException.check(0 <= fd && fd < maxOpenedFile);
            SyscallException.check(0 <= size && size < 65536, "improper size");
            checkAddressValidity(bufferAddr);
            if (size > 0)
                checkAddressValidity(bufferAddr + size - 1);

            OpenFile file = fileDescriptorList.get(fd);
            SyscallException.check(file != null, "invalid file descriptor");

            byte[] buffer = new byte[size];
            SyscallException.check(readVirtualMemory(bufferAddr, buffer) == size);
            ret = file.write(buffer, 0, size);
        } catch (SyscallException e) {
            e.print();
        }
        return ret;
    }

    private int handleClose(int fd) {
        int ret = -1;
        try {
            SyscallException.check(0 <= fd && fd < maxOpenedFile);
            OpenFile file = fileDescriptorList.get(fd);
            SyscallException.check(file != null, "file not opened");

            file.close();
            fileDescriptorList.free(fd);
            UserKernel.fileReference.close(file.getName());

            ret = 0;
        } catch (SyscallException e) {
            e.print();
        }
        return ret;
    }

    private int handleUnlink(int name) {
        int ret = -1;
        try {
            checkAddressValidity(name);

            String filename = readVirtualMemoryString(name, 256);
            SyscallException.check(UserKernel.fileSystem.remove(filename));
            SyscallException.check(UserKernel.fileReference.remove(filename));

            ret = 0;
        } catch (SyscallException e) {
            e.print();
        }
        return ret;
    }

    private static final int
            syscallHalt = 0,
            syscallExit = 1,
            syscallExec = 2,
            syscallJoin = 3,
            syscallCreate = 4,
            syscallOpen = 5,
            syscallRead = 6,
            syscallWrite = 7,
            syscallClose = 8,
            syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     * <p>
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * </tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     *
     * @param syscall the syscall number.
     * @param a0      the first syscall argument.
     * @param a1      the second syscall argument.
     * @param a2      the third syscall argument.
     * @param a3      the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallHalt:
                return handleHalt();

            case syscallExit:
                return handleExit(a0);

            case syscallExec:
                return handleExec(a0, a1, a2);

            case syscallJoin:
                return handleJoin(a0, a1);

            case syscallCreate:
                return handleCreate(a0);

            case syscallOpen:
                return handleOpen(a0);

            case syscallRead:
                return handleRead(a0, a1, a2);

            case syscallWrite:
                return handleWrite(a0, a1, a2);

            case syscallClose:
                return handleClose(a0);

            case syscallUnlink:
                return handleUnlink(a0);

            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
                handleException(Processor.exceptionSyscall);
        }
        return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param cause the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                        processor.readRegister(Processor.regA0),
                        processor.readRegister(Processor.regA1),
                        processor.readRegister(Processor.regA2),
                        processor.readRegister(Processor.regA3)
                );
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;

            default:
                Lib.debug(dbgProcess, "Unexpected exception: " +
                        Processor.exceptionNames[cause]);
                unexpectedException = cause;
                handleExit(0);
                Lib.assertNotReached("Unexpected exception");
        }
    }

    /**
     * The program being run by this process.
     */
    protected Coff coff;

    /**
     * This process's page table.
     */
    protected TranslationEntry[] pageTable;
    /**
     * The number of contiguous pages occupied by the program.
     */
    protected int numPages;

    /**
     * The number of pages in the program's stack.
     */
    protected final int stackPages = 8;

    private int initialPC, initialSP;
    private int argc, argv;

    private FileDescriptorList fileDescriptorList;

    private int processId;
    private static int numProcesses = 0;
    private static int runningProcesses = 0;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    public static final int maxOpenedFile = 16;

    private LinkedList<UserProcess> children = new LinkedList<>();
    private UThread mainThread = null;

    private int exitCode = 0;

    public int NO_EXCEPTION = -1;
    private int unexpectedException = NO_EXCEPTION;

}
