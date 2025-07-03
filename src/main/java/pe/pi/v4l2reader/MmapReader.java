/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package pe.pi.v4l2reader;

import java.nio.ByteBuffer;

/**
 *
 * @author thp
 */
public interface MmapReader {

    public void startCap() throws Throwable ;

    public ByteBuffer read() throws Throwable;

    public void stop() throws Throwable;
    
}
