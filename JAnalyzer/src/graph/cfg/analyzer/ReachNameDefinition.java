package graph.cfg.analyzer;

import graph.cfg.ExecutionPoint;
import nameTable.nameDefinition.NameDefinition;
import nameTable.nameReference.NameReference;

/**
 * A variable (local variable, parameter or member field) and a reference (expression) to assign value to it
 * @author Zhou Xiaocong
 * @since 2017Äê9ÔÂ5ÈÕ
 * @version 1.0
 *
 */
public class ReachNameDefinition {
	protected NameDefinition name = null;
	protected NameReference value = null;
	protected ExecutionPoint node = null;

	public ReachNameDefinition(ExecutionPoint node, NameDefinition name, NameReference value) {
		this.node = node;
		this.name = name;
		this.value = value;
	}

	public NameDefinition getName() {
		return name;
	}
	
	public NameReference getValue() {
		return value;
	}
	
	public ExecutionPoint getNode() {
		return node;
	}
}
