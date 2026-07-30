/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package pe.pi.v4l2reader;

import java.util.HashMap;

/**
 *
 * @author thp
 */
public interface ControlMapper {
        public HashMap<String, V4l2ExtControl> getMapOfControls();
}
