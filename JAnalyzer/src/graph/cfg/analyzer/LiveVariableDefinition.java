package graph.cfg.analyzer;

import graph.cfg.ExecutionPoint;
import nameTable.nameDefinition.NameDefinition;
import nameTable.nameReference.NameReference;

public class LiveVariableDefinition {
	protected NameReference reference = null;
	protected NameDefinition definition = null;
	protected ExecutionPoint node = null;

	public LiveVariableDefinition(NameReference reference, NameDefinition definition, ExecutionPoint node) {
		this.reference = reference;
		this.definition = definition;
		this.node = node;
	}

	public NameReference getReference() {
		return reference;
	}
	
	public NameDefinition getDefinition() {
		return definition;
	}
	
	public ExecutionPoint getNode() {
		return node;
	}
	
	public boolean equals(LiveVariableDefinition variableDefinition) {
		if (variableDefinition == null) {
			return false;
		}
		return compareDefinition(definition, variableDefinition.getDefinition());
	}
	
	static boolean compareReference(NameReference reference1, NameReference reference2) {
		if (reference1 != null && reference2 != null) {
			return reference1.equals(reference2);
		}
		return reference1 == null && reference2 == null;
	}
	
	static boolean compareDefinition(NameDefinition definition1, NameDefinition definition2) {
		if (definition1 != null && definition2 != null) {
			return definition1.equals(definition2);
		}
		return definition1 == null && definition2 == null;
	}
	
	static boolean compareNode(ExecutionPoint node1, ExecutionPoint node2) {
		if (node1 != null && node2 != null) {
			return node1.equals(node2);
		}
		return node1 == null && node2 == null;
	}
}
