/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.iesapp.modules.mensajesup;

import java.awt.Component;
import java.io.File;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.filechooser.FileSystemView;
import org.iesapp.cloudws.client.FileInfo;

/**
 *
 * @author Josep
 */
public class MyListRenderer implements ListCellRenderer {

   private static FileSystemView fsv = FileSystemView.getFileSystemView();
   
    public MyListRenderer() {
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        
        String txt = "";
        Icon icon = null;
        
        if (value instanceof File) {
            File fi = (File) value;
            String onlyname = fi.getName();
            txt = onlyname + " (" + FileInfo.convertFileSize(fi.length()) + ")";
            icon = fsv.getSystemIcon(fi);
        }

        
        JLabel label = new JLabel(txt);
        label.setOpaque(true);
        if(isSelected)
        {
            label.setBackground(list.getSelectionBackground());
        }
        label.setIcon(icon);
       
        return label;
    }
    
}
