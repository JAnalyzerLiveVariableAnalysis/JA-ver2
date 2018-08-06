package graph.cfg.analyzer;

import java.util.List;

import graph.cfg.IFlowInfoRecorder;

public interface ILiveVariableRecorder extends IFlowInfoRecorder {
	public boolean addUseVariable(LiveVariableDefinition variable);
	public boolean addDefVariable(LiveVariableDefinition variable);
	public boolean addLiveInVariable(LiveVariableDefinition variable);
	public boolean addLiveOutVariable(LiveVariableDefinition variable);
	
	public List<LiveVariableDefinition> getUseVariableList();
	public List<LiveVariableDefinition> getDefVariableList();
	public List<LiveVariableDefinition> getLiveInVariableList();
	public List<LiveVariableDefinition> getLiveOutVariableList();
	
	public boolean removeLiveInVariable(LiveVariableDefinition variable);
	public boolean removeLiveOutVariable(LiveVariableDefinition variable);
	public void setLiveInEmpty();
	public void setLiveOutEmpty();
}
