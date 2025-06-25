/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package pe.pi.v4l2reader;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import java.lang.invoke.MethodHandle;
import static pe.pi.v4l2reader.V4l2Ioctls.linker;

/**
 *
 * @author thp
 */
public class Mmap {

    static final MethodHandle munmap;
    static final MethodHandle mmap;
    static final SymbolLookup libc;

    static {
        libc = linker.defaultLookup(); //
        munmap = linker.downcallHandle(
                libc.find("munmap").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG)
        );
        mmap = linker.downcallHandle(
                libc.find("mmap").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG)
        );
    }

    Mmap() {
    }
}
