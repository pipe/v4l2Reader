package pe.pi.v4l2reader;

import com.phono.srtplight.Log;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import pe.pi.amlh264enc.Encoder;
import pe.pi.turbojpeg.JpegDec;

/**
 *
 * @author thp
 */
public class ReadJpegs {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Log.setLevel(Log.VERB);
        String adev = "/dev/video0";
        if (args.length > 0) {
            adev = args[0];
        }
        try {
            var encoder = new Encoder(1920, 1080, 0, false, 30, true); // Encoder(int w, int h, int rotate, boolean mirror,int framerate,boolean yuv)
            var decoder = new JpegDec(1920, 1080);
            ByteBuffer raw = ByteBuffer.allocate(2 * 1920 * 1080);

            MmapRead reader = new MmapRead(adev, 1920, 1080, 30, V4l2Structs.V4L2_PIX_FMT_MJPEG) {
                @Override
                public ByteBuffer process(ByteBuffer frame) {
                    ByteBuffer ret = null;
                    try {
                        Log.debug("got frame  " + frame.remaining());
                        int r = decoder.decompress(frame, raw);

                        if (r == 0) {
                            Log.debug("raw frame of " + raw.remaining());
                            ret = encoder.encode(raw);
                            Log.debug("encoded frame to " + ret.remaining());
                        } else {
                            Log.error("couldn't de compress jpeg");
                        }
                    } catch (Throwable t) {
                        Log.error("can't transcode because " + t.getMessage());
                        t.printStackTrace();
                    }
                    return ret;

                }
            };
            reader.startCap();
            int fno = 0;
            var peg = Paths.get("100.h264");
            OpenOption[] options = {StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE};

            var fout = Files.newOutputStream(peg, options);
            while (fno < 300) {
                if ((fno % 30)== 0){
                    encoder.keyFrameReq();
                }
                var frame = reader.read();
                fno++;
                byte[] dst = new byte[frame.remaining()];
                Log.debug("copying bytes " + dst.length);
                frame.get(dst);
                fout.write(dst);
            }
            fout.close();
        } catch (Throwable ex) {
            Log.error("can't read from " + adev + " because " + ex.getMessage());
            ex.printStackTrace();
        }
    }

}
