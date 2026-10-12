package pe.pi.libav;

import com.phono.srtplight.Log;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemoryLayout.PathElement.sequenceElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import pe.pi.v4l2reader.Decoder;

/**
 *
 * @author thp
 */

/*

#include <stdio.h>
#include <string.h>

#include <libavcodec/avcodec.h>
#include <libavutil/error.h>

typedef struct {
    AVCodecContext *ctx;
    AVPacket *pkt;
    AVFrame *frame;
} H264Decoder;

int h264_decoder_init(H264Decoder *d)
{
    const AVCodec *codec;

    *d = (H264Decoder){0};

    codec = avcodec_find_decoder(AV_CODEC_ID_H264);
    if (!codec) {
        fprintf(stderr, "H.264 decoder not found\n");
        return -1;
    }

    d->ctx = avcodec_alloc_context3(codec);
    d->pkt = av_packet_alloc();
    d->frame = av_frame_alloc();

    if (!d->ctx || !d->pkt || !d->frame)
        goto fail;

    if (avcodec_open2(d->ctx, codec, NULL) < 0) {
        fprintf(stderr, "Failed to open H.264 decoder\n");
        goto fail;
    }

    return 0;

fail:
    av_frame_free(&d->frame);
    av_packet_free(&d->pkt);
    avcodec_free_context(&d->ctx);
    return -1;
}

//*
 * Submit one complete H.264 packet/access unit.
 * For every decoded frame, on_frame() is called.
 *
 * The AVFrame is valid only during the callback unless
 * the caller takes its own reference or makes a copy.
/
int h264_decoder_decode(
    H264Decoder *d,
    const unsigned char *data,
    int size,
    void (*on_frame)(const AVFrame *frame, void *user),
    void *user)
{
    int ret;

    ret = av_new_packet(d->pkt, size);
    if (ret < 0)
        return ret;

    memcpy(d->pkt->data, data, size);

    ret = avcodec_send_packet(d->ctx, d->pkt);
    av_packet_unref(d->pkt);

    if (ret < 0)
        return ret;

    for (;;) {
        ret = avcodec_receive_frame(d->ctx, d->frame);

        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            return 0;

        if (ret < 0)
            return ret;

        if (on_frame)
            on_frame(d->frame, user);

        av_frame_unref(d->frame);
    }
}

void h264_decoder_close(H264Decoder *d)
{
    av_frame_free(&d->frame);
    av_packet_free(&d->pkt);
    avcodec_free_context(&d->ctx);
    *d = (H264Decoder){0};
}

 */
public class H264Dec extends Decoder implements AutoCloseable {

    private final Arena arena;
    private final MethodHandle alloc_context3;
    private final MethodHandle find_decoder;
    private final static int AV_CODEC_ID_H264 = 27; // 
    private final MethodHandle new_packet;
    private final MethodHandle send_packet;
    private final MethodHandle packet_unref;
    private final MethodHandle receive_frame;
    private final MethodHandle frame_unref;
    private final MethodHandle frame_alloc;
    private final MethodHandle packet_alloc;
    private final MethodHandle codec_open2;
    private MemorySegment frame;
    private final MethodHandle frame_free;
    private final MethodHandle packet_free;
    private final MethodHandle codec_free_context;
    private final MemorySegment codec;
    private MemorySegment ctx;
    private MemorySegment pkt;
    final static int AVPACKETSIZE = 0x68;
    final static int AVFRAMESIZE = 0x1E0;
    final static int AVCODECCONTEXTSIZE = 0x3B0;
    final static int AVCODECCONTEXTFLAGSOFFS = 0x4C;
    final static int AVCODECCONTEXTTHREADS = 0x27C; 

    final static int AV_CODEC_FLAG_LOW_DELAY = (1 << 19);

    /*
     private static final ValueLayout.OfAddress PTR =
            ValueLayout.ADDRESS;

    private static final ValueLayout.OfInt I32 =
            ValueLayout.JAVA_INT;

    private static final ValueLayout.OfLong I64 =
            ValueLayout.JAVA_LONG;
     */
    public static final GroupLayout AVRational = MemoryLayout.structLayout(
            JAVA_INT.withName("num"),
            JAVA_INT.withName("den")
    ).withName("AVRational");

    public static final GroupLayout AVPacketLayout = MemoryLayout.structLayout(
            ADDRESS.withName("buf"),
            JAVA_LONG.withName("pts"),
            JAVA_LONG.withName("dts"),
            ADDRESS.withName("data"),
            JAVA_INT.withName("size"),
            JAVA_INT.withName("stream_index"),
            JAVA_INT.withName("flags"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("side_data"),
            JAVA_INT.withName("side_data_elems"),
            MemoryLayout.paddingLayout(4),
            JAVA_LONG.withName("duration"),
            JAVA_LONG.withName("pos"),
            ADDRESS.withName("opaque"),
            ADDRESS.withName("opaque_ref"),
            AVRational.withName("time_base")
    ).withName("AVPacket");
    public static final int AV_NUM_DATA_POINTERS = 8;

    public static final GroupLayout AVFrameLayout = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(AV_NUM_DATA_POINTERS, ADDRESS)
                    .withName("data"),
            MemoryLayout.sequenceLayout(AV_NUM_DATA_POINTERS, JAVA_INT)
                    .withName("linesize"),
            ADDRESS.withName("extended_data"),
            JAVA_INT.withName("width"),
            JAVA_INT.withName("height"),
            JAVA_INT.withName("nb_samples"),
            JAVA_INT.withName("format")
    ).withName("AVFrame");

    private final MethodHandle memcpy;

    public H264Dec(int width, int height) throws Throwable {
        super(width, height);
        arena = Arena.ofConfined();
        Linker linker = Linker.nativeLinker();
        var avcodec = SymbolLookup.libraryLookup("libavcodec.so", arena);
        var avutil = SymbolLookup.libraryLookup("libavutil.so", arena);

        alloc_context3 = linker.downcallHandle(
                avcodec.find("avcodec_alloc_context3").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.ADDRESS, // AVCodecContext *
                        ValueLayout.ADDRESS // const AVCodec *
                )
        );
        find_decoder = linker.downcallHandle(
                avcodec.find("avcodec_find_decoder").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.ADDRESS, // const AVCodec *
                        ValueLayout.JAVA_INT // enum AVCodecID
                )
        );
        new_packet = linker.downcallHandle(
                avcodec.find("av_new_packet").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, // int return
                        ValueLayout.ADDRESS, // AVPacket *pkt
                        ValueLayout.JAVA_INT // int size
                )
        );
        send_packet = linker.downcallHandle(
                avcodec.find("avcodec_send_packet").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS
                )
        );
        packet_unref = linker.downcallHandle(
                avcodec.find("av_packet_unref").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS
                )
        );
        receive_frame = linker.downcallHandle(
                avcodec.find("avcodec_receive_frame").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS
                )
        );
        frame_unref = linker.downcallHandle(
                avutil.find("av_frame_unref").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );
        frame_alloc = linker.downcallHandle(
                avutil.find("av_frame_alloc").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS)
        );
        packet_alloc = linker.downcallHandle(
                avcodec.find("av_packet_alloc").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS)
        );
        codec_open2 = linker.downcallHandle(
                avcodec.find("avcodec_open2").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // AVCodecContext *
                        ValueLayout.ADDRESS, // const AVCodec *
                        ValueLayout.ADDRESS // AVDictionary **
                )
        );
        frame_free = linker.downcallHandle(
                avutil.find("av_frame_free").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );
        packet_free = linker.downcallHandle(
                avcodec.find("av_packet_free").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );
        codec_free_context = linker.downcallHandle(
                avcodec.find("avcodec_free_context").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );
        Log.info("loaded software h264 decoder.");

        memcpy = linker.downcallHandle(
                avcodec.find("memcpy").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );
        Log.info("found memcpy");

        var codecId = AV_CODEC_ID_H264;
        codec = (MemorySegment) find_decoder.invokeExact(AV_CODEC_ID_H264);

        if (codec.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException(
                    "No decoder found for codec ID " + codecId);
        }
        MemorySegment lctx = (MemorySegment) alloc_context3.invokeExact(codec);
        if (lctx.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException(
                    "No context made for codec ID " + codecId);
        }
        ctx = lctx.reinterpret(AVCODECCONTEXTSIZE, arena, (s) -> {
            try {
                if (ctx != MemorySegment.NULL) {
                    codec_free_context.invokeExact(ctx);
                }
            } catch (Throwable t) {
            }
        });
        /*ctx.setAtIndex(JAVA_INT, AVCODECCONTEXTFLAGSOFFS, AV_CODEC_FLAG_LOW_DELAY);
        ctx.setAtIndex(JAVA_INT,AVCODECCONTEXTTHREADS , 4);*/

        /*
            d->ctx->flags |= AV_CODEC_FLAG_LOW_DELAY;

         */

        MemorySegment lpkt = (MemorySegment) packet_alloc.invokeExact();
        if (lpkt.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException(
                    "No packet made for codec ID " + codecId);
        }

        pkt = lpkt.reinterpret(AVPACKETSIZE, arena, (s) -> {
            try {
                packet_free.invokeExact(s);
            } catch (Throwable t) {
            }
        });

        MemorySegment lframe = (MemorySegment) frame_alloc.invokeExact();
        if (lframe.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException(
                    "No frame made found for codec ID " + codecId);
        }

        frame = lframe.reinterpret(AVFRAMESIZE, arena, (s) -> {
            try {
                frame_free.invokeExact(s);
            } catch (Throwable t) {
            }
        });

        int ret = (int) codec_open2.invokeExact(ctx, codec, MemorySegment.NULL);
        if (ret != 0) {
            throw new IllegalArgumentException(
                    "No codec init for codec ID " + codecId);
        }
        Log.info("inited software h264 decoder.");

    }

    @Override
    public int decompress(ByteBuffer encodedFrame, ByteBuffer out) throws Throwable {

        long insize = encodedFrame.remaining();
        int ret = (int) new_packet.invokeExact(pkt, (int) insize);
        if (ret < 0) {
            return ret;
        }

        MemorySegment pktdata = pkt.get(
                ValueLayout.ADDRESS,
                AVPacketLayout.byteOffset(groupElement("data"))
        );
        pktdata = pktdata.reinterpret(insize, arena, (s) -> {
            try {
                frame_unref.invokeExact(frame);
            } catch (Throwable t) {
            }
        });

        //memcpy(d->pkt->data, data, size);
        // MemorySegment e = (MemorySegment) memcpy.invokeExact(pktdata,MemorySegment.ofBuffer(encodedFrame),insize);
        //aaagh...
        // Copy bytes into FFmpeg-owned packet storage.
        MemorySegment.copy(
                MemorySegment.ofBuffer(encodedFrame),
                0,
                pktdata,
                0,
                insize
        );

        ret = (int) send_packet.invokeExact(ctx, pkt);
        packet_unref.invokeExact(pkt);
        if (ret < 0) {
            return ret;
        }

        ret = (int) receive_frame.invokeExact(ctx, frame);
        int format = (int) frame.get(ValueLayout.JAVA_INT,
                AVFrameLayout.byteOffset(groupElement("format"))
        );
        int w = (int) frame.get(ValueLayout.JAVA_INT,
                AVFrameLayout.byteOffset(groupElement("width"))
        );
        int h = (int) frame.get(ValueLayout.JAVA_INT,
                AVFrameLayout.byteOffset(groupElement("height"))
        );
        Log.debug("We have a frame of type " + format);
// now copy it out..

        if ((w != width) || (h != height)) {
            Log.warn("Unexpected framesize = " + w + "x" + h);
        }
        if (ret < 0) {
            return ret;
        }
        int planes = 3;
        int ysz = w * h;
        int usz = 0;
        int vsz = 0;
        int wb = w;
        int hny = h;

        switch (format) {
            case 0 -> {
                //i420
                vsz = usz = ysz / 4;
                wb = w / 2;
                hny = h/2;
            }
            case 1 -> {
                //i422
                vsz = usz = ysz / 2;
                wb = w / 2;
                hny = h;
            }
            default -> {
                Log.error("Unknown pix format");
                planes = 0;
            }
        }
        long dao = AVFrameLayout.byteOffset(groupElement("data"));
        long lao = AVFrameLayout.byteOffset(groupElement("linesize"));
        out.position(0);

        for (int p = 0; p < planes; p++) {
            Log.debug("copy plane " + p);
            copyFramePlane(dao, lao, p, p == 0 ? w : wb, p==0 ? h:hny, out);
        }
       
        out.flip();
      
        return format;
    }

    void copyFramePlane(
            long dataArrayOffset,
            long linesizeArrayOffset,
            int plane,
            int widthBytes,
            int height,
            ByteBuffer destination
    ) {
        if (plane < 0 || plane >= 8) {
            throw new IllegalArgumentException("Invalid plane index");
        }
        if (widthBytes < 0 || height < 0) {
            throw new IllegalArgumentException("Invalid dimensions");
        }

        long required = (long) widthBytes * height;
        if (required > destination.remaining()) {
            throw new IllegalArgumentException("Destination buffer too small");
        }

        // AVFrame.data is uint8_t *data[8].
        long doffset = AVFrameLayout.byteOffset(
                groupElement("data"),
                sequenceElement(plane)
        );

        MemorySegment data = frame.get(ADDRESS, doffset);
        data = data.reinterpret(required, arena, (s) -> {
        });
        // AVFrame.linesize is int linesize[8].

        ByteBuffer bbin = data.asByteBuffer();
        long loffset = AVFrameLayout.byteOffset(
                groupElement("linesize"),
                sequenceElement(plane)
        );

        int stride = frame.get(JAVA_INT, loffset);

        if (data.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("Frame plane is null");
        }
        if (Math.abs((long) stride) < widthBytes) {
            throw new IllegalArgumentException("Invalid frame stride " + stride + " vs " + widthBytes);
        }

        int boffs = destination.position();

        for (int y = 0; y < height; y++) {
            // Negative linesize is possible for vertically inverted images.
            long rowOffset = (long) y * stride;
            destination.put(boffs, bbin, (int) rowOffset, widthBytes);
            boffs += widthBytes;
        }
        destination.position(boffs);
    }

    public static void main(String[] args) {
        //1280x720
        Log.setLevel(Log.ALL);
        try {
            var h = Paths.get("1.h264");
            var bmp = Paths.get("1.yuv");
            OpenOption[] options = {StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE};
            var decoder = new H264Dec(1280, 720);
            var outfile = Files.newOutputStream(bmp, options);

            var buf = Files.readAllBytes(h);
            ByteBuffer in = ByteBuffer.wrap(buf);
            int len = buf.length;
            int outlen = (1280 * 720 * 2);
            ByteBuffer out = ByteBuffer.allocate(outlen);
            for (int i = 0; i < 100; i++) {
                long then = System.currentTimeMillis();
                in.position(0);
                in.limit(len);
                out.position(0);
                decoder.decompress(in, out);
                System.out.println("took " + (System.currentTimeMillis() - then) + " ms");
            }
            outfile.write(out.array());
        } catch (Throwable x) {
            x.printStackTrace();
        }
    }

    @Override
    public void close() throws Exception {

    }

}
