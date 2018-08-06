package analyzer.nullCheck;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import graph.basic.GraphNode;
import graph.cfg.CFGNode;
import graph.cfg.CFGNodeType;
import graph.cfg.ControlFlowGraph;
import graph.cfg.ExecutionPoint;
import graph.cfg.ExecutionPointLabel;
import graph.cfg.ExecutionPointType;
import graph.cfg.analyzer.ReachNameDefinition;
import graph.cfg.analyzer.ReachNameAnalyzer;
import graph.cfg.creator.CFGCreator;
import nameTable.NameTableASTBridge;
import nameTable.NameTableManager;
import nameTable.creator.NameReferenceCreator;
import nameTable.creator.NameTableCreator;
import nameTable.filter.NameDefinitionKindFilter;
import nameTable.nameDefinition.DetailedTypeDefinition;
import nameTable.nameDefinition.FieldDefinition;
import nameTable.nameDefinition.MethodDefinition;
import nameTable.nameDefinition.NameDefinition;
import nameTable.nameDefinition.NameDefinitionKind;
import nameTable.nameDefinition.TypeDefinition;
import nameTable.nameReference.MethodReference;
import nameTable.nameReference.NameReference;
import nameTable.nameReference.NameReferenceKind;
import nameTable.nameReference.NameReferenceLabel;
import nameTable.nameReference.TypeReference;
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
 * @since 2017Äê7ÔÂ18ÈÕ
 * @version 1.0
 *
 */
public class MethodReturnValueAnalyzer {
	TreeMap<MethodDefinition, MethodReturnValueRecorderList> map = null;
	NameTableManager manager = null;
	
	public MethodReturnValueAnalyzer(NameTableManager manager) {
		this.manager = manager;
		map = new TreeMap<MethodDefinition, MethodReturnValueRecorderList>();
	}
	
	public void analyze() {
		NameDefinitionVisitor visitor = new NameDefinitionVisitor(new NameDefinitionKindFilter(NameDefinitionKind.NDK_METHOD));
		manager.accept(visitor);
		List<NameDefinition> methodList = visitor.getResult();
		if (methodList == null) return;
		for (NameDefinition method : methodList) {
//			if (!method.getFullQualifiedName().contains("getRankPairList")) continue;
			collectReturnValueRecordFromAllRelatedMethods((MethodDefinition)method);
		}
		refineReturnNullValueJudgement();
	}
	
	public void analyze(List<MethodDefinition> methodList) {
		if (methodList == null) return;
		for (MethodDefinition method : methodList) collectReturnValueRecordFromAllRelatedMethods(method);
		refineReturnNullValueJudgement();
	}

	public void analyze(MethodDefinition method) {
		collectReturnValueRecordFromAllRelatedMethods(method);	
		refineReturnNullValueJudgement();
	}

	public boolean isReturnPrimitiveValue(MethodDefinition method) {
		NameReference returnType = method.getReturnType();
		if (returnType == null) return false;
		if (!returnType.isTypeReference()) {
			throw new AssertionError("The return type reference of method " + method.getUniqueId() + " is not a type reference!");
		}
		TypeReference typeReference = (TypeReference)returnType;
		if (typeReference.isArrayType()) return false;
		if (NameReferenceLabel.isPrimitiveTypeName(typeReference.getName())) return true;
		else return false;
	}
	
	public boolean isPossibleReturnNull(MethodDefinition method) {
		if (!map.containsKey(method)) return false;
		MethodReturnValueRecorderList recorder = map.get(method);
		if (recorder == null) return false;
		return recorder.hasNullValue();
	}
	
	public void printDetailsAsDataTable(PrintWriter writer) {
		String message = "File\tClass\tMethod\tReturnValue\tIsNull\tLastAssign\tReturnExpression\tReturnLocation\tMethodLocation";
		writer.println(message);
		
		Set<MethodDefinition> methodSet = map.keySet();
		for (MethodDefinition method : methodSet) {
			MethodReturnValueRecorderList recorderList = map.get(method);
			SourceCodeLocation location = method.getLocation();
			String unitFileName = location.getFileUnitName();
			DetailedTypeDefinition type = (DetailedTypeDefinition)method.getEnclosingType();
			
			message = unitFileName + "\t" + type.getSimpleName() + "\t" + method.getSimpleName();
			for (MethodReturnValueRecorder recorder : recorderList.returnValueList) {
				String returnMessage = null;
				if (recorder.value != null) returnMessage = "\t" + recorder.value.getUniqueId();
				else returnMessage = "\t" + "Unknown";
				
				if (recorder.isNull) returnMessage += "\tNULL";
				else returnMessage += "\tNoNull";
				
				if (recorder.lastAssignment != null) returnMessage += "\t" + recorder.lastAssignment.toSimpleString();
				else returnMessage += "\tUnknown";
				
				if (recorder.expression != null) {
					returnMessage += "\t" + recorder.expression.toSimpleString() + "\t[" + recorder.expression.getLocation() + "]";
				} else returnMessage += "\tUnknown\tUnknown";
				
				returnMessage += "\t" + location.getUniqueId();
				
				writer.println(message + returnMessage);
			}
		}
	}
	
	public void printDetailsAsTextFile(PrintWriter writer) {
		Set<MethodDefinition> methodSet = map.keySet();
		for (MethodDefinition method : methodSet) {
			MethodReturnValueRecorderList recorderList = map.get(method);
			SourceCodeLocation location = method.getLocation();

			writer.println(method.getSimpleName() + "  [" + location.getUniqueId() + "] ");
			for (MethodReturnValueRecorder recorder : recorderList.returnValueList) {
				if (recorder.expression != null) {
					writer.println("\tExpression: " + recorder.expression.toSimpleString() + " [" + recorder.expression.getLocation() + "]");
				} else writer.println("\tExpression: Unknown");

				if (recorder.value != null) {
					writer.println("\t\tValue: " + recorder.value.getUniqueId() + " [" + recorder.value.getDefinitionKind() + "]");
				} else writer.println("\t\tValue: Unknown");
				
				if (recorder.lastAssignment != null) {
					NameReferenceKind kind = recorder.lastAssignment.getReferenceKind();
					String kindString = kind.toString();
					if (kind == NameReferenceKind.NRK_GROUP) {
						kindString = ((NameReferenceGroup)recorder.lastAssignment).getGroupKind().toString();
					}
					writer.println("\t\tLast Assignment: " + recorder.lastAssignment.toSimpleString() + "(" + recorder.lastAssignment.getLocation() + ")[" + kindString + "]");
				} else writer.println("\t\tLast Assignment: Unknown");

				if (recorder.isNull){
					writer.println("\t\tPossible Null: True");
				} else writer.println("\t\tPossible Null: False");
			}
		}
		writer.flush();
	}

	public void printSummary(PrintWriter writer) {
		String message = "File\tClass\tMethod\tReturnNull\tLocation";
		writer.println(message);
		
		Set<MethodDefinition> methodSet = map.keySet();
		for (MethodDefinition method : methodSet) {
			MethodReturnValueRecorderList recorderList = map.get(method);
			SourceCodeLocation location = method.getLocation();
			String unitFileName = location.getFileUnitName();
			DetailedTypeDefinition type = (DetailedTypeDefinition)method.getEnclosingType();
			
			message = unitFileName + "\t" + type.getSimpleName() + "\t" + method.getSimpleName();
			if (recorderList.hasNullValue()) message += "\tYes";
			else message += "\tNo";
			message += "\t" + location.getUniqueId();
			writer.println(message);
		}
	}
	
	void refineReturnNullValueJudgement() {
		Set<MethodDefinition> methodSet = map.keySet();
		
		while (true) {
			boolean hasChanged = false;
			for (MethodDefinition method : methodSet) {
				MethodReturnValueRecorderList recorderList = map.get(method);
				if (recorderList.hasNullValue()) continue;
				for (MethodReturnValueRecorder recorder : recorderList.returnValueList) {
					if (recorder.value != null) {
						if (recorder.value.getDefinitionKind() == NameDefinitionKind.NDK_METHOD) {
							MethodDefinition value = (MethodDefinition)recorder.value;
							if (map.containsKey(value)) {
								MethodReturnValueRecorderList valueRecorderList = map.get(value);
								if (valueRecorderList.hasNullValue()) {
									hasChanged = true;
									recorder.isNull = true;
									break;
								}
							}
						}
					}
				}
			}
			if (!hasChanged) return;
		}
	}
	
	void collectReturnValueRecordFromAllRelatedMethods(MethodDefinition method) {
		if (method.isConstructor()) return;
		if (isReturnPrimitiveValue(method)) return;
		TypeDefinition type = method.getEnclosingType();
		if (!type.isDetailedType() || type.isAnonymous()) return;
		
		LinkedList<MethodDefinition> needAnalyzeMethodQueue = new LinkedList<MethodDefinition>();
		needAnalyzeMethodQueue.add(method);
		
		while (!needAnalyzeMethodQueue.isEmpty()) {
			MethodDefinition currentMethod = needAnalyzeMethodQueue.removeFirst();
			
			// Collect return value for the currentMethod. If the method call other methods in its return statments, the list of all
			// such callee methods will be returned, and all this callee should be analyzed further.
			List<MethodDefinition> methodReturnCalleeList = collectReturnValueRecord(currentMethod);
			if (methodReturnCalleeList != null) {
				for (MethodDefinition callee : methodReturnCalleeList) {
					if (!map.containsKey(callee)) {
						// This callee have not been analyzed, so add it to the last of the queue.
						needAnalyzeMethodQueue.addLast(callee);
					}
				}
			}
		}
	}

	List<MethodDefinition> collectReturnValueRecord(MethodDefinition method) {
		NameTableASTBridge bridge = new NameTableASTBridge(manager);
		SourceCodeFileSet sourceCodeFileSet = manager.getSouceCodeFileSet();

		CompilationUnitScope unitScope = manager.getEnclosingCompilationUnitScope(method);
		if (unitScope == null) return null;
		String unitFileName = unitScope.getUnitName();
		CompilationUnit astRoot = manager.getSouceCodeFileSet().findSourceCodeFileASTRootByFileUnitName(unitFileName);
		if (astRoot == null) return null;
		CompilationUnitRecorder unitRecorder = new CompilationUnitRecorder(unitFileName, astRoot);
		
		// Create a ControFlowGraph object
		ControlFlowGraph graph = CFGCreator.create(manager, method);
		if (graph == null) return null;
		
		ReachNameAnalyzer.setReachNameRecorder(graph);
		ReachNameAnalyzer.reachNameAnalysis(manager, unitRecorder, method, graph);
		NameReferenceCreator referenceCreator = new NameReferenceCreator(manager, true);
		
		List<GraphNode> nodeList = graph.getAllNodes();
		if (nodeList == null) return null;

		MethodReturnValueRecorderList recorderList = new MethodReturnValueRecorderList();
		Debug.println("Analyze method " + method.getFullQualifiedName());
		
		for (GraphNode node : nodeList) {
			CFGNode cfgNode = (CFGNode)node;
			if (cfgNode.getCFGNodeType() != CFGNodeType.N_EXECUTION_POINT) continue;
			ExecutionPoint exePoint = (ExecutionPoint)cfgNode;
			
			if (exePoint.getType() == ExecutionPointType.FLOW_CONTROLLER && exePoint.getLabel() == ExecutionPointLabel.RETURN_LABEL) {
				ASTNode astNode = exePoint.getAstNode();
				List<NameReference> referenceList = referenceCreator.createReferencesForASTNode(unitFileName, astNode);	
				if (referenceList != null) {
					if (referenceList.size() != 1) {
						throw new AssertionError("There are more than one expression in a return statement " + astNode);
					}
					NameReference reference = referenceList.get(0);
					if (reference.isNullReference()) {
						MethodReturnValueRecorder recorder = new MethodReturnValueRecorder();
						recorder.expression = reference;
						recorder.isNull = true;
						recorderList.returnValueList.add(recorder);
					} else {
						reference.resolveBinding();
						List<ReachNameDefinition> reachNameList = ReachNameAnalyzer.getRootReachNameDefinitionList(graph, exePoint, reference);
						if (reachNameList.size() > 0) {
							for (ReachNameDefinition reachName : reachNameList) {
								MethodReturnValueRecorder recorder = new MethodReturnValueRecorder();
								recorder.expression = reference;
								recorder.value = reachName.getName();
								recorder.lastAssignment = reachName.getValue();
								recorderList.returnValueList.add(recorder);
							}
						} else {
							MethodReturnValueRecorder recorder = new MethodReturnValueRecorder();
							recorder.expression = reference;
							recorder.value = extractLeftValueInReference(reference);
							recorderList.returnValueList.add(recorder);
						}
					}
				}
			}
		}

		// Put the method and its recorder list to map
		map.put(method, recorderList);
		
		sourceCodeFileSet.releaseAST(unitFileName);
		sourceCodeFileSet.releaseFileContent(unitFileName);
		
		// Check if the last assignment reference for the return value is binded to null
		List<MethodDefinition> calleeList = new ArrayList<MethodDefinition>();
		List<MethodReturnValueRecorder> extraRecordForPolynominalCalling = new ArrayList<MethodReturnValueRecorder>();
		for (MethodReturnValueRecorder recorder : recorderList.returnValueList) {
			if (recorder.value == null) continue;
			if (recorder.lastAssignment != null) {
				if (recorder.lastAssignment.isNullReference()) {
					// The return value is assigned to be null finally
					recorder.isNull = true;
					return null;
				} else if (recorder.lastAssignment.isGroupReference()) {
					// The return value is from a method call, so we analyze this method further, that is, we add it to calleeList for 
					// further analyze
					NameReferenceGroup group = (NameReferenceGroup)recorder.lastAssignment;
					NameReferenceGroupKind groupKind = group.getGroupKind();
					if (groupKind == NameReferenceGroupKind.NRGK_METHOD_INVOCATION || 
							groupKind == NameReferenceGroupKind.NRGK_SUPER_METHOD_INVOCATION) {
						List<NameReference> sublist = group.getSubReferenceList();
						for (NameReference subreference : sublist) {
							if (subreference.getReferenceKind() == NameReferenceKind.NRK_METHOD) {
								if (subreference.getDefinition() != null) {
									recorder.value = subreference.getDefinition();
									MethodDefinition callee = (MethodDefinition)recorder.value;
									TypeDefinition enclosingType = callee.getEnclosingType();
									if (enclosingType.isDetailedType() && !enclosingType.isAnonymous()) {
										calleeList.add(callee);
									}
									
									// To add new record for polynominal calling, note that the first binded method definition
									// is returned by subreference.getDefinition()
									MethodReference methodReference = (MethodReference)subreference;
									List<MethodDefinition> alternativeList = methodReference.getAlternativeList();
									for (int index = 1; index < alternativeList.size(); index++) {
										callee = alternativeList.get(index);
										enclosingType = callee.getEnclosingType();
										if (enclosingType.isDetailedType() && !enclosingType.isAnonymous()) {
											MethodReturnValueRecorder newRecorder = new MethodReturnValueRecorder(recorder);
											newRecorder.value = callee;
											extraRecordForPolynominalCalling.add(newRecorder);
											calleeList.add(callee);
										}
									}
								}
							}
						}
					}
				}
			} else if (recorder.value.getDefinitionKind() == NameDefinitionKind.NDK_FIELD) {
				FieldDefinition field = (FieldDefinition)recorder.value;
				recorder.lastAssignment = extractLastAssignmentForField(manager, referenceCreator, bridge, field);
				if (recorder.lastAssignment != null) {
					if (recorder.lastAssignment.isNullReference()) {
						// The return value is a field, and it is assigned to be null finally
						recorder.isNull = true;
					}
				}
			}
		}
		
		// Add extra recorder to the recorderList
		for (MethodReturnValueRecorder recorder : extraRecordForPolynominalCalling) recorderList.returnValueList.add(recorder);
		return calleeList;
	}
	
	
	NameReference extractLastAssignmentForField(NameTableManager manager, NameReferenceCreator referenceCreator, NameTableASTBridge bridge, FieldDefinition field) {
		NameReference result = field.getInitializer();
		
		TypeDefinition enclosingType = field.getEnclosingType();
		if (!enclosingType.isDetailedType()) return result;
		DetailedTypeDefinition type = (DetailedTypeDefinition)enclosingType;
		
		String unitFileName = field.getLocation().getFileUnitName();
		SourceCodeFileSet sourceCodeFileSet = manager.getSouceCodeFileSet();
		CompilationUnit root = sourceCodeFileSet.findSourceCodeFileASTRootByFileUnitName(unitFileName);
		TypeDeclaration typeDeclaration = bridge.findASTNodeForDetailedTypeDefinition(type);
		
		// Find assignment for the field in the initializer block of the type
		NameReference lastAssignment = null;
		@SuppressWarnings("unchecked")
		List<BodyDeclaration> bodyList = typeDeclaration.bodyDeclarations();
		for (BodyDeclaration bodyDecl : bodyList) {
			int nodeType = bodyDecl.getNodeType();
			if (nodeType == ASTNode.INITIALIZER) {
				List<NameReference> referenceList = referenceCreator.createReferences(unitFileName, root, type, (Initializer)bodyDecl);
				for (NameReference reference : referenceList) {
					reference.resolveBinding();
					NameReference assignment = extractAssignmentRightExpressionForFieldInReference(field, reference);
					if (lastAssignment == null) lastAssignment = assignment;
					else {
						SourceCodeLocation location = assignment.getLocation();
						SourceCodeLocation lastLocation = lastAssignment.getLocation();
						if (lastLocation.compareTo(location) >= 0) lastAssignment = assignment;
					}
				}
			}
		}
		// The assignment to the field in last initializer is the last assignment for the field.
		if (lastAssignment != null) result = lastAssignment;

		
		// Find assignment for the field in the constructor of the type
		MethodDeclaration[] methodDeclarationArray = typeDeclaration.getMethods();
		for (int index = 0; index < methodDeclarationArray.length; index++) {
			MethodDeclaration methodDeclaration = methodDeclarationArray[index];
			if (!methodDeclaration.isConstructor()) continue;

			lastAssignment = null;
			
			SourceCodeLocation constrcutorLocation = SourceCodeLocation.getStartLocation(methodDeclaration, root, unitFileName);
			MethodDefinition methodDefinition = bridge.findDefinitionForMethodDeclaration(type, constrcutorLocation, methodDeclaration);
			List<NameReference> referenceList = referenceCreator.createReferences(unitFileName, root, methodDefinition, methodDeclaration);
			for (NameReference reference : referenceList) {
				reference.resolveBinding();
				NameReference assignment = extractAssignmentRightExpressionForFieldInReference(field, reference);
				if (assignment != null) {
					if (lastAssignment == null) lastAssignment = assignment;
					else {
						SourceCodeLocation location = assignment.getLocation();
						SourceCodeLocation lastLocation = lastAssignment.getLocation();
						if (lastLocation.compareTo(location) >= 0) lastAssignment = assignment;
					}
				}
			}
			
			if (lastAssignment == null) {
				// This constructor has not assignment for this field, so the last assignment for the field should
				// be in field declaration or initializer
				return result;
			} else {
				if (lastAssignment.isNullReference()) {
					// There a constructor assign the field to be null, so return this assignment
					return lastAssignment;
				} else {
					// otherwise, we should check the remainder constructor, and the assignment in the last constructor will be returned!
					result = lastAssignment;
				}
			}
		}
		return result;
	}
	
	NameReference extractAssignmentRightExpressionForFieldInReference(FieldDefinition field, NameReference reference) {
		if (!reference.isGroupReference()) return null;

		NameReferenceGroup group = (NameReferenceGroup)reference;
		NameReferenceGroupKind groupKind = group.getGroupKind();

		List<NameReference> sublist = group.getSubReferenceList();

		if(groupKind == NameReferenceGroupKind.NRGK_ASSIGNMENT) {
			NameReference leftReference = sublist.get(0);
			NameReference rightReference = sublist.get(1);
			NameDefinition leftValue = extractLeftValueInReference(leftReference);
			if (leftValue == field) return rightReference;
		}
		return null;
	}
	
	NameDefinition extractLeftValueInReference(NameReference reference) {
		if (!reference.isGroupReference()) return reference.getDefinition();
		NameReferenceGroup group = (NameReferenceGroup)reference;
		NameReferenceGroupKind groupKind = group.getGroupKind();
		List<NameReference> sublist = group.getSubReferenceList();
		
		if (groupKind == NameReferenceGroupKind.NRGK_ARRAY_ACCESS) {
			return sublist.get(0).getDefinition();
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
	
	public static void main(String[] args) {
		String rootPath = "C:\\";

		
		String[] paths = {"C:\\QualitasPacking\\recent\\eclipse_SDK\\eclipse_SDK-4.3\\", "C:\\QualitasPacking\\recent\\jfreechart\\jfreechart-1.0.13\\", 
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
			writer = new PrintWriter(new FileOutputStream(new File(result)));
		} catch (Exception exc) {
			exc.printStackTrace();
			return;
		}
		
		try {
			String info = rootPath + "ZxcWork\\ProgramAnalysis\\data\\debug.txt";
			output = new PrintWriter(new FileOutputStream(new File(info)));
			Debug.setWriter(output);
			Debug.setScreenOn();
		} catch (Exception exc) {
			exc.printStackTrace();
			writer.close();
			return;
		}
		
		try {
			Debug.setStart("Begin collection....");
			NameTableManager manager = NameTableManager.createNameTableManager(path);
			MethodReturnValueAnalyzer analyzer = new MethodReturnValueAnalyzer(manager);
			analyzer.analyze();
			analyzer.printDetailsAsTextFile(writer);
			Debug.time("After collection...");
		} catch (Exception exc) {
			exc.printStackTrace();
		}

		writer.close();
		output.close();
	}
	
}

class MethodReturnValueRecorder {
	// The expression after the keyword "return"
	NameReference expression = null;		
	// The impossible value which the expression binded to. 
	// For statement "return variable", the value will be the definition of the variable (reference) binded to;
	// For statement "return methodCallExpression", the value with be the definition of the method definition.
	NameDefinition value = null;
	
	// The last assignment reference to the above value
	NameReference lastAssignment = null;
	// If the value will be null possibly, the isNull will be true
	boolean isNull = false;
	
	MethodReturnValueRecorder() {
	}
	
	MethodReturnValueRecorder(MethodReturnValueRecorder other) {
		expression = other.expression;
		value = other.value;
		lastAssignment = other.lastAssignment;
		isNull = other.isNull;
	}
}

class MethodReturnValueRecorderList {
	List<MethodReturnValueRecorder> returnValueList = new ArrayList<MethodReturnValueRecorder>();
	
	boolean hasNullValue() {
		for (MethodReturnValueRecorder returnValue : returnValueList) {
			if (returnValue.isNull) return true;
		}
		return false;
	}
}
