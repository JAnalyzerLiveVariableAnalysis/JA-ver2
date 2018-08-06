package graph.cfg.analyzer;

import graph.cfg.ExecutionPoint;
import nameTable.nameDefinition.NameDefinition;
import nameTable.nameReference.NameReference;

public class LiveVariableDefinition {
//	protected NameDefinition name = null;
//	protected NameReference value = null;
//	protected ExecutionPoint node = null;
	protected String variable = null;

	
//	public LiveVariableDefinition(ExecutionPoint node, NameDefinition name, NameReference value, String variable) {
//		this.node = node;
//		this.name = name;
//		this.value = value;
	public LiveVariableDefinition(String variable) {
		this.variable = variable;
	}

//	public NameDefinition getName() {
//		return name;
//	}
//	
//	public NameReference getValue() {
//		return value;
//	}
//	
//	public ExecutionPoint getNode() {
//		return node;
//	}
//	
	public String getVariable() {
		return variable;
	}
}
