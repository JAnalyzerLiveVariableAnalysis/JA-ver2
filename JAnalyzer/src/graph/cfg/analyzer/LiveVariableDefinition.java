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
	
	public boolean isEqual(LiveVariableDefinition variableDefinition) {
		if (reference.equals(variableDefinition.getReference()) &&
				definition.equals(variableDefinition.getDefinition()) &&
				node.equals(variableDefinition.getNode())) {
			return true;
		}
		return false;
	}
}
