package pe.pi.v4l2reader;

import com.phono.srtplight.Log;
import pe.pi.amlh264enc.Encoder;

/**
 *
 * @author thp
 */
public class ReadAndEncode {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Log.setLevel(Log.DEBUG);
        String adev = "/dev/media0";
        if (args.length > 0) {
            adev = args[0];
        }
        try {
            Encoder encoder;
            encoder = new Encoder(1920, 1080);

            MmapRead reader = new MmapRead(adev) {
                @Override
                public byte[] process(byte [] frame) {
                    var h = encoder.encode(out);
                    Log.debug("encoded to h264 " + h.length);
                    return h;
                }
            };
            reader.startCap();
            while (true) {
                reader.read();
            }
        } catch (Throwable ex) {
            Log.error("can't read from " + adev + " because " + ex.getMessage());
            ex.printStackTrace();
        }
    }

}
