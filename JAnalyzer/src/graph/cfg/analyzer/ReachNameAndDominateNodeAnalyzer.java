package graph.cfg.analyzer;

import java.util.List;

import org.eclipse.jdt.core.dom.CompilationUnit;

import graph.basic.GraphNode;
import graph.cfg.ControlFlowGraph;
import graph.cfg.ExecutionPoint;
import graph.cfg.creator.CFGCreator;
import nameTable.NameTableManager;
import nameTable.nameDefinition.MethodDefinition;
import nameTable.nameScope.CompilationUnitScope;
import sourceCodeAST.CompilationUnitRecorder;

/**
 * @author Zhou Xiaocong
 * @since 2017Äê9ÔÂ9ÈÕ
 * @version 1.0
 *
 */
public class ReachNameAndDominateNodeAnalyzer {

	public static ControlFlowGraph create(NameTableManager nameTable, MethodDefinition method) {
		CompilationUnitScope unitScope = nameTable.getEnclosingCompilationUnitScope(method);
		if (unitScope == null) return null;
		String sourceFileName = unitScope.getUnitName();
		CompilationUnit astRoot = nameTable.getSouceCodeFileSet().findSourceCodeFileASTRootByFileUnitName(sourceFileName);
		if (astRoot == null) return null;
		CompilationUnitRecorder unitRecorder = new CompilationUnitRecorder(sourceFileName, astRoot);
		
		// Create a ControFlowGraph object
		ControlFlowGraph currentCFG = CFGCreator.create(nameTable, method);
		if (currentCFG == null) return null;

		setReachNameAndDominateNodeRecorder(currentCFG);
		ReachNameAnalyzer.reachNameAnalysis(nameTable, unitRecorder, method, currentCFG);
		DominateNodeAnalyzer.dominateNodeAnalysis(currentCFG, method);
		
		return currentCFG;
	}

	public static void setReachNameAndDominateNodeRecorder(ControlFlowGraph currentCFG) {
		List<GraphNode> nodeList = currentCFG.getAllNodes();
		for (GraphNode graphNode : nodeList) {
			if (graphNode instanceof ExecutionPoint) {
				ExecutionPoint node = (ExecutionPoint)graphNode;
				ReachNameAndDominateNodeRecorder recorder = new ReachNameAndDominateNodeRecorder();
				node.setFlowInfoRecorder(recorder);
			}
		}
	}
}
