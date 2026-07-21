/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package pe.pi.v4l2reader.jpegdecode;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;

/**
 *
 * @author thp
 */
public class NewMain {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            var peg = Paths.get("100.jpeg");
            var bmp = Paths.get("100.jpg");
            OpenOption[] options = {StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE};

            var outfile = Files.newOutputStream(bmp, options);

            var buf = Files.readAllBytes(peg);
            BufferedImage ii = null;

            for (int i = 0; i < 100; i++) {
                var bin = new ByteArrayInputStream(buf);
                long then = System.currentTimeMillis();
                ii = ImageIO.read(bin);
                System.out.println("took " + (System.currentTimeMillis() - then) + " ms");
            }
            ImageIO.write(ii, "bmp", outfile);
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

}
