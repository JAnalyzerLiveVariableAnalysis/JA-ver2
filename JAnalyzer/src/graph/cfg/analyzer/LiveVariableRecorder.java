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
		if (contains(UseVariableList, variable)) {
			return false;
		}
		UseVariableList.add(variable);
		return true;
	}

	@Override
	public boolean addDefVariable(LiveVariableDefinition variable) {
		if (DefVariableList == null)
			DefVariableList = new LinkedList<LiveVariableDefinition>();
		if (contains(DefVariableList, variable)) {
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
		if (contains(LiveInVariableList, variable)) {
			return false;
		}
		LiveInVariableList.add(variable);
		return true;
	}

	@Override
	public boolean addLiveOutVariable(LiveVariableDefinition variable) {
		if (LiveOutVariableList == null)
			LiveOutVariableList = new LinkedList<LiveVariableDefinition>();
		if (contains(LiveOutVariableList, variable)) {
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
	
	public boolean contains(List<LiveVariableDefinition> list, LiveVariableDefinition variableDefinition) {
		for (LiveVariableDefinition definition : list) {
			if (definition.equals(variableDefinition)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean removeLiveInVariable(LiveVariableDefinition variable) {
		if (contains(LiveInVariableList, variable)) {
			for (LiveVariableDefinition variableDefinition : LiveInVariableList) {
				if (variableDefinition.equals(variable)) {
					LiveInVariableList.remove(variableDefinition);
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean removeLiveOutVariable(LiveVariableDefinition variable) {
		if (contains(LiveOutVariableList, variable)) {
			for (LiveVariableDefinition variableDefinition : LiveOutVariableList) {
				if (variableDefinition.equals(variable)) {
					LiveOutVariableList.remove(variableDefinition);
					return true;
				}
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
}
