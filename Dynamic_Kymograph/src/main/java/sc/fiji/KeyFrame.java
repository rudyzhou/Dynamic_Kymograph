package sc.fiji;

import ij.gui.Roi;

//I haven't used this class in the plugin yet, but the idea was to store both the frame number and ROI in a KeyFrame object.

public class KeyFrame implements Comparable<KeyFrame>{
	
	private int frame;
	
	private Roi roi;
	private int roiType;
	
	public KeyFrame() {
		
		frame = 0;
		
		roi = null;
		roiType = 0;
	}
	
	public KeyFrame(int frame, Roi roi) {
		this.frame = frame;
		
		this.roi = (Roi) roi.clone();
		this.roiType = this.roi.getType();
	}

	public int getFrame() {
		return frame;
	}
	
	public Roi getRoi() {
		return roi;
	}
	
	public int getType() {
		return roiType;
	}
	
	@Override
	public int compareTo(KeyFrame o) {
		return frame - o.getFrame();
	}
}
