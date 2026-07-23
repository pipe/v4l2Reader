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
public class MmapReadMJpeg extends MmapRead {

    private final JpegDec decoder;
    private final ByteBuffer i420;
    private final ByteBuffer nv12;

    public MmapReadMJpeg(String dev, int w, int h, int rate) throws Throwable {
        super(dev, w, h, rate, V4L2_PIX_FMT_MJPEG);

        decoder = new JpegDec(1920, 1080);
        i420 = ByteBuffer.allocate(2 * 1920 * 1080);
        nv12 = ByteBuffer.allocate(3 * 1920 * 1080 / 2);
        Log.debug("i420 is " + i420.capacity());
        Log.debug("nv12 is " + nv12.capacity());
    }

    @Override
    public ByteBuffer process(ByteBuffer frame) {
        ByteBuffer ret = null;
        try {
            Log.debug("got frame  " + frame.remaining());
            var fbuffer = new byte[frame.remaining()];
            frame.get(fbuffer);
            frame.flip();
            // Files.write(peg, fbuffer, options);

            int r = decoder.decompress(frame, i420);
            //Files.write(f420, i420.array(), options);

            if (r == 0) {
                Log.debug("i420 frame of " + i420.remaining());
                JpegDec.i420ToNV12(i420, nv12, 1920, 1080);
                //Files.write(fnv12, nv12.array(), options);

                Log.debug("nv12 frame of " + nv12.remaining());
                ret = nv12;
            } else {
                Log.error("couldn't de compress jpeg");
            }
        } catch (Throwable t) {
            Log.error("can't transcode because " + t.getMessage());
            t.printStackTrace();
        }
        return ret;
    }

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
            var encoder = new Encoder(1920, 1080, 0, false, 30, false); 
            OpenOption[] options = {StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE};

            MmapReadMJpeg reader = new MmapReadMJpeg(adev, 1920, 1080, 30) {
                @Override
                public ByteBuffer process(ByteBuffer frame) {
                    ByteBuffer ret = super.process(frame);
                    try {
                        ret = encoder.encode(ret);
                        Log.debug("encoded frame to " + ret.remaining());
                    } catch (Throwable t) {
                        Log.error("can't transcode because " + t.getMessage());
                        t.printStackTrace();
                    }
                    return ret;
                }
            };
            reader.startCap();
            int fno = 0;
            var h264 = Paths.get("100.h264");

            var fout = Files.newOutputStream(h264, options);
            while (fno < 120) {
                if ((fno % 30) == 0) {
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
