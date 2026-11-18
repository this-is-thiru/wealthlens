package com.thiru.wealthlens.taxplanning.documents.parser;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import lombok.extern.log4j.Log4j2;

/**
 * Marks the application's own process non-dumpable on Linux (W-131j C2a). A process run as the application's user, such as the
 * PDF reader {@link IsolatedPdfRead} starts once the application is not root, can then not read the application's
 * {@code /proc/<pid>/environ} (its secrets), {@code mem} or {@code maps}, nor trace it. Elsewhere (a developer's Mac) there is no
 * {@code /proc} to protect, and the call is skipped.
 *
 * <p>{@code prctl(PR_SET_DUMPABLE, 0)} is called through the Foreign Function API, then read back with {@code PR_GET_DUMPABLE}. A
 * Linux process that is still dumpable after it is a failed start (C3a): the application does not run with its secrets readable.
 */
@Log4j2
final class ProcessProtection {

    // linux/prctl.h
    private static final int PR_GET_DUMPABLE = 3;
    private static final int PR_SET_DUMPABLE = 4;

    private ProcessProtection() {
    }

    /** {@code prctl}'s shape: an option, then four arguments read as unsigned longs. */
    // Public, as MethodHandleProxies refuses any other interface (CI run 44340477367).
    @FunctionalInterface
    public interface Prctl {
        int call(int option, long second, long third, long fourth, long fifth);
    }

    static void makeNonDumpable() {
        if (!System.getProperty("os.name").startsWith("Linux")) {
            log.info("Not Linux ({}): the process is left dumpable, there is no /proc to protect", System.getProperty("os.name"));
            return;
        }
        Prctl prctl = prctl();
        int set = prctl.call(PR_SET_DUMPABLE, 0, 0, 0, 0);
        int now = prctl.call(PR_GET_DUMPABLE, 0, 0, 0, 0);
        if (set != 0 || now != 0) {
            throw new IllegalStateException("The process could not be made non-dumpable (prctl answered " + set + ", dumpable now " + now
                    + "), so a PDF reader could read its secrets; the application does not start");
        }
        log.info("The process is non-dumpable: its environment, memory and maps are closed to its own user's other processes");
    }

    private static Prctl prctl() {
        Linker linker = Linker.nativeLinker();
        MethodHandle handle = linker.downcallHandle(
                linker.defaultLookup().find("prctl").orElseThrow(() -> new IllegalStateException("This C library has no prctl")),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG),
                // prctl is variadic after its option
                Linker.Option.firstVariadicArg(1));
        return MethodHandleProxies.asInterfaceInstance(Prctl.class, handle);
    }
}
