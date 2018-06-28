package sc.fiji;

import java.util.TreeSet;

public class KeyFrames {

	private TreeSet<KeyFrame> keyFrames;
	
	private int anchorID;
	private boolean anchorExists;
	
	private double maxRoiLength;
	private int maxAnchorIndex;
	
	public KeyFrames() {
		
		keyFrames = new TreeSet<KeyFrame>();
		
		anchorID = 0;
		anchorExists = false;
		
		maxRoiLength = 0;
		maxAnchorIndex = 0;
	}
	
	public boolean addKeyFrame(KeyFrame kf) {
		
		boolean success = keyFrames.add(kf);
		
		if(success) {
			//see if you need to update length and anchor index
		}
		
		return success;
	}
	
	public int getAnchorID() {
		return anchorID;
	}
	
	public boolean anchorExists() {
		return anchorExists;
	}
	
	public double getMaxRoiLength() {
		return maxRoiLength;
	}
	
	public int getMaxAnchorIndex() {
		return maxAnchorIndex;
	}
	
	public void updateAnchor(int newAnchorID) {
		anchorID = newAnchorID;
		anchorExists = true;
	}
}
