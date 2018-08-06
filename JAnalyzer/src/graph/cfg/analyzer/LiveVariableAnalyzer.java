package graph.cfg.analyzer;

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
import org.eclipse.osgi.container.SystemModule;
import graph.basic.GraphNode;
import graph.cfg.ControlFlowGraph;
import graph.cfg.ExecutionPoint;
import graph.cfg.ExecutionPointType;
import graph.cfg.creator.CFGCreator;
import nameTable.NameTableManager;
import nameTable.creator.ExpressionReferenceASTVisitor;
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
					if (!isContain(LiveInList_, definition)) {
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
		
		List<VariableDefinition> parameterList = method.getParameterList();
		
		// parameter是传递的参数
		if (parameterList != null) {
			for (VariableDefinition parameter : parameterList) {
				recorder.addDefVariable(new LiveVariableDefinition(parameter.getSimpleName()));
			}
		}
		
		// get Def[node]
		List<GraphNode> nodeList = currentCFG.getAllNodes();
		for (GraphNode graphNode : nodeList) {
			ExecutionPoint node = (ExecutionPoint)graphNode;
			if (node.isStart()) continue;
			
			recorder = (ILiveVariableRecorder)node.getFlowInfoRecorder();
			
			ASTNode astNode = node.getAstNode();
			if (astNode == null) continue;
			
			SourceCodeLocation startLocation = node.getStartLocation();
			
			int nodeType = astNode.getNodeType();
			
			if (nodeType == ASTNode.ASSIGNMENT) {
				Assignment assignment = (Assignment)astNode;
				Expression leftHandSide = assignment.getLeftHandSide();
				Expression rightHandSide = assignment.getRightHandSide();
				Operator operator = assignment.getOperator();
				
				NameReference leftReference = extractNameReferenceFromExpression(leftHandSide, manager, unitRecorder, startLocation);
				NameReference rightReference = extractNameReferenceFromExpression(rightHandSide, manager, unitRecorder, startLocation);
				
				if (operator == Operator.PLUS_ASSIGN || operator == Operator.MINUS_ASSIGN) {
					recorder.addDefVariable(new LiveVariableDefinition(leftReference.getName()));
					recorder.addUseVariable(new LiveVariableDefinition(leftReference.getName()));
				} else {
					recorder.addDefVariable(new LiveVariableDefinition(leftReference.getName()));
				}
				
				List<String> variables = extractVariablesFromNameReference(rightReference);
				for (String variable : variables) {
					recorder.addUseVariable(new LiveVariableDefinition(variable));
				}
			} else if (nodeType == ASTNode.METHOD_INVOCATION) {
				MethodInvocation invocation = (MethodInvocation)astNode;
				List<Expression> arguments = invocation.arguments();
				for (Expression argument : arguments) {
					NameReference reference = extractNameReferenceFromExpression(argument, manager, unitRecorder, startLocation);
					List<String> variables = extractVariablesFromNameReference(reference);
					for (String variable : variables) {
						recorder.addUseVariable(new LiveVariableDefinition(variable));
					}
				}
			} else if (nodeType == ASTNode.RETURN_STATEMENT) {
				ReturnStatement statement = (ReturnStatement)astNode;
				Expression expression = statement.getExpression();
				
				NameReference reference = extractNameReferenceFromExpression(expression, manager, unitRecorder, startLocation);
				List<String> variables = extractVariablesFromNameReference(reference);
				for (String variable : variables) {
					recorder.addUseVariable(new LiveVariableDefinition(variable));
				}
			} else if (nodeType == ASTNode.ENHANCED_FOR_STATEMENT) {
				EnhancedForStatement statement = (EnhancedForStatement)astNode;
				Expression expression = statement.getExpression();
				SingleVariableDeclaration declaration = statement.getParameter();
				
				NameReference reference = extractNameReferenceFromExpression(expression, manager, unitRecorder, startLocation);
				GraphNode forPredicate = (currentCFG.adjacentToNode(node)).get(0);
				ExecutionPoint executionPoint = (ExecutionPoint)forPredicate;
				recorder = (ILiveVariableRecorder)executionPoint.getFlowInfoRecorder();
				recorder.addUseVariable(new LiveVariableDefinition(reference.getName()));
				
				recorder.addDefVariable(new LiveVariableDefinition(declaration.getName().toString()));
			} else if (nodeType == ASTNode.INFIX_EXPRESSION) {
				InfixExpression infixExpression = (InfixExpression)astNode;
				List<String> Vars = extractVariableFromInfixExpression(infixExpression);
				for(String Var : Vars) {
					recorder.addUseVariable(new LiveVariableDefinition(Var));
				}
			} else if (nodeType == ASTNode.PREFIX_EXPRESSION) {
				PrefixExpression expression = (PrefixExpression)astNode;
				Expression var = expression.getOperand();
				recorder.addDefVariable(new LiveVariableDefinition(var.toString()));
				recorder.addUseVariable(new LiveVariableDefinition(var.toString()));
			} else if (nodeType == ASTNode.POSTFIX_EXPRESSION) {
				PostfixExpression expression = (PostfixExpression)astNode;
				Expression var = expression.getOperand();
				recorder.addDefVariable(new LiveVariableDefinition(var.toString()));
				recorder.addUseVariable(new LiveVariableDefinition(var.toString()));
			} else if (nodeType == ASTNode.VARIABLE_DECLARATION_EXPRESSION) {
				VariableDeclarationExpression expression = (VariableDeclarationExpression)astNode;
				extractFragment(recorder, expression.fragments(), startLocation, manager, unitRecorder);
			} else if (nodeType == ASTNode.VARIABLE_DECLARATION_FRAGMENT) {
				VariableDeclarationFragment fragment = (VariableDeclarationFragment)astNode;
				List<VariableDeclarationFragment> fragments = new ArrayList<VariableDeclarationFragment>();
				fragments.add(fragment);
				extractFragment(recorder, fragments, startLocation, manager, unitRecorder);
			} else if (nodeType == ASTNode.VARIABLE_DECLARATION_STATEMENT) {
				VariableDeclarationStatement statement = (VariableDeclarationStatement)astNode;
				extractFragment(recorder, statement.fragments(), startLocation, manager, unitRecorder);
			}
		}
	}

	// output formatted Def and Use variable for all execution point
	public static String outPutDefAndUseVariable(ControlFlowGraph controlFlowGraph) {
		List<GraphNode> nodeList = controlFlowGraph.getAllNodes();
		String output = "";
		
		for (GraphNode node : nodeList) {
			if (node instanceof ExecutionPoint) {
				ExecutionPoint graphNode = (ExecutionPoint)node;
				LiveVariableRecorder recorder = (LiveVariableRecorder)graphNode.getFlowInfoRecorder();
				
				List<LiveVariableDefinition> DefList = recorder.getDefVariableList();
				List<LiveVariableDefinition> UseList = recorder.getUseVariableList();
				
				output += graphNode.toFullString() + "\n";
				output += "Def : "; 
				for (LiveVariableDefinition definition : DefList) {
					output += definition.getVariable() + " ";
				}
				output += "\n";
				
				output += "Use : ";
				for (LiveVariableDefinition definition : UseList) {
					output += definition.getVariable() + " ";
				}
				output += "\n";
			}
		}
		
		return output;
	}
	
	
	// output formatted LiveIn and LiveOut variable for all execution point
	public static String outPutLiveInAndOutVariable(ControlFlowGraph controlFlowGraph) {
		List<GraphNode> nodeList = controlFlowGraph.getAllNodes();
		String output = "";
		
		for (GraphNode node : nodeList) {
			if (node instanceof ExecutionPoint) {
				ExecutionPoint graphNode = (ExecutionPoint)node;
				LiveVariableRecorder recorder = (LiveVariableRecorder)graphNode.getFlowInfoRecorder();
				
				List<LiveVariableDefinition> LiveInList = recorder.getLiveInVariableList();
				List<LiveVariableDefinition> LiveOutList = recorder.getLiveOutVariableList();
				
				output += graphNode.toFullString() + "\n";
				output += "LiveIn : "; 
				for (LiveVariableDefinition definition : LiveInList) {
					output += definition.getVariable() + " ";
				}
				output += "\n";
				
				output += "LiveOut : ";
				for (LiveVariableDefinition definition : LiveOutList) {
					output += definition.getVariable() + " ";
				}
				output += "\n";
			}
		}
		
		return output;
	}
	
	
	public static String outPutAllInfo(ControlFlowGraph controlFlowGraph) {
		List<GraphNode> nodeList = controlFlowGraph.getAllNodes();
		String output = "";
		
		for (GraphNode node : nodeList) {
			if (node instanceof ExecutionPoint) {
				ExecutionPoint graphNode = (ExecutionPoint)node;
				LiveVariableRecorder recorder = (LiveVariableRecorder)graphNode.getFlowInfoRecorder();
				
				List<LiveVariableDefinition> LiveInList = recorder.getLiveInVariableList();
				List<LiveVariableDefinition> LiveOutList = recorder.getLiveOutVariableList();
				List<LiveVariableDefinition> DefList = recorder.getDefVariableList();
				List<LiveVariableDefinition> UseList = recorder.getUseVariableList();
				
				output += graphNode.toFullString() + "\n";
				
				output += "Def :     ";
				for (LiveVariableDefinition definition : DefList) {
					output += definition.getVariable() + " ";
				}
				output += "\n";
				
				output += "Use :     ";
				for (LiveVariableDefinition definition : UseList) {
					output += definition.getVariable() + " ";
				}
				output += "\n";
				
				output += "LiveIn :  "; 
				for (LiveVariableDefinition definition : LiveInList) {
					output += definition.getVariable() + " ";
				}
				output += "\n";
				
				output += "LiveOut : ";
				for (LiveVariableDefinition definition : LiveOutList) {
					output += definition.getVariable() + " ";
				}
				output += "\n";
			}
		}
		
		return output;
	}
	
	static List<String> extractVariablesFromNameReference(NameReference reference) {
		List<String> variables = new ArrayList<String>();
		if (reference.isGroupReference()) {
			List<NameReference> subList = reference.getSubReferenceList();
			for (NameReference subReference : subList) {
				if (subReference.isGroupReference()) {
					variables.addAll(extractVariablesFromNameReference(subReference));
				} else {
					NameReferenceKind kind = subReference.getReferenceKind();
					if (kind == NameReferenceKind.NRK_VARIABLE) {
						variables.add(subReference.getName());
					} else if (kind == NameReferenceKind.NRK_METHOD) {
						List<NameReference> leafs = subReference.getReferencesAtLeaf();
						for (NameReference leaf : leafs) {
							if (leaf.getReferenceKind() == NameReferenceKind.NRK_VARIABLE) {
								variables.add(leaf.getName());
							}
						}
					}
				}
			}
		} else {
			if (reference.getReferenceKind() == NameReferenceKind.NRK_VARIABLE) {
				variables.add(reference.getName());
			}
		}
		return variables;
	}
	
	static NameReference extractNameReferenceFromExpression(Expression expression, NameTableManager manager, CompilationUnitRecorder unitRecorder, SourceCodeLocation startLocation) {
		NameScope currentScope = manager.getScopeOfLocation(startLocation);
		NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
		ExpressionReferenceASTVisitor visitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
		expression.accept(visitor);
		NameReference reference = visitor.getResult();
		return reference;
	}
	
	static boolean isContain(List<LiveVariableDefinition> list, LiveVariableDefinition definition) {
		for (LiveVariableDefinition definition2 : list) {
			if (definition.getVariable().equals(definition2.getVariable()))
				return true;
		}
		return false;
	}
	
	static List<String> extractVariableFromInfixExpression(InfixExpression expression) {
		List<String> vars = new ArrayList<String>();
		Expression leftSide = expression.getLeftOperand();
		Expression rightSide = expression.getRightOperand();
		
		if (!isLiteral(leftSide) && leftSide.getNodeType() != ASTNode.SIMPLE_NAME) {
			int type = leftSide.getNodeType();
			if (type == ASTNode.INFIX_EXPRESSION) {
				InfixExpression leftExpression = (InfixExpression)leftSide;
				vars.addAll(extractVariableFromInfixExpression(leftExpression));
			}
		} else if (!isLiteral(leftSide) && leftSide.getNodeType() == ASTNode.SIMPLE_NAME) {
			vars.add(leftSide.toString());
		}
		
		if (!isLiteral(rightSide) && rightSide.getNodeType() != ASTNode.SIMPLE_NAME) {
			int type = rightSide.getNodeType();
			if (type == ASTNode.INFIX_EXPRESSION) {
				InfixExpression rightExpression = (InfixExpression)rightSide;
				vars.addAll(extractVariableFromInfixExpression(rightExpression));
			}
		} else if (!isLiteral(rightSide) && rightSide.getNodeType() == ASTNode.SIMPLE_NAME) {
			vars.add(rightSide.toString());
		}
		
		return vars;
	}
	
	static void extractFragment(ILiveVariableRecorder recorder, List<VariableDeclarationFragment> fragments,
			SourceCodeLocation startLocation, NameTableManager manager, CompilationUnitRecorder unitRecorder) {
		for (VariableDeclarationFragment fragment : fragments) {
			SimpleName variable = fragment.getName();
			Expression expression = fragment.getInitializer();
			if (expression != null) {
				recorder.addDefVariable(new LiveVariableDefinition(variable.getIdentifier()));
				
				NameScope currentScope = manager.getScopeOfLocation(startLocation);
				NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
				ExpressionReferenceASTVisitor visitor = new ExpressionReferenceASTVisitor(referenceCreator, unitRecorder, currentScope, true);
				expression.accept(visitor);
				NameReference reference = visitor.getResult();
				
				if (reference.isGroupReference()) {
					List<NameReference> references = reference.getSubReferenceList();
					for (NameReference value : references) {
						if (!value.isLiteralReference() && !value.isMethodReference()
								&& !value.isNullReference() && !value.isTypeReference()) {
							recorder.addUseVariable(new LiveVariableDefinition(value.getName()));
						}
					}
				} else {
					if (!reference.isLiteralReference()) {
						recorder.addUseVariable(new LiveVariableDefinition(reference.getName()));
					}
				}
			}
		}
	}
	static boolean isLiteral(Expression expression) {
		int type = expression.getNodeType();
		if (type == ASTNode.BOOLEAN_LITERAL || type == ASTNode.CHARACTER_LITERAL
				|| type == ASTNode.NULL_LITERAL || type == ASTNode.NUMBER_LITERAL
				|| type == ASTNode.STRING_LITERAL || type == ASTNode.TYPE_LITERAL)
			return true;
		return false;
	}
	static void cout(List<LiveVariableDefinition> list) {
		for (LiveVariableDefinition definition : list) {
			System.out.print(definition.getVariable()+ " ");
		}
		System.out.print("\n");
	}
	
	}
