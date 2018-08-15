package graph.cfg.analyzer;

import java.util.LinkedList;
import java.util.LinkedList;
import java.util.List;

public class LiveVariableRecorder implements ILiveVariableRecorder {
	protected List<LiveVariableDefinition> UseVariableList = null;
	protected List<LiveVariableDefinition> DefVariableList = null;
	protected List<LiveVariableDefinition> LiveInVariableList = null;
	protected List<LiveVariableDefinition> LiveOutVariableList = null;
	
	
	@Override
	public boolean addUseVariable(LiveVariableDefinition variable) {
		if (UseVariableList == null)
			UseVariableList = new LinkedList<LiveVariableDefinition>();
		if (isContain(UseVariableList, variable)) {
			return false;
		}
		UseVariableList.add(variable);
		return true;
	}

	@Override
	public boolean addDefVariable(LiveVariableDefinition variable) {
		if (DefVariableList == null)
			DefVariableList = new LinkedList<LiveVariableDefinition>();
		if (isContain(DefVariableList, variable)) {
			return false;
		}
		DefVariableList.add(variable);
		return true;
	}

	@Override
	public List<LiveVariableDefinition> getUseVariableList() {
		if (UseVariableList == null)
			UseVariableList = new LinkedList<LiveVariableDefinition>();
		return UseVariableList;
	}

	@Override
	public List<LiveVariableDefinition> getDefVariableList() {
		if (DefVariableList == null)
			DefVariableList = new LinkedList<LiveVariableDefinition>();
		return DefVariableList;
	}

	@Override
	public boolean addLiveInVariable(LiveVariableDefinition variable) {
		if (LiveInVariableList == null)
			LiveInVariableList = new LinkedList<LiveVariableDefinition>();
		if (isContain(LiveInVariableList, variable)) {
			return false;
		}
		LiveInVariableList.add(variable);
		return true;
	}

	@Override
	public boolean addLiveOutVariable(LiveVariableDefinition variable) {
		if (LiveOutVariableList == null)
			LiveOutVariableList = new LinkedList<LiveVariableDefinition>();
		if (isContain(LiveOutVariableList, variable)) {
			return false;
		}
		LiveOutVariableList.add(variable);
		return true;
	}

	@Override
	public List<LiveVariableDefinition> getLiveInVariableList() {
		if (LiveInVariableList == null)
			LiveInVariableList = new LinkedList<LiveVariableDefinition>();
		return LiveInVariableList;
	}

	@Override
	public List<LiveVariableDefinition> getLiveOutVariableList() {
		if (LiveOutVariableList == null)
			LiveOutVariableList = new LinkedList<LiveVariableDefinition>();
		return LiveOutVariableList;
	}
	
	public boolean removeLiveInVariable(LiveVariableDefinition variable) {
		for (LiveVariableDefinition definition : LiveInVariableList) {
			if (definition.isEqual(variable)) {
				LiveInVariableList.remove(definition);
				return true;
			}
		}
		return false;
	}
	
	public boolean removeLiveOutVariable(LiveVariableDefinition variable) {
		for (LiveVariableDefinition definition : LiveOutVariableList) {
			if (definition.isEqual(variable)) {
				LiveOutVariableList.remove(definition);
				return true;
			}
		}
		return false;
	}
	
	public void setLiveOutEmpty() {
		if (LiveOutVariableList == null)
			LiveOutVariableList = new LinkedList<LiveVariableDefinition>();
		LiveOutVariableList.clear();
	}
	
	public void setLiveInEmpty() {
		if (LiveInVariableList == null)
			LiveInVariableList = new LinkedList<LiveVariableDefinition>();
		LiveInVariableList.clear();
	}
	
	static boolean isContain(List<LiveVariableDefinition> VarList, LiveVariableDefinition Var) {
		for (LiveVariableDefinition definition : VarList) {
			if (Var.isEqual(definition))
				return true;
		}
		return false;
	}
}
