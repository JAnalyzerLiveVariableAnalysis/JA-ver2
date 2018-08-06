package analyzer.nullCheck;

import java.util.ArrayList;
import java.util.List;

import graph.cfg.analyzer.ReachNameAndDominateNodeRecorder;
import nameTable.nameReference.NameReference;

/**
 * @author Zhou Xiaocong
 * @since 2017Äê9ÔÂ10ÈÕ
 * @version 1.0
 *
 */
public class NullCheckReferenceRecorder extends ReachNameAndDominateNodeRecorder {
	protected List<NameReference> checkedReferenceList = null;		// The references checked with null in this node
	protected List<NameReference> nodeReferenceList = null;			// The all references in this node!
	
	public boolean addCheckedReference(NameReference reference) {
		if (checkedReferenceList == null) checkedReferenceList = new ArrayList<NameReference>();
		return checkedReferenceList.add(reference);
	}
	
	public List<NameReference> getCheckedReferneceList(){
		if (checkedReferenceList == null) checkedReferenceList = new ArrayList<NameReference>();
		return checkedReferenceList;
	}
	
	public void setNodeReference(List<NameReference> referenceList) {
		nodeReferenceList = referenceList; 
	}
	
	public List<NameReference> getNodeReference() {
		if (nodeReferenceList == null) nodeReferenceList = new ArrayList<NameReference>();
		return nodeReferenceList;
	}
}
