package local.bitwig.video;

import java.awt.*;
import java.nio.file.Path;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class VideoControls {
    private final JFrame window = new JFrame("Bitwig Video · 0.5.0-dev.3");
    private static final String[] POSITIONS={"top","bottom","left","right","float"};
    private final JComboBox<String> placement=new JComboBox<>(new String[]{"上方","下方","左侧","右侧","独立窗口"});
    private boolean refreshingPlacement;
    private final DefaultListModel<VideoMarkerInfo> markerModel=new DefaultListModel<>();
    private final JList<VideoMarkerInfo> markerList=new JList<>(markerModel);
    private java.util.List<VideoMarkerInfo> markers=java.util.List.of();
    private final JTextField path = new JTextField();
    private final JComboBox<String> mediaChoice=new JComboBox<>();
    private java.util.List<String> media=java.util.List.of();
    private final JButton choose=new JButton("导入视频…");
    private final JLabel explanation=new JLabel();
    private final JSpinner offset = new JSpinner(new SpinnerNumberModel(0.0, -86400.0, 86400.0, 0.04));
    private final JCheckBox enabled = new JCheckBox("启用当前工程的视频预览");
    private final JLabel status = new JLabel("选择视频后跟随 Bitwig 播放");
    private final JLabel time = new JLabel(" ");
    private UiClient.State shown;
    private final UiClient client;
    private long lastShow, lastError;

    public static void main(String[] args) throws Exception {
        UiClient client=new UiClient(Integer.parseInt(args[0]),args[1]);
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(()->new VideoControls(client).show());
    }

    VideoControls(UiClient client) {
        this.client=client;
        JPanel body = new JPanel(); body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(18, 20, 18, 20));
        JLabel title = new JLabel("视频预览"); title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setAlignmentX(0); body.add(title); body.add(Box.createVerticalStrut(10));
        JLabel hint = new JLabel("把视频拖入编曲区即可创建片段；预览位置和窗口大小会自动记住。");
        hint.setAlignmentX(0); body.add(hint); body.add(Box.createVerticalStrut(16));
        JPanel placementRow=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));placementRow.setAlignmentX(0);
        placementRow.add(new JLabel("预览位置  "));placementRow.add(placement);placementRow.add(Box.createHorizontalStrut(10));
        JButton resetLayout=new JButton("恢复默认布局");resetLayout.addActionListener(e->client.send("reset-layout"));placementRow.add(resetLayout);
        placement.setSelectedIndex(1);placement.addActionListener(e->{if(!refreshingPlacement)client.send("placement","position",POSITIONS[placement.getSelectedIndex()]);});
        body.add(placementRow);body.add(Box.createVerticalStrut(12));
        path.setEditable(false); path.setColumns(36);
        choose.addActionListener(e -> choose(!media.isEmpty()?"relink":client.state().config().file().isEmpty()?"import":"choose"));
        choose.setToolTipText("拖入视频会自动绑定；重新定位仅用于修复已经丢失的素材。");
        mediaChoice.setVisible(false);
        JPanel sourceFields=new JPanel(new BorderLayout());sourceFields.add(path,BorderLayout.CENTER);sourceFields.add(mediaChoice,BorderLayout.SOUTH);
        JPanel fileRow = new JPanel(new BorderLayout(8, 0)); fileRow.add(sourceFields); fileRow.add(choose, BorderLayout.EAST);
        fileRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32)); fileRow.setAlignmentX(0); body.add(fileRow);
        body.add(Box.createVerticalStrut(14));
        JPanel start = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        start.add(new JLabel("旧版单视频起点（秒）  ")); start.add(offset);
        start.setAlignmentX(0); start.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32)); body.add(start);
        explanation.setText("使用视频片段后，请直接在编曲区移动、切分和修剪。");
        explanation.setAlignmentX(0); body.add(Box.createVerticalStrut(6)); body.add(explanation);
        body.add(Box.createVerticalStrut(10)); enabled.setAlignmentX(0); body.add(enabled);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); buttons.setAlignmentX(0);
        JButton apply = new JButton("应用到工程");
        apply.addActionListener(e -> {
            if (shown == null || shown.document() == 0) return;
            try {
                offset.commitEdit();
                client.send("apply","document",shown.document(),"offset",((Number)offset.getValue()).doubleValue(),"enabled",enabled.isSelected());
            } catch (Exception failure) { error(failure.getMessage()); }
        });
        JButton show = new JButton("恢复视频画面"); show.addActionListener(e -> client.send("show"));
        JButton hide = new JButton("隐藏视频画面"); hide.addActionListener(e -> client.send("hide"));
        buttons.add(apply); buttons.add(Box.createHorizontalStrut(8)); buttons.add(show);
        buttons.add(Box.createHorizontalStrut(8)); buttons.add(hide);
        body.add(Box.createVerticalStrut(12)); body.add(buttons); body.add(Box.createVerticalStrut(16));
        JButton insert=new JButton("在当前位置导入视频片段…");insert.setAlignmentX(0);insert.addActionListener(e->choose("import"));
        body.add(insert);body.add(Box.createVerticalStrut(12));
        JPanel audioButtons=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));audioButtons.setAlignmentX(0);
        for(String[] action:new String[][]{{"提取并绑定音频","bind-audio"},{"分离音视频","separate"},{"重试素材收集","collect"},{"取消导入","cancel-media"}}){
            JButton button=new JButton(action[0]);button.addActionListener(e->client.send(action[1],"document",client.state().document()));
            audioButtons.add(button);audioButtons.add(Box.createHorizontalStrut(8));
        }
        body.add(audioButtons);body.add(Box.createVerticalStrut(12));
        JPanel timeButtons=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));timeButtons.setAlignmentX(0);
        JButton lockTime=new JButton("将选中旧视频转为绝对时间");lockTime.addActionListener(e->client.send("lock-video-time","document",client.state().document()));
        timeButtons.add(lockTime);body.add(timeButtons);body.add(Box.createVerticalStrut(10));
        status.setAlignmentX(0); time.setAlignmentX(0); body.add(status); body.add(Box.createVerticalStrut(6)); body.add(time);
        JLabel save = new JLabel("照常 Ctrl+S；素材收集完成后，复制整个工程文件夹即可携带音视频。");
        save.setAlignmentX(0); body.add(Box.createVerticalStrut(16)); body.add(save);
        body.add(Box.createVerticalStrut(18));
        JLabel markerTitle=new JLabel("工程时间线标记");markerTitle.setAlignmentX(0);body.add(markerTitle);
        markerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        markerList.setCellRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
            var label=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
            if(value instanceof VideoMarkerInfo m)label.setText(String.format(java.util.Locale.ROOT,"%10.3f s   [%s]  %s",m.seconds(),m.locked()?"时间锁定":"音乐位置",m.name()));return label;
        }});
        JScrollPane markerScroll=new JScrollPane(markerList);markerScroll.setPreferredSize(new Dimension(620,150));markerScroll.setAlignmentX(0);body.add(markerScroll);
        JPanel markerButtons=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));markerButtons.setAlignmentX(0);
        JButton addMarker=new JButton("当前位置打点…");addMarker.addActionListener(e->{var origin=client.state();String name=JOptionPane.showInputDialog(window,"标记名称（可留空自动编号）","画面事件 / 音乐入点",JOptionPane.PLAIN_MESSAGE);if(name!=null)client.send("marker-add","document",origin.document(),"seconds",origin.seconds(),"name",name);});
        JButton renameMarker=new JButton("重命名…");renameMarker.addActionListener(e->{var m=markerList.getSelectedValue();if(m==null)return;String name=JOptionPane.showInputDialog(window,"标记名称",m.name());if(name!=null)client.send("marker-rename","document",client.state().document(),"id",m.id(),"name",name);});
        JButton goMarker=new JButton("跳到标记");goMarker.addActionListener(e->goMarker());
        markerList.addMouseListener(new java.awt.event.MouseAdapter(){public void mouseClicked(java.awt.event.MouseEvent e){if(e.getClickCount()==2)goMarker();}});
        markerButtons.add(addMarker);markerButtons.add(Box.createHorizontalStrut(8));markerButtons.add(renameMarker);markerButtons.add(Box.createHorizontalStrut(8));markerButtons.add(goMarker);body.add(markerButtons);
        JPanel markerModes=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));markerModes.setAlignmentX(0);
        for(boolean lock:new boolean[]{true,false}){JButton mode=new JButton(lock?"锁定标记时间":"标记按音乐位置");mode.addActionListener(e->{var m=markerList.getSelectedValue();if(m!=null)client.send("marker-lock","document",client.state().document(),"id",m.id(),"locked",lock);});markerModes.add(mode);markerModes.add(Box.createHorizontalStrut(8));}body.add(markerModes);
        window.setContentPane(body); window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        window.pack(); window.setMinimumSize(new Dimension(680, window.getHeight())); window.setLocationByPlatform(true);
        new Timer(150, e -> refresh()).start(); refresh();
    }
    void show() { refresh(); window.setVisible(true); window.toFront(); }
    private void choose(String action) {
        UiClient.State origin = client.state();
        if (origin.document() == 0) { error("请先打开一个 Bitwig 工程。"); return; }
        JFileChooser picker = new JFileChooser(); picker.setDialogTitle("选择视频（复制到工程，源文件保持不变）");
        picker.setMultiSelectionEnabled(action.equals("import"));
        picker.setFileFilter(new FileNameExtensionFilter("视频文件", "mp4", "mov", "mkv", "avi", "webm", "m4v", "mxf", "mpeg", "mpg"));
        String previous=action.equals("relink")?(String)mediaChoice.getSelectedItem():origin.config().file();
        if(previous!=null&&!previous.isEmpty())picker.setSelectedFile(Path.of(previous).toFile());
        if(picker.showOpenDialog(window)==JFileChooser.APPROVE_OPTION){
            if(action.equals("import"))client.send(action,"document",origin.document(),"files",java.util.Arrays.stream(picker.getSelectedFiles()).map(f->f.toPath().toAbsolutePath().toString()).toList());
            else client.send(action,"document",origin.document(),"file",picker.getSelectedFile().toPath().toAbsolutePath().toString(),"previous",previous);
        }
    }
    private void refresh() {
        UiClient.State s = client.state();
        refreshingPlacement=true;try{for(int i=0;i<POSITIONS.length;i++)if(POSITIONS[i].equals(s.previewPosition()))placement.setSelectedIndex(i);}finally{refreshingPlacement=false;}
        if (shown == null || shown.document() != s.document() || !shown.config().equals(s.config())) {
            shown = s; path.setText(s.config().file()); offset.setValue(s.config().offset()); enabled.setSelected(s.config().enabled());
        }
        status.setText(s.status());
        explanation.setText(s.timeMode()==null?"":s.timeMode());
        if(s.media()!=null&&!s.media().equals(media)){
            Object selected=mediaChoice.getSelectedItem();media=s.media();mediaChoice.removeAllItems();for(String file:media)mediaChoice.addItem(file);
            if(selected!=null&&media.contains(selected))mediaChoice.setSelectedItem(selected);
            boolean timeline=!media.isEmpty();mediaChoice.setVisible(timeline);path.setVisible(!timeline);offset.setEnabled(!timeline);
            window.revalidate();
        }
        choose.setText(!media.isEmpty()?"重新定位素材…":s.config().file().isEmpty()?"导入视频…":"旧版视频重联…");
        if(s.markers()!=null&&!s.markers().equals(markers)){
            var selected=markerList.getSelectedValue();markers=s.markers();markerModel.clear();
            for(var m:markers){markerModel.addElement(m);if(selected!=null&&selected.id()==m.id())markerList.setSelectedValue(m,true);}
        }
        time.setText(String.format(java.util.Locale.ROOT, "工程 %8.3f s   %s   %s", s.seconds(), s.playing() ? "播放中" : "已停止", s.time()));
        if(s.show()!=lastShow){lastShow=s.show();window.setVisible(true);window.toFront();}
        if(s.errorSerial()!=lastError){lastError=s.errorSerial();error(s.error());}
    }
    private void error(String text){JTextArea message=new JTextArea(text,4,56);message.setLineWrap(true);message.setWrapStyleWord(true);message.setEditable(false);message.setOpaque(false);JOptionPane.showMessageDialog(window,new JScrollPane(message),"Video Preview",JOptionPane.ERROR_MESSAGE);}
    private void goMarker(){var m=markerList.getSelectedValue();if(m!=null)client.send("marker-go","document",client.state().document(),"id",m.id());}
}
