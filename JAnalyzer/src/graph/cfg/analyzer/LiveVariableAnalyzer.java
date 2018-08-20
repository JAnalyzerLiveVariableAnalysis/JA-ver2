package graph.cfg.analyzer;

import java.awt.Frame;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.Assignment.Operator;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.core.util.PublicScanner;
import org.eclipse.osgi.container.SystemModule;
import graph.basic.GraphNode;
import graph.cfg.ControlFlowGraph;
import graph.cfg.ExecutionPoint;
import graph.cfg.ExecutionPointType;
import graph.cfg.creator.CFGCreator;
import nameTable.NameTableManager;
import nameTable.creator.ExpressionReferenceASTVisitor;
import nameTable.creator.NameDefinitionCreator;
import nameTable.creator.NameReferenceCreator;
import nameTable.filter.NameDefinitionLocationFilter;
import nameTable.filter.NameReferenceLocationFilter;
import nameTable.nameDefinition.MethodDefinition;
import nameTable.nameDefinition.NameDefinition;
import nameTable.nameDefinition.VariableDefinition;
import nameTable.nameReference.NameReference;
import nameTable.nameReference.NameReferenceKind;
import nameTable.nameReference.referenceGroup.NameReferenceGroup;
import nameTable.nameReference.referenceGroup.NameReferenceGroupKind;
import nameTable.nameScope.CompilationUnitScope;
import nameTable.nameScope.NameScope;
import nameTable.visitor.NameDefinitionVisitor;
import nameTable.visitor.NameReferenceVisitor;
import sourceCodeAST.CompilationUnitRecorder;
import sourceCodeAST.SourceCodeFileSet;
import sourceCodeAST.SourceCodeLocation;

public class LiveVariableAnalyzer {
	// create control flow graph with live variable analysis
	public static ControlFlowGraph create(NameTableManager nameTable, MethodDefinition method) {
		CompilationUnitScope unitScope = nameTable.getEnclosingCompilationUnitScope(method);
		if (unitScope == null) return null;
		String sourceFileName = unitScope.getUnitName();
		CompilationUnit astRoot = nameTable.getSouceCodeFileSet().findSourceCodeFileASTRootByFileUnitName(sourceFileName);
		if (astRoot == null) return null;
		CompilationUnitRecorder unitRecorder = new CompilationUnitRecorder(sourceFileName, astRoot);
		
		// Create a ControFlowGraph object
		ControlFlowGraph currentCFG = CFGCreator.create(nameTable, unitRecorder, method);
		if (currentCFG == null) return null;
		
		setLiveVariableRecorder(currentCFG);
		// live variable analysis
		liveVariableAnalysis(nameTable, unitRecorder, method, currentCFG);
		
		return currentCFG;
	}
	
	public static void setLiveVariableRecorder(ControlFlowGraph currentCFG) {
		List<GraphNode> nodeList = currentCFG.getAllNodes();
		for (GraphNode graphNode : nodeList) {
			if (graphNode instanceof ExecutionPoint) {
				ExecutionPoint node = (ExecutionPoint)graphNode;
				LiveVariableRecorder recorder = new LiveVariableRecorder();
				node.setFlowInfoRecorder(recorder);
			}
		}
	}
	
	public static void liveVariableAnalysis(NameTableManager manager, CompilationUnitRecorder unitRecorder, MethodDefinition method, ControlFlowGraph currentCFG) {
		// get Def[n] and Use[n] for for all nodes
		initializeDefAndUseVariableInAllNodes(manager, unitRecorder, method, currentCFG);
		// compute LiveIn[n] and LiveOut[n]
		boolean hasChanged = true;
		while (hasChanged) {
			hasChanged = false;
			List<GraphNode> graphNodes = currentCFG.getAllNodes();
			for (GraphNode graphNode : graphNodes) {
				ExecutionPoint currentNode = (ExecutionPoint)graphNode;
				ILiveVariableRecorder currentRecorder = (ILiveVariableRecorder)currentNode.getFlowInfoRecorder();
				
				// LiveInList is in[n]
				// LiveInList_ is in[n]'
				// first set them equal
				// if in[n] and in[n]' is different
				// need iterate again
				List<LiveVariableDefinition> LiveInList = currentRecorder.getLiveInVariableList();
				List<LiveVariableDefinition> LiveInList_ = new ArrayList<LiveVariableDefinition>();
				for (LiveVariableDefinition definition : LiveInList) {
					LiveInList_.add(definition);
				}
				
				currentRecorder.setLiveInEmpty();
				currentRecorder.setLiveOutEmpty();
				
				List<LiveVariableDefinition> UseList = currentRecorder.getUseVariableList();
				List<LiveVariableDefinition> DefList = currentRecorder.getDefVariableList();
				
				// out[n] = U in[s]
				List<GraphNode> successorsOfNode = currentCFG.adjacentFromNode(graphNode);
				for (GraphNode adjacentFromNode : successorsOfNode) {
					if (adjacentFromNode instanceof ExecutionPoint) {
						ExecutionPoint successorOfNode = (ExecutionPoint)adjacentFromNode;
						ILiveVariableRecorder successorRecorder = (ILiveVariableRecorder)successorOfNode.getFlowInfoRecorder();
						
						List<LiveVariableDefinition> successorLiveInList = successorRecorder.getLiveInVariableList();
						for (LiveVariableDefinition variable : successorLiveInList) {
							currentRecorder.addLiveOutVariable(variable);
						}
					}
				}
				
				// tempList is out[n]
				// in[n] = (out[n] - Def[n]) U Use[n]
				List<LiveVariableDefinition> tempList = currentRecorder.getLiveOutVariableList();
				for (LiveVariableDefinition temp : tempList) {
					currentRecorder.addLiveInVariable(temp);
				}
				
				for (LiveVariableDefinition Def : DefList) {
					currentRecorder.removeLiveInVariable(Def);
				}
				
				for (LiveVariableDefinition Use : UseList) {
					currentRecorder.addLiveInVariable(Use);
				}

				// if in[n] == in[n]'
				for (LiveVariableDefinition definition : LiveInList) {
					if (!currentRecorder.contains(LiveInList_, definition)) {
						hasChanged = true;
					}
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	// get Def[n] and Use[n] for all nodes
	static void initializeDefAndUseVariableInAllNodes(NameTableManager manager, CompilationUnitRecorder unitRecorder, MethodDefinition method, ControlFlowGraph currentCFG) {
		ExecutionPoint startNode = (ExecutionPoint)currentCFG.getStartNode(); 
		ILiveVariableRecorder recorder = (ILiveVariableRecorder)startNode.getFlowInfoRecorder();
		// Add parameter definition to the defined name list of the start node. Note that its reference for definition is NULL!
		List<VariableDefinition> parameterList = method.getParameterList();
		if (parameterList != null) {
			for (VariableDefinition parameter : parameterList) {
				recorder.addDefVariable(new LiveVariableDefinition(null, parameter, startNode));
			}
		}

		// Initialize defined name in node if its ASTNode is assignment, variable declaration, prefix or postfix expression (++, --) 
		List<GraphNode> nodeList = currentCFG.getAllNodes();
		for (GraphNode graphNode : nodeList) {
			ExecutionPoint node = (ExecutionPoint)graphNode;
			if (node.isStart()) continue;
			
			recorder = (ILiveVariableRecorder)node.getFlowInfoRecorder();
			
			ASTNode astNode = node.getAstNode();
			if (astNode == null) continue;
			
			SourceCodeLocation startLocation = node.getStartLocation();
			SourceCodeLocation endLocation = node.getEndLocation();
			int nodeType = astNode.getNodeType();
			if (nodeType == ASTNode.ASSIGNMENT) {
				Assignment assignment = (Assignment)astNode;
				Expression leftHandSide = assignment.getLeftHandSide();
				
				NameScope currentScope = manager.getScopeOfLocation(startLocation);
				NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
				ExpressionReferenceASTVisitor visitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
				leftHandSide.accept(visitor);
				NameReference leftReference = visitor.getResult();
				if (leftReference.resolveBinding()) {
					NameDefinition definition = extractLeftValueInReference(leftReference);
					
					Expression rightHandSide = assignment.getRightHandSide();
					visitor.reset();
					rightHandSide.accept(visitor);
					NameReference rightReference = visitor.getResult();
					
					rightReference.resolveBinding();
					recorder.addDefVariable(new LiveVariableDefinition(rightReference, definition, node));
					
					Operator operator = assignment.getOperator();
					if (operator == Operator.MINUS_ASSIGN || operator == Operator.PLUS_ASSIGN
							|| operator == Operator.TIMES_ASSIGN || operator == Operator.DIVIDE_ASSIGN) {
						recorder.addUseVariable(new LiveVariableDefinition(rightReference, definition, node));
					}
					
					List<LiveVariableDefinition> list = extractVariableFromReference(rightReference, node);
					for (LiveVariableDefinition variable1 : list) {
						recorder.addUseVariable(variable1);
					}
				}
			} else if (nodeType == ASTNode.VARIABLE_DECLARATION_EXPRESSION) {
				VariableDeclarationExpression variableDeclarationExpression = (VariableDeclarationExpression)astNode;
				
				NameDefinitionVisitor visitor = new NameDefinitionVisitor();
				NameDefinitionLocationFilter filter = new NameDefinitionLocationFilter(startLocation, endLocation);
				visitor.setFilter(filter);
				method.accept(visitor);
				List<NameDefinition> variableList = visitor.getResult();
				@SuppressWarnings("unchecked")
				List<VariableDeclarationFragment> fragmentList = variableDeclarationExpression.fragments();
				for (VariableDeclarationFragment fragment : fragmentList) {
					Expression initializer = fragment.getInitializer();
					if (initializer == null) continue;
					
					for (NameDefinition variable : variableList) {
						if (variable.getSimpleName().equals(fragment.getName().getIdentifier())) {
							NameScope currentScope = manager.getScopeOfLocation(startLocation);
							NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
							ExpressionReferenceASTVisitor referenceVisitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
							initializer.accept(referenceVisitor);
							NameReference valueReference = referenceVisitor.getResult();
							
							valueReference.resolveBinding();
							recorder.addDefVariable(new LiveVariableDefinition(valueReference, variable, node));
							List<LiveVariableDefinition> list = extractVariableFromReference(valueReference, node);
							for (LiveVariableDefinition variable1 : list) {
								recorder.addUseVariable(variable1);
							}
							break;
						}
					}
				}
			} else if (nodeType == ASTNode.VARIABLE_DECLARATION_FRAGMENT) {
				VariableDeclarationFragment fragment = (VariableDeclarationFragment)astNode;
				
				NameDefinitionVisitor visitor = new NameDefinitionVisitor();
				NameDefinitionLocationFilter filter = new NameDefinitionLocationFilter(startLocation, endLocation);
				visitor.setFilter(filter);
				method.accept(visitor);
				List<NameDefinition> variableList = visitor.getResult();
				
				Expression initializer = fragment.getInitializer();
				if (initializer == null) continue;
				
				for (NameDefinition variable : variableList) {
					if (variable.getSimpleName().equals(fragment.getName().getIdentifier())) {
						NameScope currentScope = manager.getScopeOfLocation(startLocation);
						NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
						ExpressionReferenceASTVisitor referenceVisitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
						initializer.accept(referenceVisitor);
						NameReference valueReference = referenceVisitor.getResult();
						
						valueReference.resolveBinding();
						recorder.addDefVariable(new LiveVariableDefinition(valueReference, variable, node));
						List<LiveVariableDefinition> list = extractVariableFromReference(valueReference, node);
						for (LiveVariableDefinition variable1 : list) {
							recorder.addUseVariable(variable1);
						}
						break;
					}
				}
			} else if (nodeType == ASTNode.VARIABLE_DECLARATION_STATEMENT) {
				VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement)astNode;
				
				NameDefinitionVisitor visitor = new NameDefinitionVisitor();
				NameDefinitionLocationFilter filter = new NameDefinitionLocationFilter(startLocation, endLocation);
				visitor.setFilter(filter);
				method.accept(visitor);
				List<NameDefinition> variableList = visitor.getResult();
				
				@SuppressWarnings("unchecked")
				List<VariableDeclarationFragment> fragmentList = variableDeclarationStatement.fragments();
				for (VariableDeclarationFragment fragment : fragmentList) {
					Expression initializer = fragment.getInitializer();
					if (initializer == null) continue;
					
					for (NameDefinition variable : variableList) {
						if (variable.getSimpleName().equals(fragment.getName().getIdentifier())) {
							NameScope currentScope = manager.getScopeOfLocation(startLocation);
							NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
							ExpressionReferenceASTVisitor referenceVisitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
							initializer.accept(referenceVisitor);
							NameReference valueReference = referenceVisitor.getResult();
							
							valueReference.resolveBinding();
							recorder.addDefVariable(new LiveVariableDefinition(valueReference, variable, node));
							List<LiveVariableDefinition> list = extractVariableFromReference(valueReference, node);
							for (LiveVariableDefinition variable1 : list) {
								recorder.addUseVariable(variable1);
							}
							break;
						}
					}
				}
			} else if (nodeType == ASTNode.METHOD_INVOCATION) {
				MethodInvocation methodInvocation = (MethodInvocation)astNode;
				Expression expression = methodInvocation.getExpression();
				Expression expression2 = (Expression)astNode;
				
				List<LiveVariableDefinition> variables = extractVariableFromExpression(expression, manager, startLocation, unitRecorder, node);
				for (LiveVariableDefinition variable : variables) {
					recorder.addUseVariable(variable);
				}
				NameScope currentScope = manager.getScopeOfLocation(startLocation);
				NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
				ExpressionReferenceASTVisitor visitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
				expression2.accept(visitor);
				NameReference reference = visitor.getResult();
				List<LiveVariableDefinition> list = extractVariableFromReference(reference, node);
				for (LiveVariableDefinition variable1 : list) {
					recorder.addUseVariable(variable1);
				}
			} else if (nodeType == ASTNode.PREFIX_EXPRESSION) {
				PrefixExpression prefix = (PrefixExpression)astNode;
				if (prefix.getOperator() == PrefixExpression.Operator.DECREMENT || prefix.getOperator() == PrefixExpression.Operator.INCREMENT) {
					Expression leftHandSide = prefix.getOperand();
					
					NameScope currentScope = manager.getScopeOfLocation(startLocation);
					NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
					ExpressionReferenceASTVisitor visitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
					leftHandSide.accept(visitor);
					NameReference leftReference = visitor.getResult();
					if (leftReference.resolveBinding()) {
						NameDefinition definition = leftReference.getDefinition();
						
						visitor.reset();
						prefix.accept(visitor);
						NameReference rightReference = visitor.getResult();
						
						rightReference.resolveBinding();
						recorder.addDefVariable(new LiveVariableDefinition(rightReference, definition, node));
						recorder.addUseVariable(new LiveVariableDefinition(rightReference, definition, node));
					}
				}
			} else if (nodeType == ASTNode.POSTFIX_EXPRESSION) {
				PostfixExpression postfix = (PostfixExpression)astNode;
				if (postfix.getOperator() == PostfixExpression.Operator.DECREMENT || postfix.getOperator() == PostfixExpression.Operator.INCREMENT) {
					Expression leftHandSide = postfix.getOperand();
					
					NameScope currentScope = manager.getScopeOfLocation(startLocation);
					NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
					ExpressionReferenceASTVisitor visitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
					leftHandSide.accept(visitor);
					NameReference leftReference = visitor.getResult();
					if (leftReference.resolveBinding()) {
						NameDefinition definition = leftReference.getDefinition();
						
						visitor.reset();
						postfix.accept(visitor);
						NameReference rightReference = visitor.getResult();

						rightReference.resolveBinding();
						recorder.addDefVariable(new LiveVariableDefinition(rightReference, definition, node));
						recorder.addUseVariable(new LiveVariableDefinition(rightReference, definition, node));
					}
				}
			} else if (nodeType == ASTNode.INFIX_EXPRESSION) {
				InfixExpression expression = (InfixExpression)astNode;
				Expression infix = (Expression)expression;
				List<LiveVariableDefinition> list = extractVariableFromExpression(infix, manager, startLocation, unitRecorder, node);
				for (LiveVariableDefinition variable : list) {
					recorder.addUseVariable(variable);
				}
			} else if (nodeType == ASTNode.ENHANCED_FOR_STATEMENT) {
				EnhancedForStatement enhancedForStatement = (EnhancedForStatement)astNode;
				SingleVariableDeclaration parameter = enhancedForStatement.getParameter();
				Expression expression = enhancedForStatement.getExpression();
				
				NameDefinitionVisitor visitor = new NameDefinitionVisitor();
				NameDefinitionLocationFilter filter = new NameDefinitionLocationFilter(startLocation, endLocation);
				visitor.setFilter(filter);
				method.accept(visitor);
				List<NameDefinition> variableList = visitor.getResult();
				
				ExecutionPoint prevNode = (ExecutionPoint)currentCFG.adjacentToNode(node).get(0);
				ILiveVariableRecorder prevRecorder = (ILiveVariableRecorder) prevNode.getFlowInfoRecorder();

				for (NameDefinition variable : variableList) {
					if (variable.getSimpleName().equals(parameter.getName().getIdentifier())) {
						NameScope currentScope = manager.getScopeOfLocation(startLocation);
						NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
						ExpressionReferenceASTVisitor referenceVisitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
						expression.accept(referenceVisitor);
						NameReference valueReference = referenceVisitor.getResult();
						
						valueReference.resolveBinding();
						prevRecorder.addDefVariable(new LiveVariableDefinition(valueReference, variable, node));
						List<LiveVariableDefinition> list = extractVariableFromReference(valueReference, node);
						for (LiveVariableDefinition variable1 : list) {
							prevRecorder.addUseVariable(variable1);
						}
						break;
					}
				}
			} else if (nodeType == ASTNode.FOR_STATEMENT) {
				ForStatement forStatement = (ForStatement)astNode;
				Expression expression = forStatement.getExpression();
				ExecutionPoint prevNode = (ExecutionPoint)currentCFG.adjacentToNode(node).get(0);
				ILiveVariableRecorder prevRecorder = (ILiveVariableRecorder) prevNode.getFlowInfoRecorder();
				List<LiveVariableDefinition> list = extractVariableFromExpression(expression, manager, startLocation, unitRecorder, node);
				for (LiveVariableDefinition variable : list) {
					prevRecorder.addUseVariable(variable);
				}
			} else if (nodeType == ASTNode.RETURN_STATEMENT) {
				ReturnStatement statement = (ReturnStatement)astNode;
				Expression expression = statement.getExpression();
				List<LiveVariableDefinition> list = extractVariableFromExpression(expression, manager, startLocation, unitRecorder, node);
				for (LiveVariableDefinition variable : list) {
					recorder.addUseVariable(variable);
				}
			}
		}
	}
	
	public static NameDefinition extractLeftValueInReference(NameReference reference) {
		if (!reference.isGroupReference()) return reference.getDefinition();
		NameReferenceGroup group = (NameReferenceGroup)reference;
		NameReferenceGroupKind groupKind = group.getGroupKind();
		List<NameReference> sublist = group.getSubReferenceList();
		
		if (groupKind == NameReferenceGroupKind.NRGK_ARRAY_ACCESS) {
			// Find the array name in this reference!
			NameReference firstSubReference = sublist.get(0);
			while (firstSubReference.isGroupReference()) {
				sublist = firstSubReference.getSubReferenceList();
				firstSubReference = sublist.get(0);
			}
			return firstSubReference.getDefinition();
		} else if (groupKind == NameReferenceGroupKind.NRGK_FIELD_ACCESS) {
			return sublist.get(1).getDefinition();
		} else if (groupKind == NameReferenceGroupKind.NRGK_METHOD_INVOCATION ||
				groupKind == NameReferenceGroupKind.NRGK_SUPER_METHOD_INVOCATION) {
			for (NameReference subreference : sublist) {
				if (subreference.getReferenceKind() == NameReferenceKind.NRK_METHOD) {
					return subreference.getDefinition();
				}
			}
		} else if (groupKind == NameReferenceGroupKind.NRGK_SUPER_FIELD_ACCESS) {
			NameReference firstReference = sublist.get(0);
			if (firstReference.getReferenceKind() == NameReferenceKind.NRK_TYPE) 
				return sublist.get(1).getDefinition();
			else return firstReference.getDefinition();
		} else if (groupKind == NameReferenceGroupKind.NRGK_THIS_EXPRESSION) {
			return sublist.get(0).getDefinition();
		} else if (groupKind == NameReferenceGroupKind.NRGK_QUALIFIED_NAME) {
			return group.getDefinition();
		}
		return null;
	}
	
	public static List<NameReference> extractGroupReference(NameReference groupReference) {
		List<NameReference> references = new ArrayList<NameReference>();
		if (groupReference.isGroupReference()) {
			List<NameReference> group = groupReference.getReferencesAtLeaf();
			for (NameReference reference : group) {
				if (reference.isLiteralReference() || reference.isNullReference()
						|| reference.isTypeReference() || reference.isMethodReference()) {
					
				} else {
					references.add(reference);
				}
			}
		} else {
			if (groupReference.isLiteralReference() || groupReference.isMethodReference()
					|| groupReference.isNullReference() || groupReference.isTypeReference()) {
				
			} else {
				references.add(groupReference);
			}
		}
		return references;
	}
	
	public static List<LiveVariableDefinition> extractVariableFromExpression(Expression expression, NameTableManager manager,
			SourceCodeLocation startLocation, CompilationUnitRecorder unitRecorder, ExecutionPoint node) {
		List<LiveVariableDefinition> list = new ArrayList<LiveVariableDefinition>();
		if (expression == null)
			return list;
		NameScope currentScope = manager.getScopeOfLocation(startLocation);
		NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
		ExpressionReferenceASTVisitor visitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
		expression.accept(visitor);
		NameReference reference = visitor.getResult();
		List<NameReference> references = extractGroupReference(reference);
		for (NameReference reference2 : references) {
			if (reference2.resolveBinding()) {
				list.add(new LiveVariableDefinition(reference2, reference2.getDefinition(), node));
			}
		}
		return list;
	}

	public static LiveVariableDefinition extractVariableFromFragment(VariableDeclarationFragment fragment, NameTableManager manager,
			SourceCodeLocation startLocation, CompilationUnitRecorder unitRecorder, ExecutionPoint node) {
		LiveVariableDefinition variableDefinition = null;
		NameScope currentScope = manager.getScopeOfLocation(startLocation);
		NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
		ExpressionReferenceASTVisitor visitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);

		fragment.accept(visitor);
		NameReference reference = visitor.getResult();
		List<NameReference> list = reference.getReferencesAtLeaf();
		System.out.println(list);
		for (NameReference nameReference : list) {
			System.out.println("reference : " + nameReference.getName());
			System.out.println("name : " + fragment.getName());
			if (nameReference.getName().equals(fragment.getName().toString())) {
				if (nameReference.resolveBinding()) {
					variableDefinition = new LiveVariableDefinition(nameReference, nameReference.getDefinition(), node);
					return variableDefinition;
				}
			}
		}
		return null;
	}
	
	public static List<LiveVariableDefinition> extractVariableFromReference(NameReference valueReference, ExecutionPoint node) {
		List<LiveVariableDefinition> varables = new ArrayList<LiveVariableDefinition>();
		List<NameReference> references = valueReference.getReferencesAtLeaf();
		for (NameReference reference :references) {
			if (!reference.isTypeReference() && !reference.isLiteralReference() &&
					!reference.isMethodReference() && !reference.isNullReference()) {
				if (reference.resolveBinding()) {
					NameDefinition definition1 = reference.getDefinition();
					varables.add(new LiveVariableDefinition(reference, definition1, node));
				}
			}
		}
		return varables;
	}
	
	public static String outPutAllInfo(ControlFlowGraph controlFlowGraph) {
		List<GraphNode> nodeList = controlFlowGraph.getAllNodes();
		String result = "";
		
		for (GraphNode node : nodeList) {
			if (node instanceof ExecutionPoint) {
				String output = "";
				ExecutionPoint graphNode = (ExecutionPoint)node;
				LiveVariableRecorder recorder = (LiveVariableRecorder)graphNode.getFlowInfoRecorder();
				
				List<LiveVariableDefinition> LiveInList = recorder.getLiveInVariableList();
				List<LiveVariableDefinition> LiveOutList = recorder.getLiveOutVariableList();
				List<LiveVariableDefinition> DefList = recorder.getDefVariableList();
				List<LiveVariableDefinition> UseList = recorder.getUseVariableList();
				
				result += graphNode.toFullString() + "\n";
				
				output += "Defined variable  : ";
				for (LiveVariableDefinition definition : DefList) {
					NameDefinition definition2 = definition.getDefinition();
					output += definition2.getFullQualifiedName() + " ";
				}
				output += "\n";
				
				output += "Used variable     : ";
				for (LiveVariableDefinition definition : UseList) {
					NameDefinition definition2 = definition.getDefinition();
					output += definition2.getFullQualifiedName() + " ";
				}
				output += "\n";
				
				output += "Variables liveIn  : "; 
				for (LiveVariableDefinition definition : LiveInList) {
					NameDefinition definition2 = definition.getDefinition();
					output += definition2.getFullQualifiedName() + " ";
				}
				output += "\n";
				
				output += "Variables liveOut : ";
				for (LiveVariableDefinition definition : LiveOutList) {
					NameDefinition definition2 = definition.getDefinition();
					output += definition2.getFullQualifiedName() + " ";
				}
				output += "\n";
				
				graphNode.setLabel("\n" + output + "\n");
				result += graphNode.getLabel();
			}
		}
		
		return result;
	}
}
