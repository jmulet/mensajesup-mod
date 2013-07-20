/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.iesapp.modules.mensajesup;

import com.l2fprod.common.swing.StatusBar;
import java.awt.CardLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import javax.swing.Timer;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import org.iesapp.clients.sgd7.mensajes.BeanInbox;
import org.iesapp.clients.sgd7.mensajes.BeanMensajesAttachment;
import org.iesapp.clients.sgd7.mensajes.BeanOutbox;
import org.iesapp.clients.sgd7.mensajes.BeanProfSms;
import org.iesapp.clients.sgd7.mensajes.MensajesListas;
import org.iesapp.clients.sgd7.mensajes.MensajesListasProfesores;
import org.iesapp.clients.sgd7.profesores.Profesores;
import org.iesapp.clients.sgd7.tareas.BeanTareas;
import org.iesapp.cloudws.client.CloudClientSession;
import org.iesapp.cloudws.client.FileInfo;
import org.iesapp.framework.pluggable.grantsystem.GrantBean;
import org.iesapp.framework.pluggable.grantsystem.GrantModule;
import org.iesapp.framework.pluggable.grantsystem.GrantSystem;
import org.iesapp.framework.table.CellDateRenderer;
import org.iesapp.framework.table.CellTableState;
import org.iesapp.framework.table.MyCheckBoxRenderer;
import org.iesapp.framework.table.MyIconButtonRenderer;
import org.iesapp.framework.table.MyIconLabelRenderer;
import org.iesapp.framework.table.TextAreaRenderer;
import org.iesapp.framework.util.CoreCfg;
import org.iesapp.util.DataCtrl;

/**
 *
 * @author Josep
 */
public class MensajesUPModule extends org.iesapp.framework.pluggable.TopModuleWindow {
    private DefaultTableModel modelTable1;
    private DefaultTableModel modelTable2;
    private DefaultTableModel modelTable3;
    private DefaultTableModel modelTable4;
    private int idProfesores;
    private ArrayList<BeanInbox> inbox;
    private ArrayList<BeanOutbox> outbox;
    private ArrayList<BeanInbox> trash;
    private ArrayList<BeanProfSms> listProf;
    private boolean listening=false;
    private ArrayList<String> equipDocent;
    private ArrayList<BeanTareas> listTareas;
    private int maxlength;
    private ArrayList<MensajesListas> mensajesListas;
    private GrantModule grantModule;
    private GrantModule grantModuleDefaults;
    private CloudClientSession session;
    private File localCurrentDir;
    private DefaultListModel modelList1;
    private Timer timer;
    
    private boolean isInboxLoading;
    private boolean isSentLoading;
    private boolean isTrashLoading;
    private final MensajesPanel mp1;
    private final MensajesPanel mp2;


    /**
     * Creates new form MensajesUPModule
     */
    public MensajesUPModule() {
        
        this.moduleDescription = "Aquest mòdul permet gestionar els missatges a PDAs";
        this.moduleDisplayName = "Missatges a PDAs";
        this.moduleName = "MensajesUP";
        initComponents();
        jButton1.setIcon(new ImageIcon(MensajesUPModule.class.getResource("/org/iesapp/framework/icons/envia.gif")));
        jTable1.getTableHeader().setReorderingAllowed(false);
        jTable2.getTableHeader().setReorderingAllowed(false);
        jTable3.getTableHeader().setReorderingAllowed(false);
        
        mp1 = new MensajesPanel();
        mp1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                CardLayout layout = (CardLayout) jPanel1.getLayout();
                layout.first(jPanel1);
            }
        });
        mp2 = new MensajesPanel();
        mp2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                CardLayout layout = (CardLayout) jPanel2.getLayout();
                layout.first(jPanel2);
            }
        });
        jPanel1.add(mp1, "mp1");
        jPanel2.add(mp2, "mp2");      
    
    }
    
    /**
     *
     */
    @Override
    public void postInitialize()
    {
        
        grantModuleDefaults = new GrantModule(moduleName, coreCfg);
        grantModuleDefaults.register("listEdit", "Permet l'edició de llistes de missatges", GrantBean.NONE, GrantBean.BASIC_CONFIG);
        
      
        int inboxRefresh = 60000;
        if(beanModule.getIniParameters().containsKey("inboxRefreshTime"))
        {
            inboxRefresh = ((Number) beanModule.getIniParameters().get("inboxRefreshTime")).intValue();
        }
        
        //Periodically check for new inbox messages
        timer = new Timer(inboxRefresh, new ActionListener ()
        {
            @Override
                public void actionPerformed(ActionEvent e)
                {
                    new InboxLoader().execute();
                }
         });
        timer.start();
        modelList1 = new DefaultListModel();
        jList1.setModel(modelList1);
        jList1.setCellRenderer(new MyListRenderer());
    
        //Crea el model per a la taula de professorat
        listProf = coreCfg.getSgdClient().getProfesoresCollection().listProf();
        fillProfesList();
        
       
        modelTable4.addTableModelListener(new TableModelListener(){

            @Override
            public void tableChanged(TableModelEvent e) {
                 int row = jTable4.getSelectedRow();
                 if(row<0 || !listening)
                 {
                     return;
                 }
                 String idProfe = (String) jTable4.getValueAt(row, 0);
                 Boolean selected = (Boolean) jTable4.getValueAt(row, 1);
                 for(BeanProfSms bean: listProf)
                 {
                     if(bean.getCodigo().equals(idProfe))
                     {
                         bean.setSelected(selected);
                         break;
                     }
                     
                 }
            }
        });
        
        //Carrega les tasques 
        listTareas = coreCfg.getSgdClient().getTareasCollection().getListTareas();
        DefaultComboBoxModel modelCombo1 = new DefaultComboBoxModel();
        modelCombo1.addElement("Triau una tasca");
        for(BeanTareas b: listTareas)
        {
            modelCombo1.addElement(b.getNombre());
        }
        jComboBox1.setModel(modelCombo1);
        
        DefaultComboBoxModel modelCombo2 = new DefaultComboBoxModel();
        modelCombo2.addElement("Triau una llista");
        mensajesListas = coreCfg.getSgdClient().getMensajesCollection().getMensajesListas();
        for(MensajesListas b: mensajesListas)
        {
            modelCombo2.addElement(b.getNombre());
        }
        jComboBox2.setModel(modelCombo2);
        
        idProfesores = coreCfg.getUserInfo().getIdSGD();
        int pendents = coreCfg.getSgdClient().getMensajesCollection().getSmsNoLeidos(idProfesores+"");
       // System.out.println("SMS no leidos : "+pendents);
        this.openingRequired = pendents>0;
        
       
    }

    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        localToolbar1 = new javax.swing.JToolBar();
        jNewMail = new javax.swing.JButton();
        jMensajesList = new javax.swing.JButton();
        jOutlookBar1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable(){
            public boolean isCellEditable(int row, int col)
            {
                return false;
            }
        };
        jPanel2 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTable2 = new javax.swing.JTable(){
            public boolean isCellEditable(int row, int col)
            {
                return false;
            }
        };
        jPanel3 = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        jTable3 = new javax.swing.JTable(){
            public boolean isCellEditable(int row, int col)
            {
                return false;
            }
        };
        jPanel4 = new javax.swing.JPanel();
        jScrollPane4 = new javax.swing.JScrollPane();
        jTable4 = new javax.swing.JTable(){
            public boolean isCellEditable(int row, int col)
            {
                return (col==1);
            }
        };
        jPanel5 = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        jCheckBox1 = new javax.swing.JCheckBox();
        jCheckBox2 = new javax.swing.JCheckBox();
        jLabel2 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jComboBox1 = new javax.swing.JComboBox();
        jLabel6 = new javax.swing.JLabel();
        jComboBox2 = new javax.swing.JComboBox();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        hTMLEditorPane1 = new net.atlanticbb.tantlinger.shef.HTMLEditorPane();
        jPanel6 = new javax.swing.JPanel();
        jList1 = new javax.swing.JList();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();

        localToolbar1.setFloatable(false);

        jNewMail.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/iesapp/modules/mensajesup/icons/newmail.png"))); // NOI18N
        jNewMail.setToolTipText("Nou missatge");
        jNewMail.setFocusable(false);
        jNewMail.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jNewMail.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jNewMail.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jNewMailActionPerformed(evt);
            }
        });
        localToolbar1.add(jNewMail);

        jMensajesList.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/iesapp/modules/mensajesup/icons/list.png"))); // NOI18N
        jMensajesList.setToolTipText("Edita llistes de destinaris");
        jMensajesList.setFocusable(false);
        jMensajesList.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jMensajesList.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jMensajesList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMensajesListActionPerformed(evt);
            }
        });
        localToolbar1.add(jMensajesList);

        getContentContainer().setLayout(new java.awt.BorderLayout());

        jPanel1.setLayout(new java.awt.CardLayout());

        modelTable1 = new javax.swing.table.DefaultTableModel(
            new Object [][] {
            },
            new String [] {
                "Esborrar", "Llegit", "Data", "Remitent", "Missatge"
            }
        );
        jTable1.setAutoCreateRowSorter(true);
        jTable1.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        jTable1.setModel(modelTable1);
        jTable1.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        jTable1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jTable1MouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(jTable1);
        jTable1.setRowHeight(50);

        String[] icons = new String[]{"/org/iesapp/framework/icons/delete.gif",
            "/org/iesapp/modules/mensajesup/icons/clip.gif",
            "/org/iesapp/framework/icons/blank.gif"};

        jTable1.getColumnModel().getColumn(4).setCellRenderer(new MyIconLabelRenderer(icons));
        jTable1.getColumnModel().getColumn(0).setCellRenderer(new MyIconButtonRenderer(icons));
        jTable1.getColumnModel().getColumn(1).setCellRenderer(new MyCheckBoxRenderer());
        jTable1.getColumnModel().getColumn(2).setCellRenderer(new CellDateRenderer());
        jTable1.getColumnModel().getColumn(0).setPreferredWidth(60);
        jTable1.getColumnModel().getColumn(1).setPreferredWidth(60);
        jTable1.getColumnModel().getColumn(2).setPreferredWidth(120);
        jTable1.getColumnModel().getColumn(3).setPreferredWidth(120);
        jTable1.getColumnModel().getColumn(4).setPreferredWidth(420);
        jTable1.setAutoCreateRowSorter(true);

        jPanel1.add(jScrollPane1, "card2");

        jOutlookBar1.addTab("Missatges rebuts", new javax.swing.ImageIcon(getClass().getResource("/org/iesapp/modules/mensajesup/icons/inbox.png")), jPanel1); // NOI18N

        jPanel2.setLayout(new java.awt.CardLayout());

        modelTable2 = new javax.swing.table.DefaultTableModel(
            new Object [][] {
            },
            new String [] {
                "Data", "Destinataris", "Missatge"
            }
        );
        jTable2.setAutoCreateRowSorter(true);
        jTable2.setModel(modelTable2);
        jTable2.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jTable2MouseClicked(evt);
            }
        });
        jScrollPane2.setViewportView(jTable2);
        jTable2.setRowHeight(32);

        jTable2.getColumnModel().getColumn(2).setCellRenderer(new MyIconLabelRenderer(icons));
        jTable2.getColumnModel().getColumn(0).setCellRenderer(new CellDateRenderer());
        jTable2.getColumnModel().getColumn(1).setCellRenderer(new TextAreaRenderer());
        jTable2.getColumnModel().getColumn(0).setPreferredWidth(60);
        jTable2.getColumnModel().getColumn(1).setPreferredWidth(300);
        jTable2.getColumnModel().getColumn(2).setPreferredWidth(300);

        jTable2.setAutoCreateRowSorter(true);

        jPanel2.add(jScrollPane2, "card2");

        jOutlookBar1.addTab("Missatges enviats", new javax.swing.ImageIcon(getClass().getResource("/org/iesapp/modules/mensajesup/icons/outboux.png")), jPanel2); // NOI18N

        jPanel3.setLayout(new java.awt.BorderLayout());

        modelTable3 = new javax.swing.table.DefaultTableModel(
            new Object [][] {
            },
            new String [] {
                "Restaura", "Data", "Remitent", "Missatge"
            }
        );
        jTable3.setAutoCreateRowSorter(true);
        jTable3.setModel(modelTable3);
        jTable3.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jTable3MouseClicked(evt);
            }
        });
        jScrollPane3.setViewportView(jTable3);
        jTable3.setRowHeight(32);

        String[] icons2 = new String[]{"/org/iesapp/framework/icons/back.gif",
            "/org/iesapp/modules/mensajesup/icons/clip.gif",
            "/org/iesapp/framework/icons/blank.gif"};

        jTable3.getColumnModel().getColumn(3).setCellRenderer(new MyIconLabelRenderer(icons2));
        jTable3.getColumnModel().getColumn(0).setCellRenderer(new MyIconButtonRenderer(icons2));
        jTable3.getColumnModel().getColumn(1).setCellRenderer(new CellDateRenderer());
        jTable3.getColumnModel().getColumn(0).setPreferredWidth(60);
        jTable3.getColumnModel().getColumn(1).setPreferredWidth(60);
        jTable3.getColumnModel().getColumn(2).setPreferredWidth(120);
        jTable3.getColumnModel().getColumn(3).setPreferredWidth(420);

        jTable3.setAutoCreateRowSorter(true);

        jPanel3.add(jScrollPane3, java.awt.BorderLayout.CENTER);

        jOutlookBar1.addTab("Missatges esborrats", new javax.swing.ImageIcon(getClass().getResource("/org/iesapp/modules/mensajesup/icons/trash.png")), jPanel3); // NOI18N

        jPanel4.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N

        modelTable4 = new javax.swing.table.DefaultTableModel(
            new Object [][] {
            },
            new String [] {
                "id", "Tria", "Professor"
            }
        );
        jTable4.setModel(modelTable4);
        jTable4.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
        jTable4.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jTable4MouseClicked(evt);
            }
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                jTable4MouseEntered(evt);
            }
        });
        jScrollPane4.setViewportView(jTable4);
        jTable4.setRowHeight(32);
        JCheckBox checkbox = new JCheckBox();
        jTable4.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(checkbox));
        jTable4.getColumnModel().getColumn(1).setCellRenderer(new MyCheckBoxRenderer());
        jTable4.getColumnModel().getColumn(0).setPreferredWidth(50);
        jTable4.getColumnModel().getColumn(1).setPreferredWidth(50);
        jTable4.getColumnModel().getColumn(2).setPreferredWidth(350);

        jButton1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/iesapp/modules/mensajesup/icons/newmail.png"))); // NOI18N
        jButton1.setText("Envia'l");
        jButton1.setEnabled(false);
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jPanel5.add(jButton1);

        jCheckBox1.setText("Tots");
        jCheckBox1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBox1ActionPerformed(evt);
            }
        });

        jCheckBox2.setText("Equip docent");
        jCheckBox2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBox2ActionPerformed(evt);
            }
        });

        jLabel2.setText(" Cerca");

        jTextField1.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                jTextField1KeyReleased(evt);
            }
        });

        jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        jLabel3.setText(" ");

        jLabel4.setText("Per tasques");

        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        jComboBox1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox1ActionPerformed(evt);
            }
        });

        jLabel6.setText("Per llistes");

        jComboBox2.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        jComboBox2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox2ActionPerformed(evt);
            }
        });

        jTabbedPane1.addTab("Cos del missatge", hTMLEditorPane1);

        jList1.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jList1.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                jList1KeyReleased(evt);
            }
        });

        jButton2.setText("- Treu");
        jButton2.setToolTipText("Eliminar fitxer adjunt");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        jButton3.setText("+ Afegeix fitxer adjunt");
        jButton3.setToolTipText("Eliminar fitxer adjunt");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGap(1, 1, 1)
                .addComponent(jButton3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton2)
                .addGap(378, 378, 378))
            .addComponent(jList1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton2)
                    .addComponent(jButton3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jList1, javax.swing.GroupLayout.DEFAULT_SIZE, 129, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Fitxers adjunts", jPanel6);

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jCheckBox1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jCheckBox2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboBox1, 0, 113, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel6)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboBox2, 0, 113, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 104, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField1))
                    .addComponent(jTabbedPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(3, 3, 3)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 104, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jCheckBox2)
                    .addComponent(jCheckBox1)
                    .addComponent(jLabel3)
                    .addComponent(jLabel4)
                    .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6)
                    .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        jTabbedPane1.getAccessibleContext().setAccessibleName("jPanel4");

        jOutlookBar1.addTab("Nou missatge", new javax.swing.ImageIcon(getClass().getResource("/org/iesapp/modules/mensajesup/icons/newmail.png")), jPanel4); // NOI18N

        getContentContainer().add(jOutlookBar1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    //Send
    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        
        ArrayList<BeanMensajesAttachment> attachments = new ArrayList<BeanMensajesAttachment>();
        //upload Attachments
        if(modelList1.getSize()>0 && (getSession()==null || getSession().ping()!=200))
        {
            JOptionPane.showMessageDialog(this, "Ho lamentam però el servidor\nde fitxers no respon.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        for(int i=0; i<modelList1.getSize(); i++)
        {
            File file = (File) modelList1.get(i);
            HashMap uploadFile = getSession().uploadFile(new File(file.getAbsolutePath()), getSession().getHome()+File.separator+".attachments"+File.separator);
            if(!uploadFile.get("upload.status").equals("200"))
            {
                    //Show reasons why upload failed
                    String message = uploadFile.get("upload.result")+"\n"+"Virus scan: "+uploadFile.get("clamscan.result")+
                            "\n"+uploadFile.get("clamscan.status");
                    JOptionPane.showMessageDialog(this, message,"Error: Upload failed", JOptionPane.WARNING_MESSAGE);
                    return;
            }
            else if(!uploadFile.containsKey("upload.fileinfo"))
            {
                JOptionPane.showMessageDialog(this, "A problem has occurred while uploading attachments","Error: Upload failed", JOptionPane.WARNING_MESSAGE);
                return;
            }
            FileInfo fi = (FileInfo) uploadFile.get("upload.fileinfo");        
            BeanMensajesAttachment attach =
                    new BeanMensajesAttachment( (org.iesapp.clients.sgd7.IClient) coreCfg.getSgdClient());
            attach.setAttachment(fi.getName());
            attach.setSize(fi.getSize());
            //Leave idMensajes empty, it will be populated latter;
            attachments.add(attach);
        }
        
        String rich = hTMLEditorPane1.getText().trim();
        String plain = rich;
        Html2Text html2Text = new Html2Text();
        try {
            html2Text.parse(new StringReader(rich));
            plain  = html2Text.getText().trim();
        } catch (IOException ex) {
            Logger.getLogger(MensajesUPModule.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
        coreCfg.getSgdClient().getMensajesCollection().sendSms(idProfesores+"", listProf, plain, rich, attachments);
        
       
        //update enviats
        new SentLoader().execute();
        //update rebuts as well ( I may sent to myself )
        new InboxLoader().execute();
        
        new AnimatedPanel(true, 1500, 0).setVisible(true);
        //Clear selection 
        for(BeanProfSms bean: listProf)
        {
            bean.setSelected(false);
        }
        jTextField1.setText("");
        fillProfesList();
        hTMLEditorPane1.setText("");
        jButton1.setEnabled(false);
        jLabel3.setText("");
        
        modelList1.removeAllElements();
        
        //And move view to inbox
        jOutlookBar1.setSelectedIndex(0);
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jTable1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTable1MouseClicked
        int row = jTable1.getSelectedRow();
        int col = jTable1.getSelectedColumn();
        if(row<0)
        {
            return;
        }
        int idMessage = ((CellTableState) jTable1.getValueAt(row, 0)).getCode();
            
        if(col==0)
        {
            //Move this message to trash
            coreCfg.getSgdClient().getMensajesCollection().setBorradoUpProfe(idMessage);
            for(BeanInbox bean: inbox)
            {
                if(bean.getId()==idMessage)
                {
                    inbox.remove(bean);
                    modelTable1.removeRow(row);
                    new TrashLoader().execute();
                    break;
                }
            }
        }
        else
        {
            if(evt.getClickCount()==2)
            {
                
                //Set leido
                boolean leido = (Boolean) jTable1.getValueAt(row, 1);
                if(!leido) 
                {
                    coreCfg.getSgdClient().getMensajesCollection().setLeido(idMessage);
                    jTable1.setValueAt(true, row, 1);
                }
                
                //Display message
                String info = "Missatge escrit per "+inbox.get(row).getRemitente()+"  dia "+ new DataCtrl(inbox.get(row).getFechaEnviado()).getDiaMesComplet();
                
                if(inbox.get(row).getRichText()==null)
                {
                    String richText = coreCfg.getSgdClient().getMensajesCollection().loadRichText(inbox.get(row).getTexto(), 
                        inbox.get(row).getIdMensaje());
                    inbox.get(row).setRichText(richText);
                }
                
                mp1.setData(inbox.get(row).getRichText(), info,
                        inbox.get(row).getAttachments(), getSession());
                ((CardLayout) jPanel1.getLayout()).last(jPanel1);
              
                
               
                
            }
        }
    }//GEN-LAST:event_jTable1MouseClicked

    private void jTable3MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTable3MouseClicked
        int row = jTable3.getSelectedRow();
        int col = jTable3.getSelectedColumn();
        if(row<0 || col>0)
        {
            return;
        }
        //Move this message to rebuts
        int idMessage = ((CellTableState) jTable3.getValueAt(row, 0)).getCode();
        coreCfg.getSgdClient().getMensajesCollection().removeBorradoUpProfe(idMessage);
        for(BeanInbox bean: trash)
        {
            if(bean.getId()==idMessage)
            {
                trash.remove(bean);
                modelTable3.removeRow(row);
                new InboxLoader().execute();
                break;
            }
        }
    }//GEN-LAST:event_jTable3MouseClicked

    private void jTable4MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTable4MouseClicked
        int n = 0;
        //Actualitza el n. de seleccionats
        for(BeanProfSms bean: listProf)
        {
            if(bean.isSelected())
            {
                n +=1;
            }
        }
        jLabel3.setText(n+" seleccionats");
        jButton1.setEnabled(n>0 && !hTMLEditorPane1.getText().trim().isEmpty());
    }//GEN-LAST:event_jTable4MouseClicked

    private void jTextField1KeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTextField1KeyReleased
         fillProfesList();
    }//GEN-LAST:event_jTextField1KeyReleased

    private void jTable4MouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTable4MouseEntered
         // TODO add your handling code here:
    }//GEN-LAST:event_jTable4MouseEntered

    private void jCheckBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBox1ActionPerformed
        boolean sel = jCheckBox1.isSelected();
        int n = 0;
        for(BeanProfSms bean: listProf)
        {
            bean.setSelected(sel);
            if(sel)
            {
                n+=1;
            }
        }
        jTextField1.setText("");
        fillProfesList();
        jLabel3.setText(n+" seleccionats");
        jButton1.setEnabled(n>0 && !hTMLEditorPane1.getText().trim().isEmpty());
    }//GEN-LAST:event_jCheckBox1ActionPerformed

    private void jCheckBox2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBox2ActionPerformed
         //Necessita saber quin es l'equip docent no de l'alumne, si no,
         //del grup que n'es tutor aquest professor.
         if(equipDocent==null)
         {
             generateEquipDocent();
         }
         
         int n = 0;
         boolean sel = jCheckBox2.isSelected();
         for(BeanProfSms bean: listProf)
         {
             if(equipDocent.contains((bean.getCodigo())))
             {
                 bean.setSelected(sel);
             }
             if(bean.isSelected())
             {
                 n+=1;
             }
         }
         fillProfesList();
         jLabel3.setText(n+" seleccionats");
             
        
        
    }//GEN-LAST:event_jCheckBox2ActionPerformed

    private void jComboBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox1ActionPerformed
         int row = jComboBox1.getSelectedIndex();
         int n = 0;
         if(row>0)
         {
            ArrayList<Integer> listProfesores = coreCfg.getSgdClient().getTareasCollection().getListProfesores(listTareas.get(row-1).getCodigo());
            for(BeanProfSms b: listProf)
            {
                int idProf = Integer.parseInt(b.getCodigo());
                if(listProfesores.contains(idProf))
                {
                    b.setSelected(true);
                    n+=1;
                }
                else
                {
                    b.setSelected(false);
                }
            }
         }
         else
         {
              for(BeanProfSms b: listProf)
              {
                b.setSelected(false);                
             }
         }
         
        jLabel3.setText(n+" seleccionats");
        jButton1.setEnabled(n>0 && !hTMLEditorPane1.getText().trim().isEmpty());
        //Refresca la llista
        fillProfesList();
    }//GEN-LAST:event_jComboBox1ActionPerformed

    private void jComboBox2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox2ActionPerformed
        int row = jComboBox2.getSelectedIndex();
         int n = 0;
         if(row>0)
         {
            ArrayList<MensajesListasProfesores> listProfesores = mensajesListas.get(row-1).getListMensajesListasProfesores();
            
            for(BeanProfSms b: listProf)
            {
                //int idProf = Integer.parseInt(b.getCodigo());
                boolean contains = false;
                
                for(MensajesListasProfesores mlp: listProfesores)
                {
                    if(mlp.getCodigo().equals(b.getCodigo()))
                    {
                        contains = true;
                        break;
                    }
                }
                
                if(contains)
                {
                    b.setSelected(true);
                    n+=1;
                }
                else
                {
                    b.setSelected(false);
                }
            }
         }
         else
         {
              for(BeanProfSms b: listProf)
              {
                b.setSelected(false);                
             }
         }
         
        jLabel3.setText(n+" seleccionats");
        jButton1.setEnabled(n>0 && !hTMLEditorPane1.getText().trim().isEmpty());
        //Refresca la llista
        fillProfesList();
        
    }//GEN-LAST:event_jComboBox2ActionPerformed

    private void jMensajesListActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMensajesListActionPerformed
        //Opens a dialog for editing lists
        Agenda agenda = new Agenda(null,true,coreCfg);
        agenda.setLocationRelativeTo(null);
        agenda.setVisible(true);
    }//GEN-LAST:event_jMensajesListActionPerformed

    private void jNewMailActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jNewMailActionPerformed
        jOutlookBar1.setSelectedIndex(3);
    }//GEN-LAST:event_jNewMailActionPerformed

    
    private void jTable2MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTable2MouseClicked
        int row = jTable2.getSelectedRow();
        if(evt.getClickCount()==2 && row>=0)
        {
             //Display message
               String info = "Missatge redactat dia "+ new DataCtrl(outbox.get(row).getFechaEnviado()).getDiaMesComplet()+
                        " per a "+outbox.get(row).getDestinatariosCount()+" destinataris";
                       
                
                if(outbox.get(row).getRichText()==null)
                {
                    String richText = coreCfg.getSgdClient().getMensajesCollection().loadRichText(outbox.get(row).getTexto(), 
                        outbox.get(row).getId());
                    outbox.get(row).setRichText(richText);
                }
               
                mp2.setData(outbox.get(row).getRichText(), info,
                        outbox.get(row).getAttachments(), getSession());
                
               ((CardLayout) jPanel2.getLayout() ).last(jPanel2);
      }
   
        
    }//GEN-LAST:event_jTable2MouseClicked

    private void jList1KeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jList1KeyReleased
        if(evt.getKeyCode()== KeyEvent.VK_DELETE)
        {
            int row = jList1.getSelectedIndex();
            if(row>=0)
            {
                modelList1.remove(row);
            }
        }
    }//GEN-LAST:event_jList1KeyReleased

    private void addNewAttachment()
    {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int showOpenDialog = fc.showOpenDialog(this);
        if(showOpenDialog == JFileChooser.APPROVE_OPTION)
        {
            File selectedFile = fc.getSelectedFile();
            modelList1.addElement(selectedFile);
            this.localCurrentDir = selectedFile.getParentFile();            
        }
       
    }
    
    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
         int row = jList1.getSelectedIndex();
         if(row>=0)
         {
             modelList1.remove(row);
         }
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        if(getSession().getSessionId()==null)
        {
            JOptionPane.showMessageDialog(null, "Ho lamentam però el servidor\nde fitxers no respon.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        addNewAttachment();
    }//GEN-LAST:event_jButton3ActionPerformed

    @Override
    public void refreshUI() {
        //A user has been set (if this user has no associated SGD-idProfesores nothing will be enabled)
        grantModule = GrantSystem.getInstance(this.moduleName, coreCfg);
        grantModule.loadGrantForRole(coreCfg.getUserInfo().getRole(), grantModuleDefaults);
        jMensajesList.setEnabled(!grantModule.get("listEdit").isNone());
        
        maxlength = 0;
        Object get = this.getBeanModule().getIniParameters().get("mensajesup.maxlength");
        if(get!=null)
        {
            maxlength = ((Number) get).intValue();
        }
        //This is no applicable for richText 
        
//        if(maxlength>0)
//        {
//            jLabel5.setText("Màx. "+maxlength+" caràcters");
//        }
        
        this.idProfesores = coreCfg.getUserInfo().getIdSGD();
        jTable1.setEnabled(idProfesores>0);
        jTable2.setEnabled(idProfesores>0);
        jTable3.setEnabled(idProfesores>0);
        jTable4.setEnabled(idProfesores>0);
        jOutlookBar1.setEnabled(idProfesores>0);
        jOutlookBar1.setEnabledAt(0,idProfesores>0);
        jOutlookBar1.setEnabledAt(1,idProfesores>0);
        jOutlookBar1.setEnabledAt(2,idProfesores>0);
        jOutlookBar1.setEnabledAt(3,idProfesores>0);
        if(idProfesores<=0)
        {
            JOptionPane.showMessageDialog(this, "No teniu associat un usuari SGD.\nNo podeu gestionar els missatges a PDAs");
            return;
        }
     
        Profesores prof = coreCfg.getSgdClient().getUser();
        jCheckBox2.setEnabled(prof.isTutor());
        jOutlookBar1.setEnabledAt(3,prof.isEnviarSMS());
        jNewMail.setEnabled(prof.isEnviarSMS());
      
        //jCheckBox2.setEnabled(listening);*
      
        //Fill tables
        new InboxLoader().execute();
        new SentLoader().execute();
        new TrashLoader().execute();
          
    }

    @Override
    public void setMenus(JMenuBar jMenuBar1, JToolBar jToolbar1, StatusBar jStatusBar1) {
        jToolbar1.add(localToolbar1);
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private net.atlanticbb.tantlinger.shef.HTMLEditorPane hTMLEditorPane1;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JCheckBox jCheckBox1;
    private javax.swing.JCheckBox jCheckBox2;
    private javax.swing.JComboBox jComboBox1;
    private javax.swing.JComboBox jComboBox2;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JList jList1;
    private javax.swing.JButton jMensajesList;
    private javax.swing.JButton jNewMail;
    private javax.swing.JTabbedPane jOutlookBar1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JTable jTable2;
    private javax.swing.JTable jTable3;
    private javax.swing.JTable jTable4;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JToolBar localToolbar1;
    // End of variables declaration//GEN-END:variables

    private void fillInbox() {
        while(jTable1.getRowCount()>0)
        {
            modelTable1.removeRow(0);
        }
        inbox = coreCfg.getSgdClient().getMensajesCollection().getInbox(""+idProfesores, false);
        for(BeanInbox bean: inbox)
        {
            CellTableState cts = new CellTableState("",bean.getId(),0);
            cts.setTooltip("Moure a paperera");
            
            int icona = bean.getAttachments().isEmpty()?2:1;
            
            CellTableState cts2 = new CellTableState(bean.getTexto(),0,icona);
            cts2.setTooltip("Mostra el missatge");
            
            
            modelTable1.addRow(new Object[]{cts,bean.isLeido(), bean.getFechaEnviado(), bean.getRemitente(), cts2});
        }
        
    }


    private void fillSent() {
        while(jTable2.getRowCount()>0)
        {
            modelTable2.removeRow(0);
        }
        outbox = coreCfg.getSgdClient().getMensajesCollection().getOutbox(""+idProfesores);
        for(BeanOutbox bean: outbox)
        {
            //Count ; in destinatarios
            int count = 0;
            for(int i=0; i<bean.getDestinatarios().length(); i++)
            {
                 if(bean.getDestinatarios().charAt(i)==';')
                 {
                     count +=1;
                 }
            }
            String desti = bean.getDestinatarios();
            if(desti.length()>300)
            {
                desti = bean.getDestinatarios().substring(0,299)+"...";
            }
            
            int icona = bean.getAttachments().isEmpty()?2:1;
            
            CellTableState cts2 = new CellTableState(bean.getTexto(),0,icona);
            //cts2.setTooltip("Mostra el missatge");
            
           modelTable2.addRow(new Object[]{bean.getFechaEnviado(), "("+count+") "+ desti, cts2});
        }
        

    }
    
    
    private void fillTrash() {
        while(jTable3.getRowCount()>0)
        {
            modelTable3.removeRow(0);
        }
        trash = coreCfg.getSgdClient().getMensajesCollection().getInbox(""+idProfesores, true);
        for(BeanInbox bean: trash)
        {
            CellTableState cts = new CellTableState("",bean.getId(),0);
            cts.setTooltip("Moure a rebuts");
            
            int icona = bean.getAttachments().isEmpty()?2:1;
            
            CellTableState cts2 = new CellTableState(bean.getTexto(),0,icona);
            //cts2.setTooltip("Mostra el missatge");
            modelTable3.addRow(new Object[]{cts, bean.getFechaEnviado(), bean.getRemitente(), cts2});
        }
        

    }

    private void fillProfesList() {
        listening = false;
        
        while(jTable4.getRowCount()>0)
        {
            modelTable4.removeRow(0);
        }
        
        String search = jTextField1.getText().toUpperCase();
        for(BeanProfSms bean: listProf)
        {
            if(bean.getNombre().toUpperCase().contains(search) || search.trim().isEmpty())
            {
                 modelTable4.addRow(new Object[]{bean.getCodigo(),bean.isSelected(),bean.getNombre()});
            }
        }
        
        listening = true;
    }

    private void generateEquipDocent() {
        equipDocent = new ArrayList<String>();
        
      
//Aquesta select dona caps de departament (codigo tarea=3)
//SELECT DISTINCT p.idProfesores,p.nombre FROM horarios AS h INNER JOIN tareas AS t ON t.idProfesores = h.idTareas 
//INNER JOIN profesores AS p ON p.idProfesores=h.idProfesores WHERE t.codigo=3

//Aquesta select dona tots els tutors (codigo tarea=35)
//SELECT DISTINCT p.idProfesores,p.nombre FROM horarios AS h INNER JOIN tareas AS t ON t.idProfesores = h.idTareas 
//INNER JOIN profesores AS p ON p.idProfesores=h.idProfesores WHERE t.codigo=35
        
    
//Aquesta select dona l'equip docent d'un grup te per tutor idProfesores  
      
String SQL1 = " SELECT DISTINCT p.id FROM "+
        " grupos AS g   "+
        " INNER JOIN  "+
        " grupasig AS ga  "+
        " ON g.id=ga.idGrupos  "+
        " INNER JOIN  "+
        " clasesdetalle AS cd "+ 
        " ON cd.idGrupasig = ga.id "+ 
        " INNER JOIN "+
        " horarios AS h  "+
        " ON h.idClases = cd.idClases "+ 
        " INNER JOIN profesores AS p  "+
        " ON p.id = h.idProfesores  "+
        " WHERE g.idProfesores="+ idProfesores +" ORDER BY p.nombre";;
        
        Statement st;
        try {
            st = coreCfg.getSgd().createStatement();
            ResultSet rs1 = coreCfg.getSgd().getResultSet(SQL1,st);
            while(rs1!=null && rs1.next())
            {
                equipDocent.add(rs1.getString(1));
            }
            rs1.close();
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(MensajesUPModule.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
   

    @Override
    public void dispose() {
        super.dispose();
        timer.stop();
        if(session!=null)
        {
            session.close();
            session = null;
        }
    }
    
    //Lazy initialization of session
    private CloudClientSession getSession()
    {
        //Try to get connection
        if(session==null || session.ping()!=200)
        {
            CloudClientSession.baseURL = CoreCfg.cloudBaseURL;
            Profesores prof =coreCfg.getSgdClient().getUser();
            session = new CloudClientSession(prof.getSystemUser(),prof.getClaveUP());
            hTMLEditorPane1.setCloudClientSession(session);
        }
        return session;
    }
    
      
    private class InboxLoader extends javax.swing.SwingWorker<Void,Void>
    {
 
        @Override
        protected Void doInBackground() throws Exception {
            if(isInboxLoading)
            {
                return null;
            }
            isInboxLoading = true;
            fillInbox();
            isInboxLoading = false;
            return null;
        }
        
    }
    
    private class SentLoader extends javax.swing.SwingWorker<Void,Void>
    {
 
        @Override
        protected Void doInBackground() throws Exception {
            if(isSentLoading)
            {
                return null;
            }
            isSentLoading = true;
            fillSent();
            isSentLoading = false;
            
            return null;
        }
        
    }

    private class TrashLoader extends javax.swing.SwingWorker<Void,Void>
    {
        @Override
        protected Void doInBackground() throws Exception {
            if(isTrashLoading)
            {
                return null;
            }
            isTrashLoading = true;
            fillTrash();
            isTrashLoading = false;
            
            return null;
        }
        
    }
  
   
}
