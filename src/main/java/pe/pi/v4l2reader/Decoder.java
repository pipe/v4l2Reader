/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package pe.pi.v4l2reader;

import java.nio.ByteBuffer;

/**
 *
 * @author thp
 */
public abstract class Decoder {

    protected final int width;
    protected final int height;

    public Decoder(int width, int height) throws Throwable {
        this.width = width;
        this.height = height;
    }
    abstract public int decompress(ByteBuffer encodedFrame, ByteBuffer out) throws Throwable ;

    /**
     * remap the i422 data from to nv12
     *
     * @param i422 input buffer - 3 planes Y U and V U and v are vertically
     * sub-sampled. From camera or jpeg decode
     * @param nv12 output buffer - 2 planes Y and UV where UV are vertically
     * sub-sampled again. suitable for Nv12 encode in h264
     * @param width original image width from camera or jpeg
     * @param height original image height from camera or jpeg
     */
    public static void i422ToNV12(
            ByteBuffer i422,
            ByteBuffer nv12,
            int width,
            int height) {
        int ySize = width * height;
        int uvSize = ySize / 2; // input subsample

        // Copy Y plane as is
        nv12.clear();
        nv12.put(0, i422, 0, ySize);

        int uoffset = ySize; // input u plane start
        int voffset = ySize + uvSize; // input v plane start
        nv12.position(ySize); // output uv plane start
        byte rowu[] = new byte[width]; // hold u values to be subsampled
        byte rowv[] = new byte[width];// hold v values to be subsampled
        int i = 0;
        for (int r = 0; r < height / 2; r++) { // iterate over the input rows
            for (int c = 0; c < width; c++) { // iterate over the input columns
                if (r % 2 == 0) {
                    // collect the data in an even row and stash it
                    rowu[c] = i422.get(uoffset + i);
                    rowv[c] = i422.get(voffset + i);
                    i++;
                } else {
                    // collect the data in an odd row
                    byte u = i422.get(uoffset + i);
                    byte v = i422.get(voffset + i);
                    i++;
                    // then average it with the stash
                    int umean = ((0xff & u) + (0xff & rowu[c])) / 2;
                    int vmean = ((0xff & v) + (0xff & rowv[c])) / 2;
                    // and write it out
                    nv12.put((byte) umean);
                    nv12.put((byte) vmean);
                }
            }
        }
        i422.clear();
        nv12.flip();
    }

    public static void i420ToNV12(
            ByteBuffer i420,
            ByteBuffer nv12,
            int width,
            int height) {
        int ySize = width * height;
        int uvSize = ySize / 4; // input subsample

        // Copy Y plane as is
        nv12.clear();
        nv12.put(0, i420, 0, ySize);

        int uoffset = ySize; // input u plane start
        int voffset = ySize + uvSize; // input v plane start
        nv12.position(ySize); // output uv plane start
        for (int i = 0; i < uvSize; i++) {
            byte u = i420.get(uoffset + i);
            byte v = i420.get(voffset + i);
            nv12.put(u);
            nv12.put(v);
        }
        i420.clear();
        nv12.flip();
    }
}
