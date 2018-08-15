package gui.astViewer;

import gui.toolkit.FileChooserAndOpener;
import gui.toolkit.MainFrame;
import nameTable.NameTableManager;
import nameTable.filter.NameDefinitionKindFilter;
import nameTable.nameDefinition.MethodDefinition;
import nameTable.nameDefinition.NameDefinition;
import nameTable.nameDefinition.NameDefinitionKind;
import nameTable.visitor.NameDefinitionVisitor;
import java.io.OutputStream;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.Buffer;
import java.util.List;
import graph.cfg.ControlFlowGraph;
import graph.cfg.creator.CFGCreator;

import graph.cfg.analyzer.TestCFGCreator;
import javax.swing.*;
import util.Debug;
import org.eclipse.jdt.core.dom.CompilationUnit;

import graph.basic.GraphNode;
import graph.cfg.ControlFlowGraph;
import graph.cfg.ExecutionPoint;
import graph.cfg.analyzer.LiveVariableAnalyzer;
import graph.cfg.analyzer.LiveVariableDefinition;
import graph.cfg.analyzer.LiveVariableRecorder;

public class TestASTViewer {
	public static void main(String[] args) {
		// ��ʼ�������򣬵�����λ�úͿ�ȣ�ʹ����ʾ�����İ�ť��Ư��
		int widthSpace = 15;
		int heightSpace = 100;
		
		MainFrame.init("Java���������ͼչʾ����", MainFrame.screenWidth-widthSpace, 
			MainFrame.screenHeight-heightSpace, 0, 0, "system");

		DemoMenuCreator demo = 
			new DemoMenuCreator(MainFrame.getContentPane(), MainFrame.getMainFrame());
		// ������ʾ�õĲ˵�����������˵����������������������������������
		demo.createMenu();
		// ���������򣬲�������ʾ
		MainFrame.start();
	}
}

class DemoMenuCreator {
	private Container place;			// ������ʾ���������
	private JFrame topLevelFrame;		// ���ò˵��Ķ�������
	private JTabbedPane tabbedPane;
	private int astTabIndex;
	private JTextArea sourceText;		// ���ڷ���Դ�����ļ�
	private JTextArea astText;			// ���ڷ��ó����﷨��
	private JTextArea cfgText;			// ���ڷ��ó��������ͼ
	private JTextArea fixValueText;     // ���ڷ��ö�ֵ���������ͼ
	private int cfgTabIndex;
	private int fixValueIndex;
	private PrintWriter output;
	private List<ControlFlowGraph> cfgList = null; //���ڴ洢��������ͼ
	private JTextArea liveVariableText;  //ï�ִ���
	private int liveVariableTabIndex;    //ï�ִ���
	
	private NameTableManager manager;
	private final String OPEN_COMMAND = "open";
	private final String ASTPARSER_COMMAND = "astparser";
	private final String ABOUT_COMMAND = "about";
	private final String EXIT_COMMAND = "exit";
	private final String CONCISEAST_COMMAND = "consiceast";
	private final String CREATE_CFG_COMMAND = "createCFG";
	private final String FIXED_VALUE_COMMAND = "fixedVAL";
	private final String MAX_FIXED_VALUE_COMMAND = "maxFixedVAL";
	private final String LIVEVARIABLE_ANALYSISI_COMMAND = "liveVariable"; //ï�ִ���
	
	private FileChooserAndOpener fileOpener = null;
	private CompilationUnit astRoot = null;
	
	public DemoMenuCreator(Container place, JFrame topLevelFrame) {
		this.place = place;
		this.topLevelFrame = topLevelFrame;
		fileOpener = new FileChooserAndOpener(topLevelFrame);
		try {
			OutputStream os = new FileOutputStream("C:\\Java\\test2.dot");
			output = new PrintWriter(os);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void createMenu() {
		JSplitPane hSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
		hSplitPane.setDividerLocation(MainFrame.screenWidth/2);
		place.add(hSplitPane);

		sourceText = new JTextArea();
		sourceText.setEditable(false);
		JScrollPane scrollPane = new JScrollPane(sourceText);
		hSplitPane.setLeftComponent(scrollPane);

		tabbedPane = new JTabbedPane();
		hSplitPane.setRightComponent(tabbedPane);
		
		astText = new JTextArea();
		astText.setEditable(false);
		scrollPane = new JScrollPane(astText);
		tabbedPane.addTab("�����﷨��", scrollPane);
		astTabIndex = 0;
		
		cfgText = new JTextArea();
		cfgText.setEditable(false);
		scrollPane = new JScrollPane(cfgText);
		tabbedPane.addTab("������ͼ", scrollPane);
		cfgTabIndex = 1;
		
		fixValueText = new JTextArea();
		fixValueText.setEditable(false);
		scrollPane = new JScrollPane(fixValueText);
		tabbedPane.addTab("(���)��ֵ�������ͼ", scrollPane);
		fixValueIndex = 2;
		
		//ï�ִ���
		liveVariableText = new JTextArea();
		liveVariableText.setEditable(false);
		scrollPane = new JScrollPane(liveVariableText);
		tabbedPane.addTab("��Ծ����", scrollPane);
		liveVariableTabIndex = 3;
		
		hSplitPane.resetToPreferredSizes();
		
		// �����˵��ļ�����
		MenuListener menuListener = new MenuListener();
		// �����˵���
		JMenuBar menuBar = new JMenuBar();
		topLevelFrame.setJMenuBar(menuBar);		// �����ڶ�������
		
		// ������һ�����˵���
		JMenu menu = new JMenu("�ļ�(F)");
		menu.setMnemonic(KeyEvent.VK_F);		// �����ַ���FΪ��ݼ�
		menuBar.add(menu);						// ���뵽�˵���
		// ���õ�һ�����˵���ĵ�һ���Ӳ˵���
		JMenuItem menuItem = new JMenuItem("��(O)", null);
		menuItem.setMnemonic(KeyEvent.VK_O);
		// ���ô˲˵���ļ��ټ�ΪCtrl+O
		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, 
			ActionEvent.CTRL_MASK));
		menuItem.addActionListener(menuListener);
		menuItem.setActionCommand(OPEN_COMMAND);		// ��������Ϊ�˳�����
		menu.add(menuItem);						// ���뵽��һ�����˵���

		// ���õ�һ�����˵���ĵ�һ���Ӳ˵���
		menuItem = new JMenuItem("�﷨��(A)", null);
		menuItem.setMnemonic(KeyEvent.VK_A);
		// ���ô˲˵���ļ��ټ�ΪCtrl+A
		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, 
			ActionEvent.CTRL_MASK));
		menuItem.addActionListener(menuListener);
		menuItem.setActionCommand(ASTPARSER_COMMAND);		// ��������Ϊ�˳�����
		menu.add(menuItem);						// ���뵽��һ�����˵���

		// ���õ�һ�����˵���ĵڶ����Ӳ˵���
		menuItem = new JMenuItem("�����﷨��(C)", null);
		menuItem.setMnemonic(KeyEvent.VK_C);
		// ���ô˲˵���ļ��ټ�ΪCtrl+C
		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, 
			ActionEvent.CTRL_MASK));
		menuItem.addActionListener(menuListener);
		menuItem.setActionCommand(CONCISEAST_COMMAND);		// ��������Ϊ�˳�����
		menu.add(menuItem);						// ���뵽�ڶ������˵���

		// ���õ�һ�����˵���ĵ������Ӳ˵���
		menuItem = new JMenuItem("������ͼ(G)", null);
		menuItem.setMnemonic(KeyEvent.VK_G);
		// ���ô˲˵���ļ��ټ�ΪCtrl+G
		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, 
			ActionEvent.CTRL_MASK));
		menuItem.addActionListener(menuListener);
		menuItem.setActionCommand(CREATE_CFG_COMMAND);		// ��������Ϊ�˳�����
		menu.add(menuItem);						// ���뵽�ڶ������˵���

		
		// ���õ�һ�����˵���ĵ�����Ӳ˵���
		menuItem = new JMenuItem("��ֵ�������(V)", null);
		menuItem.setMnemonic(KeyEvent.VK_V);
		// ���ô˲˵���ļ��ټ�ΪCtrl+V
		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, 
				ActionEvent.CTRL_MASK));
		menuItem.addActionListener(menuListener);
		menuItem.setActionCommand(FIXED_VALUE_COMMAND);		// ��������Ϊ�˳�����
		menu.add(menuItem);						// ���뵽�ڶ������˵���
		
		// ���õ�һ�����˵��ĵ������Ӳ˵���
		menuItem = new JMenuItem("���ֵ�������(M)", null);
		menuItem.setMnemonic(KeyEvent.VK_M);
		// ���ô˲˵���ļ��ټ�ΪCtrl+M
		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, 
				ActionEvent.CTRL_MASK));
		menuItem.addActionListener(menuListener);
		menuItem.setActionCommand(MAX_FIXED_VALUE_COMMAND);		// ��������Ϊ�˳�����
		menu.add(menuItem);	                     // ���뵽�ڶ������˵���
		
		//ï�ִ���
		menuItem = new JMenuItem("��Ծ����(L)", null);
		menuItem.setMnemonic(KeyEvent.VK_L);
		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, 
				ActionEvent.CTRL_MASK));
		menuItem.addActionListener(menuListener);
		menuItem.setActionCommand(LIVEVARIABLE_ANALYSISI_COMMAND);		// ��������Ϊ�˳�����
		menu.add(menuItem);						// ���뵽�ڶ������˵���
		
		
		menu.addSeparator();
		
		// Ϊ��һ�����˵�������һ���˵���
		menuItem = new JMenuItem("�˳�");
		menuItem.addActionListener(menuListener);
		menuItem.setActionCommand(EXIT_COMMAND);		// ��������Ϊ�˳�����
		menu.add(menuItem);
		// �ڶ������˵���.
		menu = new JMenu("����(H)");
		menu.setMnemonic(KeyEvent.VK_H);
		menuBar.add(menu);
		menuItem = new JMenuItem("����...");
		menuItem.setAccelerator(KeyStroke.getKeyStroke("F1"));
		menuItem.addActionListener(menuListener);
		menuItem.setActionCommand(ABOUT_COMMAND);
		menu.add(menuItem);
	}
	
	// �����˵���İ��¶���
	private class MenuListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			JMenuItem source = (JMenuItem)(e.getSource());
			String command = source.getActionCommand();
			if (command.equals(ABOUT_COMMAND)) {
				// ����һ������ʾһЩ��Ϣ
				JOptionPane.showMessageDialog(MainFrame.getMainFrame(), "Java��������﷨��չʾ", "����", 
					JOptionPane.WARNING_MESSAGE);
			} else if (command.equals(EXIT_COMMAND)) System.exit(1);  // �˳���������
			else if (command.equals(OPEN_COMMAND)) {
				if (fileOpener.chooseFileName() == true && fileOpener.loadFile() == true) {
					sourceText.setText(fileOpener.getFileContentsWithLineNumber());
					topLevelFrame.setTitle(fileOpener.getFileName()); 
					astText.setText("");
					cfgText.setText("");
				}
			} else if (command.equals(ASTPARSER_COMMAND)) {
				String fileContents = fileOpener.getFileContents();
				if (fileContents == null) {
					fileOpener.chooseFileName();
					fileOpener.loadFile();
					fileContents = fileOpener.getFileContents();
					topLevelFrame.setTitle(fileOpener.getFileName()); 
					cfgText.setText("");
				}
				sourceText.setText(fileOpener.getFileContentsWithLineNumber());
				
				SimpleASTViewer viewer = new SimpleASTViewer(topLevelFrame, fileContents);
				viewer.parseSourceCode();
				String errorMessage = viewer.getParseErrorMessage();
				if (errorMessage != null) {
					JOptionPane.showMessageDialog(MainFrame.getMainFrame(), 
							"������ִ���\n" + errorMessage, "��ʾ", JOptionPane.WARNING_MESSAGE);	
				} 
				if (viewer.hasParserError()) astRoot = null;
				else astRoot = viewer.getASTRoot();
				astText.setText(viewer.getASTViewerText());
				tabbedPane.setSelectedIndex(astTabIndex);
			} else if (command.equals(CONCISEAST_COMMAND)) {
				String fileContents = fileOpener.getFileContents();
				if (fileContents == null) {
					fileOpener.chooseFileName();
					fileOpener.loadFile();
					fileContents = fileOpener.getFileContents();
					topLevelFrame.setTitle(fileOpener.getFileName()); 
					cfgText.setText("");
				}
				sourceText.setText(fileOpener.getFileContentsWithLineNumber());
				
				ConciseASTViewer viewer = new ConciseASTViewer(topLevelFrame, fileContents);
				viewer.parseSourceCode();
				String errorMessage = viewer.getParseErrorMessage();
				if (errorMessage != null) {
					JOptionPane.showMessageDialog(MainFrame.getMainFrame(), 
							"������ִ���\n" + errorMessage, "��ʾ", JOptionPane.WARNING_MESSAGE);					
				} 
				if (viewer.hasParserError()) astRoot = null;
				else astRoot = viewer.getASTRoot();
				astText.setText(viewer.getASTViewerText());
				tabbedPane.setSelectedIndex(astTabIndex);
			} else if (command.equals(CREATE_CFG_COMMAND)){
				String fileContents = fileOpener.getFileContents();
				if (fileContents == null) {
					fileOpener.chooseFileName();
					fileOpener.loadFile();
					fileContents = fileOpener.getFileContents();
					topLevelFrame.setTitle(fileOpener.getFileName()); 
					
					astRoot = null; 		// For regenerate the ast for the new file!
				}
				sourceText.setText(fileOpener.getFileContentsWithLineNumber());
				
				if (astRoot == null) {
					SimpleASTViewer viewer = new SimpleASTViewer(topLevelFrame, fileContents);
					viewer.parseSourceCode();
					String errorMessage = viewer.getParseErrorMessage();
					if (errorMessage != null) {
						JOptionPane.showMessageDialog(MainFrame.getMainFrame(), 
								"������ִ���\n" + errorMessage, "��ʾ", JOptionPane.WARNING_MESSAGE);	
					} 
					astRoot = viewer.getASTRoot();
					astText.setText(viewer.getASTViewerText());
				}

				try {
					ControlFlowGraphViewer viewer = new ControlFlowGraphViewer(fileOpener.getFileName(), astRoot);
					cfgText.setText(viewer.createCFGToText());
					tabbedPane.setSelectedIndex(cfgTabIndex);
				} catch (Exception exp) {
					exp.printStackTrace();
					cfgText.setText(exp.toString());
					JOptionPane.showMessageDialog(MainFrame.getMainFrame(), 
							"���ɿ�����ͼ��������", "��ʾ", JOptionPane.WARNING_MESSAGE);
				}
			} else if (command.equals(LIVEVARIABLE_ANALYSISI_COMMAND)) {
				String fileContents = fileOpener.getFileContents();
				if (fileContents == null) {
					fileOpener.chooseFileName();
					fileOpener.loadFile();
					fileContents = fileOpener.getFileContents();
					topLevelFrame.setTitle(fileOpener.getFileName());
					astText.setText("");
					cfgText.setText("");
					liveVariableText.setText("");
				}
				sourceText.setText(fileOpener.getFileContentsWithLineNumber());
				
				String path = "/Users/merlyn/git/JA-ver2/JAnalyzer/template.java";
				File file = new File(path);
				FileWriter fileWriter = null;
				try {
					fileWriter = new FileWriter(file);
				} catch (IOException e2) {
					e2.printStackTrace();
				}
				try {
					fileWriter.write(fileContents);
					fileWriter.flush();
					fileWriter.close();
				} catch (IOException e1) {
					e1.printStackTrace();
					liveVariableText.setText("not ok");
				}
				tabbedPane.setSelectedIndex(liveVariableTabIndex);
				
				NameTableManager tableManager = NameTableManager.createNameTableManager(path);
				
				NameDefinitionVisitor visitor = new NameDefinitionVisitor(new NameDefinitionKindFilter(NameDefinitionKind.NDK_METHOD));
				tableManager.accept(visitor);
				List<NameDefinition> methodList = visitor.getResult();
				
				String output = "";
				
				for (NameDefinition method : methodList) {
					MethodDefinition methodDefinition = (MethodDefinition)method;
					output += methodDefinition.toString() + "\n";
					ControlFlowGraph controlFlowGraph = LiveVariableAnalyzer.create(tableManager, methodDefinition);
					
					if (controlFlowGraph != null)
						output += LiveVariableAnalyzer.outPutAllInfo(controlFlowGraph);
				}
				
				liveVariableText.setText(output);
			} else if(command.equals(FIXED_VALUE_COMMAND)) {
				String fileContents = fileOpener.getFileContents();
				
				if (fileContents == null) {
					fileOpener.chooseFileName();
					fileOpener.loadFile();
					fileContents = fileOpener.getFileContents();
					topLevelFrame.setTitle(fileOpener.getFileName()); 
					astRoot = null;
				}
				sourceText.setText(fileOpener.getFileContentsWithLineNumber());
				//String projectRootPath = fileOpener.getParentPath();  //������Ŀ¼�µ�����java�ļ��е����ķ���������з���
				
				String projectRootPath = fileOpener.getFullFilePath();  //�Ե���java�ļ����з���
				Debug.setStart(projectRootPath);
				
				
				//Debug.setStart(projectRootPath);
				//String result = TestCFGCreator.testCreateCFGWithReachName(projectRootPath, output); //������Ŀ¼�µ�����java�ļ��е����ķ���������з���
				
				String result = TestCFGCreator.testCreateCFGWithFileName(projectRootPath, output);  //�Ե���java�ļ����з���
				fixValueText.setText(result);
				tabbedPane.setSelectedIndex(fixValueIndex);
				
				
				/*try {
					ControlFlowGraphViewer viewer = new ControlFlowGraphViewer(fileOpener.getFileName(), astRoot);
					cfgText.setText(viewer.createCFGToText());
					tabbedPane.setSelectedIndex(cfgTabIndex);
				} catch (Exception exp) {
					exp.printStackTrace();
					cfgText.setText(exp.toString());
					JOptionPane.showMessageDialog(MainFrame.getMainFrame(), 
							"���ɿ�����ͼ��������", "��ʾ", JOptionPane.WARNING_MESSAGE);
				}*/
				
			} else if(command.equals(MAX_FIXED_VALUE_COMMAND)) {
				String fileContents = fileOpener.getFileContents();
				
				if (fileContents == null) {
					fileOpener.chooseFileName();
					fileOpener.loadFile();
					fileContents = fileOpener.getFileContents();
					topLevelFrame.setTitle(fileOpener.getFileName()); 
					astRoot = null;
				}
				sourceText.setText(fileOpener.getFileContentsWithLineNumber());
				String projectRootPath = fileOpener.getParentPath();  //������Ŀ¼�µ�����java�ļ��е����ķ���������з���
				
				//String projectRootPath = fileOpener.getFullFilePath();  //�Ե���java�ļ����з���
				Debug.setStart(projectRootPath);
				
				
				//Debug.setStart(projectRootPath);
				String result = TestCFGCreator.testCreateCFGWithReachName(projectRootPath, output); //������Ŀ¼�µ�����java�ļ��е����ķ���������з���
				
				//String result = TestCFGCreator.testCreateCFGWithFileName(projectRootPath, output);  //�Ե���java�ļ����з���
				fixValueText.setText(result);
				tabbedPane.setSelectedIndex(fixValueIndex);
				
			} else {
				// ����һ������ʾһЩ��Ϣ
				JOptionPane.showMessageDialog(MainFrame.getMainFrame(), 
					"�Բ�������˵����ܻ�û��ʵ�֣�", "��ʾ", JOptionPane.WARNING_MESSAGE);
			}
		}
	}
}

