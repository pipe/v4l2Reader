package pe.pi.v4l2reader;

import com.phono.srtplight.Log;
import java.io.IOException;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import java.lang.foreign.MemorySegment;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import pe.pi.amlh264enc.AMLEncoder;
import pe.pi.amlh264enc.Encoder;
import pe.pi.sonixcam.SonixCameraAPI;
import pe.pi.turbojpeg.JpegDec;
import static pe.pi.v4l2reader.V4l2Structs.V4L2_CID_EXPOSURE;

/**
 *
 * @author thp
 */
public class MmapReadMJpeg extends MmapRead {

    final static int TJSAMP_444 = 0;
    final static int TJSAMP_422 =1;
    final static int TJSAMP_420 =2;
    final static int TJSAMP_GRAY =3;
    final static int TJSAMP_440 = 4;
    final static int TJSAMP_411 =5;
    
    private final JpegDec decoder;
    private final ByteBuffer i4xx;
    private final ByteBuffer nv12;
    private SonixV4l2Substitute v4l2Sub;



    public MmapReadMJpeg(String dev, int w, int h, int rate) throws Throwable {
        super(dev, w, h, rate, V4L2_PIX_FMT_MJPEG);

        decoder = new JpegDec(w, h);
        i4xx = ByteBuffer.allocate(2 * w * h);
        nv12 = ByteBuffer.allocate(3 * w * h / 2);
        Log.debug("i4xx is " + i4xx.capacity());
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

            int r = decoder.decompress(frame, i4xx);
            //Files.write(f420, i4xx.array(), options);

            if (r >= 0) {
                switch (r) {
                    case TJSAMP_420 -> {
                        Log.debug("i420 frame of " + i4xx.remaining());
                        JpegDec.i420ToNV12(i4xx, nv12, width, height);
                        Log.debug("nv12 frame of " + nv12.remaining());
                        ret = nv12;
                    }
                    case TJSAMP_422 -> {
                        Log.debug("i422 frame of " + i4xx.remaining());
                        JpegDec.i422ToNV12(i4xx, nv12, width, height);
                        Log.debug("nv12 frame of " + nv12.remaining());
                        ret = nv12;
                    }
                    default -> {
                        Log.debug("unexpected jpec frame decode  frame of "+r);
                        ret = i4xx;
                    }
                }
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
            var encoder = new AMLEncoder(1920, 1080, 0, false, 30, false);
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

    @Override
    public V4l2Substitute getV4l2Sub() {
        if (v4l2Sub == null) {
            v4l2Sub = new SonixV4l2Substitute(this);
        }
        return v4l2Sub;
    }

    class SonixV4l2Substitute extends MmapV4l2Substitute {

        String sensorName;
        Long ae_roi = 0L;
        Long ae_auto = 1L;
        SonixCameraAPI sapi;

        SonixV4l2Substitute(MmapRead m) {
            super(m);
            sensorName = "unknown";
            String video = path.getFileName().toString();
            var mpath = Paths.get("/sys/class/video4linux/" + video + "/device/../manufacturer");
            if (Files.isReadable(path)) {
                try {
                    sensorName = Files.readString(mpath);
                    Log.info("sensorname is " + sensorName);
                } catch (IOException ex) {
                    Log.info("can't read from " + mpath.toString());
                }
            } else {
                Log.info("no readable device info at " + mpath.toString());
            }
            sapi = new SonixCameraAPI();

        }

        @Override
        public String getSensorName() {
            return sensorName;
        }

        // these don't work at the moment so we parrot - unless we have a working sonixAPI.
        @Override
        public void setAERoi(Long v) {
            ae_roi = v;
            int x = (int) ((width * ((0xff) & (v >>> 24))) / 128);
            int y = (int) ((height * ((0xff) & (v >>> 16))) / 128);
            int x2 = (int) ((width * ((0xff) & (v >>> 8))) / 128);
            int y2 = (int) ((height * ((0xff) & (v))) / 128);
            int w = x2 - x;
            int h = y2 - y;
            if ((sapi != null) && sapi.inited()) {
                try {
                    sapi.setRoi(x, y, w, h);
                } catch (Throwable t) {
                    Log.warn("Can't set AE");
                }
            }

            //setBasicV4l2Control(V4L2_isp_ae_roi, v.intValue());
        }

        @Override
        public Long getAERoi() {
            return ae_roi;
        }

        @Override
        public String getAE() {
            return ae_auto.toString();
        }

        @Override
        public void setAE(Long v) {
            if ((sapi != null) && sapi.inited()) {
                try {
                    sapi.enableRoi(v == 3);
                } catch (Throwable t) {
                    Log.warn("Can't set AE");
                }
            } else {
                setBasicV4l2Control(V4L2_CID_AE, v.intValue());
            }
            ae_auto = v;
        }

    }
}
