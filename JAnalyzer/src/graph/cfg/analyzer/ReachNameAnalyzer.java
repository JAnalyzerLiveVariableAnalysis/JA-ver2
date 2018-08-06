package graph.cfg.analyzer;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import graph.basic.GraphNode;
import graph.cfg.ControlFlowGraph;
import graph.cfg.ExecutionPoint;
import graph.cfg.creator.CFGCreator;
import nameTable.NameTableManager;
import nameTable.creator.ExpressionReferenceASTVisitor;
import nameTable.creator.NameReferenceCreator;
import nameTable.filter.NameDefinitionLocationFilter;
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
import sourceCodeAST.CompilationUnitRecorder;
import sourceCodeAST.SourceCodeLocation;

/**
 * Create CFG for a method, and the node of CFG has reach name information, that is those names and their assigned 
 * values can be used in the current node.  
 * 
 * @author Zhou Xiaocong
 * @since 2017Äê9ÔÂ7ÈÕ
 * @version 1.0
 *
 */
public class ReachNameAnalyzer {

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
		
		setReachNameRecorder(currentCFG);
		reachNameAnalysis(nameTable, unitRecorder, method, currentCFG);
		return currentCFG;
	}
	
	public static void setReachNameRecorder(ControlFlowGraph currentCFG) {
		List<GraphNode> nodeList = currentCFG.getAllNodes();
		for (GraphNode graphNode : nodeList) {
			if (graphNode instanceof ExecutionPoint) {
				ExecutionPoint node = (ExecutionPoint)graphNode;
				ReachNameRecorder recorder = new ReachNameRecorder();
				node.setFlowInfoRecorder(recorder);
			}
		}
	}
	
	public static void reachNameAnalysis(NameTableManager manager, CompilationUnitRecorder unitRecorder, MethodDefinition method, ControlFlowGraph currentCFG) {
		initializeDefinedNameInAllNodes(manager, unitRecorder, method, currentCFG);

		// Need an iterative process to deal with the defined names in a loop
		boolean hasChanged = true;
		while (hasChanged) {
			hasChanged = false;
			List<GraphNode> graphNodeList = currentCFG.getAllNodes();
			for (GraphNode graphNode : graphNodeList) {
				ExecutionPoint currentNode = (ExecutionPoint)graphNode;
				IReachNameRecorder currentRecorder = (IReachNameRecorder)currentNode.getFlowInfoRecorder();
				
				List<GraphNode> adjacentToNodeList = currentCFG.adjacentToNode(graphNode);
				for (GraphNode adjacentToNode : adjacentToNodeList) {
					if (adjacentToNode instanceof ExecutionPoint) {
						ExecutionPoint precedeNode = (ExecutionPoint)adjacentToNode;
						IReachNameRecorder precedeRecorder = (IReachNameRecorder)precedeNode.getFlowInfoRecorder();
						
						List<ReachNameDefinition> precedeDefinedNameList = precedeRecorder.getReachNameList();
						// Note that all generated names by this node also kill the names reach the precede nodes.
						List<ReachNameDefinition> killedNameList = currentRecorder.getGeneratedNameList();
						for (ReachNameDefinition precedeDefinedName : precedeDefinedNameList) {
							boolean killed = false;
							for (ReachNameDefinition killedName : killedNameList) {
								if (killedName.getName() == precedeDefinedName.getName()) {
									killed = true;
									break;
								}
							}
							if (!killed) {
								NameScope precedeNameScope = precedeDefinedName.getName().getScope();
								SourceCodeLocation currentLocation = currentNode.getStartLocation(); 
								// Check whether the name scope of precede defined name contains the location of end node. If not, it means
								// the precede defined name can not be accessed at the location of end node.
								if (precedeNameScope.containsLocation(currentLocation)) {
									if (currentRecorder.addReachName(precedeDefinedName)) {
										// There is at least a node changing its reached defined name.
										hasChanged = true;
									}
								}
							}
						}
					}
				}
			}
		}
	}
	
	static void initializeDefinedNameInAllNodes(NameTableManager manager, CompilationUnitRecorder unitRecorder, MethodDefinition method, ControlFlowGraph currentCFG) {
		ExecutionPoint startNode = (ExecutionPoint)currentCFG.getStartNode(); 
		IReachNameRecorder recorder = (IReachNameRecorder)startNode.getFlowInfoRecorder();
		// Add parameter definition to the defined name list of the start node. Note that its reference for definition is NULL!
		List<VariableDefinition> parameterList = method.getParameterList();
		if (parameterList != null) {
			for (VariableDefinition parameter : parameterList) {
				recorder.addReachName(new ReachNameDefinition(startNode, parameter, null));
			}
		}
		
		// Initialize defined name in node if its ASTNode is assignment, variable declaration, prefix or postfix expression (++, --) 
		List<GraphNode> nodeList = currentCFG.getAllNodes();
		for (GraphNode graphNode : nodeList) {
			ExecutionPoint node = (ExecutionPoint)graphNode;
			if (node.isStart()) continue;
			
			recorder = (IReachNameRecorder)node.getFlowInfoRecorder();
			
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
					ReachNameDefinition definedName = new ReachNameDefinition(node, definition, rightReference); 
					recorder.addReachName(definedName);
					// This execution point generated this defined name, and also kills previous definition of this binded 
					// local variable, parameter or field in the precede nodes.
					recorder.addGeneratedName(definedName);
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

				boolean found = false;
				for (NameDefinition variable : variableList) {
					if (variable.getSimpleName().equals(parameter.getName().getIdentifier())) {
						found = true;
						// Find the definition of this variable declaration fragment, so this execution point generated a defined name 
						// for this variable, and kill all previous definition of this variable in the precede nodes

						// Create reference for its initializer expression, and add the definition of this variable to definedNameList of 
						// this execution point
						NameScope currentScope = manager.getScopeOfLocation(startLocation);
						NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
						ExpressionReferenceASTVisitor referenceVisitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
						expression.accept(referenceVisitor);
						NameReference valueReference = referenceVisitor.getResult();
						
						valueReference.resolveBinding();
						ReachNameDefinition definedName = new ReachNameDefinition(node, variable, valueReference); 
						recorder.addReachName(definedName);
						// Generate this defined name and kill all previous definition in the precede node! 
						recorder.addGeneratedName(definedName);
						break;
					}
				}
				if (!found) {
					throw new AssertionError("Can not find variable definition for enhanced for parameter: " + parameter.toString() + " at " + startLocation.getUniqueId());
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
					// This variable has not been initialized, that is, it is not defined!
					if (initializer == null) continue;
					
					boolean found = false;
					for (NameDefinition variable : variableList) {
						if (variable.getSimpleName().equals(fragment.getName().getIdentifier())) {
							found = true;
							// Find the definition of this variable declaration fragment, so this execution point generated a defined name 
							// for this variable, and kill all previous definition of this variable in the precede nodes

							// Create reference for its initializer expression, and add the definition of this variable to definedNameList of 
							// this execution point
							NameScope currentScope = manager.getScopeOfLocation(startLocation);
							NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
							ExpressionReferenceASTVisitor referenceVisitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
							initializer.accept(referenceVisitor);
							NameReference valueReference = referenceVisitor.getResult();
							
							valueReference.resolveBinding();
							ReachNameDefinition definedName = new ReachNameDefinition(node, variable, valueReference); 
							recorder.addReachName(definedName);
							// Generate this defined name and kill all previous definition in the precede node! 
							recorder.addGeneratedName(definedName);
							
							break;
						}
					}
					if (!found) {
						throw new AssertionError("Can not find variable definition for variable declaration: " + fragment.toString() + " at " + startLocation.getUniqueId());
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
				// This variable has not been initialized, that is, it is not defined!
				if (initializer == null) continue;
				
				boolean found = false;
				for (NameDefinition variable : variableList) {
					if (variable.getSimpleName().equals(fragment.getName().getIdentifier())) {
						found = true;
						// Find the definition of this variable declaration fragment, so this execution point generated a defined name 
						// for this variable, and kill all previous definition of this variable in the precede nodes

						// Create reference for its initializer expression, and add the definition of this variable to definedNameList of 
						// this execution point
						NameScope currentScope = manager.getScopeOfLocation(startLocation);
						NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
						ExpressionReferenceASTVisitor referenceVisitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
						initializer.accept(referenceVisitor);
						NameReference valueReference = referenceVisitor.getResult();
						
						valueReference.resolveBinding();
						ReachNameDefinition definedName = new ReachNameDefinition(node, variable, valueReference); 
						recorder.addReachName(definedName);
						// Generate this defined name and kill all previous definition in the precede node! 
						recorder.addGeneratedName(definedName);
						
						break;
					}
				}
				if (!found) {
					throw new AssertionError("Can not find variable definition for variable declaration: " + fragment.toString() + " at " + startLocation.getUniqueId());
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
					// This variable has not been initialized, that is, it is not defined!
					if (initializer == null) continue;
					
					boolean found = false;
					for (NameDefinition variable : variableList) {
						if (variable.getSimpleName().equals(fragment.getName().getIdentifier())) {
							found = true;
							// Find the definition of this variable declaration fragment, so this execution point generated a defined name 
							// for this variable, and kill all previous definition of this variable in the precede nodes

							// Create reference for its initializer expression, and add the definition of this variable to definedNameList of 
							// this execution point
							NameScope currentScope = manager.getScopeOfLocation(startLocation);
							NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
							ExpressionReferenceASTVisitor referenceVisitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
							initializer.accept(referenceVisitor);
							NameReference valueReference = referenceVisitor.getResult();
							
							valueReference.resolveBinding();
							ReachNameDefinition definedName = new ReachNameDefinition(node, variable, valueReference); 
							recorder.addReachName(definedName);
							// Generate this defined name and kill all previous definition in the precede node! 
							recorder.addGeneratedName(definedName);
							
							break;
						}
					}
					if (!found) {
						throw new AssertionError("Can not find variable definition for variable declaration: " + fragment.toString() + " at " + startLocation.getUniqueId());
					}
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
						ReachNameDefinition definedName = new ReachNameDefinition(node, definition, rightReference); 
						recorder.addReachName(definedName);
						// Generate this defined name and kill all previous definition in the precede node! 
						recorder.addGeneratedName(definedName);
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
						ReachNameDefinition definedName = new ReachNameDefinition(node, definition, rightReference); 
						recorder.addReachName(definedName);
						// Generate this defined name and kill all previous definition in the precede node! 
						recorder.addGeneratedName(definedName);
					}
				}
			}
		}
	}
	
	public static List<ReachNameDefinition> getReachNameDefinitionList(ControlFlowGraph currentCFG, ExecutionPoint node, NameReference reference) {
		List<ReachNameDefinition> result = new ArrayList<ReachNameDefinition>();
		
		NameDefinition definition = reference.getDefinition();
		if (definition == null) return result;
		
		IReachNameRecorder recorder = (IReachNameRecorder)node.getFlowInfoRecorder();
		List<ReachNameDefinition> definedNameList = recorder.getReachNameList();
		for (ReachNameDefinition definedName : definedNameList) {
			if (definedName.getName() == definition) result.add(definedName);
		}
		
		return result;
	}
	
	public static List<ReachNameDefinition> getRootReachNameDefinitionList(ControlFlowGraph currentCFG, ExecutionPoint node, NameReference reference) {
		List<ReachNameDefinition> exploredNameList = new ArrayList<ReachNameDefinition>();

		return exploreRootReachNameDefinitionList(exploredNameList, node, reference);
	}
	
	public static List<ReachNameDefinition> exploreRootReachNameDefinitionList(List<ReachNameDefinition> exploredNameList, ExecutionPoint node, NameReference reference) {
		List<ReachNameDefinition> result = new ArrayList<ReachNameDefinition>();
		
		NameDefinition definition = extractLeftValueInReference(reference);
		if (definition == null) return result;
		
		IReachNameRecorder recorder = (IReachNameRecorder)node.getFlowInfoRecorder();
		List<ReachNameDefinition> definedNameList = recorder.getReachNameList();
		for (ReachNameDefinition definedName : definedNameList) {
			if (exploredNameList.contains(definedName)) continue;
			
			if (definedName.getName() == definition) {
				exploredNameList.add(definedName);
				
				NameReference valueReference = definedName.getValue();
					
				NameReference propagatedReference = extractPropagableReference(valueReference);
				if (propagatedReference != null) {
					List<ReachNameDefinition> propagatedReachNameList = exploreRootReachNameDefinitionList(exploredNameList, definedName.getNode(), propagatedReference);
					result.addAll(propagatedReachNameList);
				} else result.add(definedName);
			}
		}
		return result;
	}

	public static NameReference extractPropagableReference(NameReference reference) {
		if (reference == null) return null;
		
		NameReference result = null;
		NameReferenceKind kind = reference.getReferenceKind();
		if (kind == NameReferenceKind.NRK_FIELD || kind == NameReferenceKind.NRK_VARIABLE) {
			result =  reference;
		} else if (reference.isGroupReference()) {
			NameReferenceGroup group = (NameReferenceGroup)reference;
			NameReferenceGroupKind groupKind = group.getGroupKind();
			if (groupKind == NameReferenceGroupKind.NRGK_CAST) {
				List<NameReference> subreferenceList = group.getSubReferenceList();
				if (!subreferenceList.get(1).isGroupReference()) result = subreferenceList.get(1);
			} else if (groupKind == NameReferenceGroupKind.NRGK_FIELD_ACCESS || groupKind == NameReferenceGroupKind.NRGK_QUALIFIED_NAME) {
				result = reference;
			}
		}
		return result;
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
}
