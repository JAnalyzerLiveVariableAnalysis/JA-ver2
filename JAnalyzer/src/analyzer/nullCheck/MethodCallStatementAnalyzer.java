package analyzer.nullCheck;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.CompilationUnit;

import analyzer.dataTable.DataTableManager;
import graph.basic.GraphNode;
import graph.cfg.CFGNode;
import graph.cfg.CFGNodeType;
import graph.cfg.ControlFlowGraph;
import graph.cfg.ExecutionPoint;
import graph.cfg.analyzer.ReachNameDefinition;
import graph.cfg.analyzer.DominateNodeAnalyzer;
import graph.cfg.analyzer.ReachNameAnalyzer;
import graph.cfg.creator.CFGCreator;
import nameTable.NameTableManager;
import nameTable.filter.NameDefinitionKindFilter;
import nameTable.nameDefinition.DetailedTypeDefinition;
import nameTable.nameDefinition.MethodDefinition;
import nameTable.nameDefinition.NameDefinition;
import nameTable.nameDefinition.NameDefinitionKind;
import nameTable.nameDefinition.TypeDefinition;
import nameTable.nameReference.MethodReference;
import nameTable.nameReference.NameReference;
import nameTable.nameReference.NameReferenceKind;
import nameTable.nameReference.referenceGroup.NameReferenceGroup;
import nameTable.nameReference.referenceGroup.NameReferenceGroupKind;
import nameTable.nameScope.CompilationUnitScope;
import nameTable.visitor.NameDefinitionVisitor;
import sourceCodeAST.CompilationUnitRecorder;
import sourceCodeAST.SourceCodeFileSet;
import sourceCodeAST.SourceCodeLocation;
import util.Debug;

/**
 * @author Zhou Xiaocong
 * @since 2017Äê7ÔÂ16ÈÕ
 * @version 1.0
 *
 */
public class MethodCallStatementAnalyzer {

	public static void main(String[] args) {
		String rootPath = "C:\\";

		
		String[] paths = {"C:\\QualitasPacking\\recent\\eclipse_SDK\\eclipse_SDK-4.3\\", "F:\\QualitasPacking\\recent\\jfreechart\\jfreechart-1.0.13\\", 
							rootPath + "ZxcWork\\JAnalyzer\\src\\", rootPath + "ZxcTools\\EclipseSource\\org\\", rootPath + "ZxcTemp\\JAnalyzer\\src\\",
							rootPath + "ZxcWork\\ToolKit\\src\\sourceCodeAsTestCase\\CNExample.java", rootPath + "ZxcDeveloping\\OOPAndJavaExamples\\automata\\src\\", 
							rootPath + "ZxcProject\\AspectViz\\ZxcWork\\SortAnimator4\\", rootPath + "ZxcTools\\JDKSource\\", 
							rootPath + "ZxcCourse\\JavaProgramming\\JHotDraw5.2\\sources\\", rootPath + "ZxcWork\\FaultLocalization\\src\\", 
							rootPath + "ZxcTools\\ArgoUml\\", rootPath + "ZxcTools\\jEdit_5_1_0\\", 
							rootPath + "ZxcTools\\lucene_2_0_0\\", rootPath + "ZxcTools\\struts_2_0_1\\",
							rootPath + "ZxcTools\\apache_ant_1_9_3\\src\\", rootPath + "ZxcTools\\apache_ant_1_9_3\\src\\main\\org\\apache\\tools\\ant\\",
		};
		
		String path = paths[2];
		String result = rootPath + "ZxcWork\\ProgramAnalysis\\data\\result.txt";

		PrintWriter writer = new PrintWriter(System.out);
		PrintWriter output = new PrintWriter(System.out);
		
		try {
			String info = rootPath + "ZxcWork\\ProgramAnalysis\\data\\debug.txt";
			output = new PrintWriter(new FileOutputStream(new File(info)));
			Debug.setWriter(output);
			Debug.setScreenOn();
		} catch (Exception exc) {
			exc.printStackTrace();
			writer.close();
			output.close();
			return;
		}
		
		try {
//			writer = new PrintWriter(new FileOutputStream(new File(result)));
//			Debug.setStart("Begin collection....");
//			collectAllMethodCallStatementByScanningMethods(path, writer);
//			Debug.time("After collection...");
			Debug.setStart("Begin check....");
			checkReturnValueNullCheckConsistence(result);
			Debug.time("After check...");
		} catch (Exception exc) {
			exc.printStackTrace();
		}

		writer.close();
		output.close();
	}

	public static void checkReturnValueNullCheckConsistence(String file) throws IOException {
		DataTableManager manager = new DataTableManager("result");
		manager.read(file, true);
		int lineNumber = manager.getLineNumber();
		int index = 0;
		
		while (index < lineNumber) {
			int startCalleeIndex = index;
			String calleeLocation = manager.getCellValueAsString(index, "CalleeLocation");
			String status1 = manager.getCellValueAsString(index, "Status");
			
			System.out.println("Total " + lineNumber + ", Check " + index + ", callee " + calleeLocation);
			boolean same = true;
			int checkIndex = index + 1;
			while (checkIndex < lineNumber) {
				String calleeLocation2 = manager.getCellValueAsString(checkIndex, "CalleeLocation");
				String status2 = manager.getCellValueAsString(checkIndex, "Status");
				
				if (!calleeLocation2.equals(calleeLocation)) break;
				if (!status1.equals(status2) && !status1.equals("Unused") && !status2.equals("Unused")) {
					same = false;
					break;
				} else if (status1.equals("Unused") && !status2.equals("Unused")) status1 = status2;
				checkIndex = checkIndex + 1;
			}
			
			if (!same) {
				checkIndex = startCalleeIndex;
				String method = manager.getCellValueAsString(checkIndex, "Callee");
				calleeLocation = manager.getCellValueAsString(checkIndex, "CalleeLocation");
				Debug.println("Method: " + method + "() at " + calleeLocation);
				
				StringBuilder checkReference = new StringBuilder();
				StringBuilder uncheckReference = new StringBuilder();
				StringBuilder warningReference = new StringBuilder();
				StringBuilder redundantReference = new StringBuilder();
				
				while (checkIndex < lineNumber) {
					String calleeLocation2 = manager.getCellValueAsString(checkIndex, "CalleeLocation");
					String status2 = manager.getCellValueAsString(checkIndex, "Status");
					String reference = manager.getCellValueAsString(checkIndex, "CallRef");
					String refLocation = manager.getCellValueAsString(checkIndex, "CallRefLocation");

					System.out.println("\tCallee " + calleeLocation2 + ", status " + status2);
					if (!calleeLocation2.equals(calleeLocation)) break;
					
					if (status2.equals("NormalCheck")) checkReference.append("\t\t" + reference + "[" + refLocation + "]\r\n");
					else if (status2.equals("NormalUncheck")) uncheckReference.append("\t\t" + reference + "[" + refLocation + "]\r\n");
					else if (status2.equals("Warnning")) warningReference.append("\t\t" + reference + "[" + refLocation + "]\r\n");
					else if (status2.equals("Redundant")) redundantReference.append("\t\t" + reference + "[" + refLocation + "]\r\n");
					
					checkIndex = checkIndex + 1;
				}
				if (warningReference.length() > 0) {
					Debug.println("\tWarning: " );
					Debug.println(warningReference.toString());
				}
				if (redundantReference.length() > 0) {
					Debug.println("\tRedundant: " );
					Debug.println(redundantReference.toString());
				}
				if (checkReference.length() > 0) {
					Debug.println("\tNormalCheck: " );
					Debug.println(checkReference.toString());
				}
				if (uncheckReference.length() > 0) {
					Debug.println("\tNormalUncheck: " );
					Debug.println(uncheckReference.toString());
				}
			}
			index = checkIndex; 
		}
	}
	
	public static void collectAllMethodCallStatementByScanningMethods(String path, PrintWriter writer) {
		NameTableManager manager = NameTableManager.createNameTableManager(path);

		NameDefinitionVisitor visitor = new NameDefinitionVisitor(new NameDefinitionKindFilter(NameDefinitionKind.NDK_METHOD));
		manager.accept(visitor);
		List<NameDefinition> methodList = visitor.getResult();
		
		MethodReturnValueAnalyzer returnValueAnalyzer = new MethodReturnValueAnalyzer(manager);
		returnValueAnalyzer.analyze();
		
		int counter = 0;
		int methodCounter = 0;
		StringBuilder message = new StringBuilder("No\tCaller\tCallee\tLeftValue\tStatus\tReturnNull\tIsChecked\tIsUsed\tIsPrimitive\tIsConstructor\tCalleeLocation\tCallRef\tCallRefLocation\tExpression");
		writer.println(message.toString());
		
		for (NameDefinition nameDefinition : methodList) {
//			if (!nameDefinition.getFullQualifiedName().contains("getSpearmanCoefficient")) continue;
			
			methodCounter++;
			MethodDefinition method = (MethodDefinition)nameDefinition;
			if (method.isAutoGenerated()) continue; 
			TypeDefinition enclosingType = method.getEnclosingType();
			if (!enclosingType.isDetailedType()) continue;
			DetailedTypeDefinition type = (DetailedTypeDefinition)enclosingType;
			if (type.isAnonymous()) continue;
			
			List<MethodCallExpressionRecorder> infoList = collectMethodCallExpressionRecorder(manager, method);
			
			for (MethodCallExpressionRecorder info : infoList) {
				counter++;
				Debug.println(counter + " Method " + methodCounter + " " + method.getFullQualifiedName() + " call " + info.reference.getName());
				boolean returnNull = false;
				boolean isPrimitive = false;
				boolean isConstructor = false;

				if (info.callee != null) {
					if (returnValueAnalyzer.isReturnPrimitiveValue(info.callee)) isPrimitive = true;
					if (info.callee.isConstructor()) continue;
					if (returnValueAnalyzer.isPossibleReturnNull(info.callee)) returnNull = true;
				}

				message = new StringBuilder();
				message.append(counter + "\t" + method.getSimpleName());
				if (info.callee != null) message.append("\t" + info.callee.getFullQualifiedName());
				else message.append("\tUnknown");
				if (info.leftValue != null) message.append("\t" + info.leftValue.getFullQualifiedName());
				else message.append("\tNone");

				if (returnNull && info.haveChecked) message.append("\tNormalCheck");
				else if (returnNull && !info.haveChecked) {
					if (info.isUsed) message.append("\tWarnning");
					else message.append("\tUnused");
				} else if (!returnNull && info.haveChecked) message.append("\tRedundant");
				else message.append("\tNormalUncheck");

				if (returnNull) message.append("\tTRUE");
				else message.append("\tFALSE");
				
				if (info.haveChecked) message.append("\tTRUE");
				else message.append("\tFALSE");
				
				if (info.isUsed) message.append("\tTRUE");
				else message.append("\tFALSE");

				if (isPrimitive) message.append("\tTRUE");
				else message.append("\tFALSE");

				if (isConstructor) message.append("\tTRUE");
				else message.append("\tFALSE");

				if (info.callee != null) message.append("\t" + info.callee.getUniqueId());
				else message.append("\t~~");
				message.append("\t" + info.reference.toSimpleString() + "\t" + info.reference.getLocation().getUniqueId() + "\t" + info.expression);
				writer.println(message);
			}
		}
		writer.flush();
	}
	
	public static List<MethodCallExpressionRecorder> collectMethodCallExpressionRecorder(NameTableManager manager, MethodDefinition method) {
		List<MethodCallExpressionRecorder> result = new ArrayList<MethodCallExpressionRecorder>();

		CompilationUnitScope unitScope = manager.getEnclosingCompilationUnitScope(method);
		String unitFileName = unitScope.getUnitName();
		CompilationUnit astRoot = manager.getSouceCodeFileSet().findSourceCodeFileASTRootByFileUnitName(unitFileName);
		CompilationUnitRecorder unitRecorder = new CompilationUnitRecorder(unitFileName, astRoot);
		SourceCodeFileSet sourceCodeFileSet = manager.getSouceCodeFileSet();
		
		// Create a ControFlowGraph object
		ControlFlowGraph graph = CFGCreator.create(manager, unitRecorder, method);
		if (graph == null) {
			sourceCodeFileSet.releaseAST(unitFileName);
			sourceCodeFileSet.releaseFileContent(unitFileName);
			return result;
		}
		
		List<GraphNode> nodeList = graph.getAllNodes();
		if (nodeList == null) {
			sourceCodeFileSet.releaseAST(unitFileName);
			sourceCodeFileSet.releaseFileContent(unitFileName);
			return result;
		}
		
		MethodNullCheckCollector.setNullCheckReferenceRecorder(graph);
		ReachNameAnalyzer.reachNameAnalysis(manager, unitRecorder, method, graph);
		DominateNodeAnalyzer.dominateNodeAnalysis(graph, method);
		MethodNullCheckCollector.NullCheckReferenceAnalysis(manager, unitRecorder, method, graph);
		
		for (GraphNode node : nodeList) {
			CFGNode cfgNode = (CFGNode)node;
			if (cfgNode.getCFGNodeType() != CFGNodeType.N_EXECUTION_POINT) continue;
			ExecutionPoint exePoint = (ExecutionPoint)cfgNode;
			if (exePoint.isVirtual()) continue;
			
			NullCheckReferenceRecorder recorder = (NullCheckReferenceRecorder)exePoint.getFlowInfoRecorder();
			List<ReachNameDefinition> generatedNameList = recorder.getGeneratedNameList();
			// Collect method call expression in this AST node from the generated name list.
			
			for (ReachNameDefinition generatedName : generatedNameList) {
				List<MethodCallExpressionRecorder> infoList = collectMethodCallExpressionRecorderInReference(generatedName.getNode(), generatedName.getName(), generatedName.getValue());
				for (MethodCallExpressionRecorder info : infoList) result.add(info);
			}

			// Collect check null predicates in this AST node from its checkedReferenceList, and find if a left value in 
			// MethodCallExpressionRecorder list is checked in this AST Node
			List<NameReference> checkedReferenceList = recorder.getCheckedReferneceList();
			for (NameReference checkReference : checkedReferenceList) {
				List<ReachNameDefinition> rootReachNameList = ReachNameAnalyzer.getRootReachNameDefinitionList(graph, exePoint, checkReference);
				for (ReachNameDefinition rootReachName : rootReachNameList) {
					for (MethodCallExpressionRecorder info : result) {
						if (rootReachName.getNode() == info.node && rootReachName.getName() == info.leftValue) {
							info.haveChecked = true;
						}
					}
				}
			}
			
			// collect object reference dereference information in this AST node. If the object reference is not dereference, 
			// then it is not need to check it.
			List<NameReference> referenceList = recorder.getNodeReference();
			for (NameReference reference : referenceList) {
				reference.resolveBinding();
				extractLeftValueUsingInReference(graph, result, exePoint, reference);
			}
		}
		
		sourceCodeFileSet.releaseAST(unitFileName);
		sourceCodeFileSet.releaseFileContent(unitFileName);
		return result;
	}

	public static void extractLeftValueUsingInReference(ControlFlowGraph currentCFG, List<MethodCallExpressionRecorder> infoList, ExecutionPoint node, NameReference reference) {
		if (!reference.isGroupReference()) return;
		
		NameReferenceGroup group = (NameReferenceGroup)reference;
		List<NameReference> sublist = group.getSubReferenceList();
		NameReferenceGroupKind groupKind = group.getGroupKind();

		if (groupKind == NameReferenceGroupKind.NRGK_FIELD_ACCESS || 
				groupKind == NameReferenceGroupKind.NRGK_QUALIFIED_NAME || 
				groupKind == NameReferenceGroupKind.NRGK_ARRAY_ACCESS) {
			NameReference objectExpression = sublist.get(0);
			List<NameReference> objectReferenceList = objectExpression.getReferencesAtLeaf();
			for (NameReference objectReference : objectReferenceList) {
				List<ReachNameDefinition> rootReachNameList = ReachNameAnalyzer.getRootReachNameDefinitionList(currentCFG, node, objectReference);
				for (ReachNameDefinition rootReachName : rootReachNameList) {
					for (MethodCallExpressionRecorder info : infoList) {
						if (rootReachName.getNode() == info.node && rootReachName.getName() == info.leftValue) {
							info.isUsed = true;
						}
					}
				}
			}
		} else if (group.getGroupKind() == NameReferenceGroupKind.NRGK_METHOD_INVOCATION) {
			NameReference objectExpression = sublist.get(0);
			if (objectExpression.getReferenceKind() != NameReferenceKind.NRK_METHOD) {
				List<NameReference> objectReferenceList = objectExpression.getReferencesAtLeaf();
				for (NameReference objectReference : objectReferenceList) {
					List<ReachNameDefinition> rootReachNameList = ReachNameAnalyzer.getRootReachNameDefinitionList(currentCFG, node, objectReference);
					for (ReachNameDefinition rootReachName : rootReachNameList) {
						for (MethodCallExpressionRecorder info : infoList) {
							if (rootReachName.getNode() == info.node && rootReachName.getName() == info.leftValue) {
								info.isUsed = true;
							}
						}
					}
				}
			}
			return;
		}
		if (sublist != null) {
			for (NameReference subreference : sublist) extractLeftValueUsingInReference(currentCFG, infoList, node, subreference);
		}
	}
	
	public static List<MethodCallExpressionRecorder> collectMethodCallExpressionRecorderInReference(ExecutionPoint node, NameDefinition leftValue, NameReference reference) {
		List<MethodCallExpressionRecorder> result = new ArrayList<MethodCallExpressionRecorder>(); 
		if (reference == null) return result;

		List<NameReference> referenceAtLeafList = reference.getReferencesAtLeaf();
		
		if (referenceAtLeafList != null) {
			for (NameReference referenceAtLeaf : referenceAtLeafList) {
				if (referenceAtLeaf.getReferenceKind() == NameReferenceKind.NRK_METHOD) {
					MethodReference methodReference = (MethodReference)referenceAtLeaf;
					List<MethodDefinition> alternativeCalleeList = methodReference.getAlternativeList();
					if (alternativeCalleeList != null) {
						String expression = reference.toSimpleString();
						for (MethodDefinition callee : alternativeCalleeList) {
							MethodCallExpressionRecorder info = new MethodCallExpressionRecorder();
							info.node = node;
							info.callee = callee;
							info.expression = expression;
							info.leftValue = leftValue;
							info.reference = methodReference;
							result.add(info);
						}
					}
				}
			}
		}
		return result;
	}
}

class MethodCallExpressionRecorder {
	ExecutionPoint node = null;
	String expression = null;
	MethodReference reference = null;
	MethodDefinition callee = null;
	NameDefinition leftValue = null;
	boolean haveChecked = false;
	boolean isUsed = false;
}

