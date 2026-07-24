package pe.pi.sonixcam;

import com.phono.srtplight.Log;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import java.lang.invoke.MethodHandle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author thp
 */
public class SonixCameraAPI {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Log.setLevel(Log.ALL);
        SonixCameraAPI sapi = new SonixCameraAPI();
        if (sapi.inited()) {
            try {
                switch (args.length) {
                    case 4:
                        int x = Integer.parseInt(args[0]);
                        int y = Integer.parseInt(args[1]);
                        int w = Integer.parseInt(args[2]);
                        int h = Integer.parseInt(args[3]);
                        Log.info("will set and enable RoI");
                        sapi.setRoi(x, y, w, h);
                        sapi.enableRoi(true);
                        break;
                    case 1:
                        Log.info("will (dis)enable RoI");
                        int v = Integer.parseInt(args[0]);
                        sapi.enableRoi(v == 1);
                        break;
                    default:
                        Log.info("will get RoI");
                        sapi.getRoi();
                        break;
                }
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        } else {
            Log.warn("can't init sonix camera - quitting");
        }

    }
    private final Arena arena;
    private MethodHandle SonixCam_Init;
    private MethodHandle SonixCam_UnInit;
    private MethodHandle SonixCam_AsicRegisterWrite;
    private MethodHandle SonixCam_AsicRegisterRead;
    boolean inited = false;

    public SonixCameraAPI() {
        arena = Arena.ofAuto();
        Linker linker = Linker.nativeLinker();
        try {
            var tjLib = SymbolLookup.libraryLookup("libSonixCamera.so", arena);
            if (tjLib != null) {
                Log.info("found libSonixCamera.so ");
                SonixCam_Init = linker.downcallHandle(
                        tjLib.find("SonixCam_Init").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, JAVA_INT)
                );
                SonixCam_UnInit = linker.downcallHandle(
                        tjLib.find("SonixCam_UnInit").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT)
                );
                SonixCam_AsicRegisterWrite = linker.downcallHandle(
                        tjLib.find("SonixCam_AsicRegisterWrite").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, JAVA_CHAR, ADDRESS, JAVA_LONG)
                );
                SonixCam_AsicRegisterRead = linker.downcallHandle(
                        tjLib.find("SonixCam_AsicRegisterWrite").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, JAVA_CHAR, ADDRESS, JAVA_LONG)
                );
            } else {
                Log.warn("Didn't find libSonixCamera.so ");
            }
        } catch (IllegalArgumentException iax) {
            Log.warn("Didn't load libSonixCamera.so " + iax.getMessage());

        }
        if (SonixCam_Init != null) {
            try {
                int ret = (int) SonixCam_Init.invokeExact(0);
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        try {
                            System.err.println("Sonix Camera API close in shutdown hook");
                            int r2 = (int) SonixCam_UnInit.invokeExact();
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                        }
                    }
                });
                Log.debug("SonixCam_Init returned "+ret);
                inited = (ret == 1);
            } catch (Throwable t) {
                Log.warn("can't control Sonix camera because " + t.getMessage());
            }
        }
    }

    public boolean inited() {
        return inited;
    }

    public boolean setRoi(int x, int y, int w, int h) throws Throwable {
        var ret = false;
        if (inited) {
            long rlen = 8;
            MemorySegment registers = arena.allocate(rlen);
            var regB = registers.asByteBuffer();
            regB.put((byte) ((0xff) & (x >> 8)));
            regB.put((byte) ((0xff) & (x)));
            regB.put((byte) ((0xff) & (y >> 8)));
            regB.put((byte) ((0xff) & (y)));
            regB.put((byte) ((0xff) & (w >> 8)));
            regB.put((byte) ((0xff) & (w)));
            regB.put((byte) ((0xff) & (h >> 8)));
            regB.put((byte) ((0xff) & (h)));
            int o = (int) SonixCam_AsicRegisterWrite.invokeExact((char)0x9FF, registers, rlen);
            Log.debug(" wrote RoI x=" + x + " y= " + y + " w= " + w + " h= " + h+ "o ="+o);
        } else {
            Log.warn("SonixAPI not inited - can't write Roi ");
        }
        return ret;
    }

    public int[] getRoi() throws Throwable {
        int[] ret = null;
        if (inited) {
            long rlen = 8;
            MemorySegment registers = arena.allocate(rlen);
            var regB = registers.asByteBuffer();
            int o = (int) SonixCam_AsicRegisterRead.invokeExact((char)0x9FF, registers, rlen);
            if (o == 1) {
                ret = new int[4];
                ret[0] = ((0xff) & regB.get()) << 8 | ((0xff) & regB.get());
                ret[1] = ((0xff) & regB.get()) << 8 | ((0xff) & regB.get());
                ret[2] = ((0xff) & regB.get()) << 8 | ((0xff) & regB.get());
                ret[3] = ((0xff) & regB.get()) << 8 | ((0xff) & regB.get());
                Log.debug(" reading RoI x=" + ret[0] + " y= " + ret[1] + " w= " + ret[2] + " h= " + ret[3]);
            } else {
                Log.error(" error reading RoI " + o);
            }
        } else {
            Log.warn("SonixAPI not inited - can't read Roi ");
        }
        return ret;
    }

    public void enableRoi(boolean en) throws Throwable {
        int v = en ? 1 : 0;
        if (inited) {
            long rlen = 1;
            MemorySegment registers = arena.allocate(rlen);
            var regB = registers.asByteBuffer();
            regB.put((byte) v);
            int o = (int)SonixCam_AsicRegisterWrite.invokeExact((char)0x9FE, registers, rlen);
            Log.debug(" wrote RoI enable =" + v+" o="+o);
        } else {
            Log.warn("SonixAPI not inited - can't enable Roi ");
        }
    }

}
