package sc.fiji;

import java.awt.Frame;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.FlowLayout;

import ij.IJ;
import ij.ImageJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.RoiListener;
import ij.gui.TextRoi;
import ij.plugin.PlugIn;
import ij.plugin.frame.PlugInFrame;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class Dynamic_Kymograph extends PlugInFrame implements PlugIn, ActionListener, ImageListener, RoiListener, KeyListener, MouseListener, WindowListener{
	
	private Frame frame;
	private Label anchorLabel;
	private Label statusLabel;
	
	protected ImagePlus image;
	private int numFrames;
	private ImageWindow window;
	private ImageCanvas canvas;
	private int imageType;
	
	private ImagePlus savedRois;
	private Overlay overlayRois;
	
	private Overlay overlayAnchor;
	
	//maintains the "edited" polylines that the user inputs
	private HashMap<Integer, Roi> recordedRois = new HashMap<Integer, Roi>();
	private Roi[] interpolatedRois;
	
	//ID of the anchor point
	private int anchorID;
	private boolean anchorExists;
	
	public void run(String arg0) {
		
		//assume stack already opened; get the associated image parameters
		image = IJ.getImage();
		window = image.getWindow();
		canvas = image.getCanvas();
		numFrames = image.getNSlices();
		imageType = image.getType();
		
		savedRois = new ImagePlus("Saved ROIS",  image.getStack().getProcessor(1));
		savedRois.show();
		
		overlayRois = new Overlay();
		
		savedRois.setOverlay(overlayRois);
		
		overlayAnchor = new Overlay();
		image.setOverlay(overlayAnchor);
		
		//indexed by frames 1 through numFrames
		interpolatedRois = new Roi[numFrames + 1];
		
		anchorID = 0;
		anchorExists = false;
		
		removeListeners();
		addListeners();
	}

	public Dynamic_Kymograph() {
		
		super("Dynamic Kymograph");
		if(frame != null) {
			WindowManager.toFront(frame);
			return;
		}
		if (IJ.isMacro()) {
			return;
		}
		
		frame = this;
		WindowManager.addWindow(this);
		
		frame.setVisible(true);
		frame.setTitle("Dynamic Kymograph");
		frame.setSize(500, 100);
		
		Panel mainPanel = new Panel();
		
		mainPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		
		Button anchorButton = new Button("Select anchor point");
		Button kymographButton = new Button("Make kymograph");
		Button saveCurrentRoi = new Button("Save current ROI");
		Button resetKeyFramesButton = new Button("Reset key frames");

		anchorLabel = new Label("Anchor not set");
		anchorLabel.setSize(anchorLabel.getPreferredSize());
		//statusLabel = new Label("status");
		
		kymographButton.addActionListener(this);
		resetKeyFramesButton.addActionListener(this);
		anchorButton.addActionListener(this);
		saveCurrentRoi.addActionListener(this);

		mainPanel.add(anchorButton);
		mainPanel.add(kymographButton);
		mainPanel.add(saveCurrentRoi);
		mainPanel.add(resetKeyFramesButton);
		
		//mainPanel.add(statusLabel);
		mainPanel.add(anchorLabel);
		
		//frame.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
		
		frame.add(mainPanel);
		
	}
	
	
	public static void main(String[] args) {
		
		Class<?> clazz = Dynamic_Kymograph.class;
		
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring("file:".length(), url.length() - clazz.getName().length() - ".class".length());
		System.setProperty("plugins.dir", pluginsDir);

		// start ImageJ
		new ImageJ();

		// open example stack
		ImagePlus image = IJ.openImage("D:/Users/rudyz/Documents/Graduate3/biology/code/pic2.tif");
		image.show();

		// run the test plugin
		IJ.runPlugIn(clazz.getName(), "");
	}
	
	public void interpolate() {
		
		fillRoiArrayInterpolate();
		IJ.log("Interpolate");
	}
	
	public void makeKymograph() {
		if(recordedRois.isEmpty()) {
			IJ.error("No ROIs recorded");
		}
		else {
		assembleKymographInterpolate();
		IJ.log("Making Kymograph");	
		}
	}
	
	public void resetKeyFrames() {
			
		resetAnchor();
				
		image.deleteRoi();
		
		recordedRois.clear();
		interpolatedRois = new Roi[numFrames + 1];
		
		IJ.log("Reset key frames");
	}
	
	public void promptAnchorPoint() throws InterruptedException {
		if(anchorExists) {
			IJ.error("promtAnchorPoint error: anchor already exists");
		}
		else {
			
			if(canvas != null && frame != null) {
				
				anchorLabel.setText("Click on a handle to set it as the anchor point");
				anchorLabel.setSize(anchorLabel.getPreferredSize());
				
				canvas.addMouseListener(this);
				frame.addMouseListener(this);
			}
			else {
				IJ.error("promptAnchorPoint error: frame or canvas does not exist");
			}
		}
		
	}
	
	public void saveRoi() {
		
		Roi currentRoi = (Roi) interpolatedRois[1].clone();
		
		if (currentRoi != null) {
			
			if(overlayRois.contains(currentRoi)) {
				int indexToReplace = 0;
				
				while(!overlayRois.get(indexToReplace).equals(currentRoi)) {
					indexToReplace++;
				}
				
				overlayRois.remove(indexToReplace + 1);
				overlayRois.remove(indexToReplace);
			}
			
			int x;
			int y;
			Polygon currentRoiPoly = currentRoi.getPolygon();
			x = currentRoiPoly.xpoints[0];
			y = currentRoiPoly.ypoints[0];
			
			TextRoi number = new TextRoi(x, y, Integer.toString(overlayRois.size()/2 + 1));
			
			Random rand = new Random();
			Color randomColor = new Color(rand.nextFloat(), rand.nextFloat(), rand.nextFloat());
			
			number.setStrokeColor(randomColor);
			currentRoi.setStrokeColor(randomColor);
			
			overlayRois.add(currentRoi);
			overlayRois.add(number);
			
			savedRois.updateAndDraw();
			savedRois.flatten();
			
			savedRois.changes = true;
		}
		else {
			IJ.error("saveRoi error: no ROI selected");
		}

	}
	
	public void showAbout() {
		IJ.showMessage("Dyamic Kymograph",
			"Plugin to generate kymographs using key framing and linear interpolation"
		);
	}

	//used by all Buttons
	@Override
	public void actionPerformed(ActionEvent e) {
		
		String label = e.getActionCommand();
		
		if (label == "Interpolate") {
			interpolate();
		}
		else if (label == "Make kymograph") {
			makeKymograph();
		}
		else if (label == "Reset key frames") {
			resetKeyFrames();
		}
		else if (label == "Select anchor point") {
			try {
				promptAnchorPoint();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		else if (label == "Save current ROI") {
			saveRoi();
		}
		else {
			IJ.error("Invalid ActionEvent in actionPerformed");
		}
	}
	
	private void addListeners() {
		
		if(window != null) {
			window.addKeyListener(this);
		}
		
		if(canvas != null) {
			canvas.addKeyListener(this);
			//canvas.addMouseListener(this);
		}
		
		
		ImagePlus.addImageListener(this);

		Roi.addRoiListener(this);
		
		IJ.log("added listeners");
	}
	
	private void removeListeners() {
        
		if (window!=null) {
            window.removeKeyListener(this);
        }
		
        if (canvas!=null) {
            canvas.removeKeyListener(this);
           // canvas.removeMouseListener(this);
        }
		
        ImagePlus.removeImageListener(this);
        
        Roi.removeRoiListener(this);
        
        IJ.log("removed listeners");
	}
	
	//Stolen from MultipleKymograph_ plugin; for polyline ROIs
	public double[] getPixelsPolyline(Roi roi, ImagePlus imp, int shift) {
		
		ImageProcessor ip = imp.getProcessor();
		int n = ((PolygonRoi)roi).getNCoordinates();
		int[] x = ((PolygonRoi)roi).getXCoordinates();
		int[] y = ((PolygonRoi)roi).getYCoordinates();
	
		for (int i=0;i<n;i++){
			x[i]+=shift;
			y[i]+=shift;
		}
		
			Rectangle r = roi.getBounds();
			int xbase = r.x;
			int ybase = r.y;
			double length = 0.0;
			double segmentLength;
			int xdelta, ydelta;
			double[] segmentLengths = new double[n];
			int[] dx = new int[n];
			int[] dy = new int[n];
			for (int i=0; i<(n-1); i++) {
	    			xdelta = x[i+1] - x[i];
	    			ydelta = y[i+1] - y[i];
	    			segmentLength = Math.sqrt(xdelta*xdelta+ydelta*ydelta);
	    			length += segmentLength;
	    			segmentLengths[i] = segmentLength;
	    			dx[i] = xdelta;
	    			dy[i] = ydelta;
			}
		double[] values = new double[(int)length];
		double leftOver = 1.0;
		double distance = 0.0;
		int index;
		
		for (int i=0; i<n; i++) {
	    		double len = segmentLengths[i];
	    		if (len==0.0)
	        		continue;
	    		double xinc = dx[i]/len;
	    		double yinc = dy[i]/len;
	    		double start = 1.0-leftOver;
	    		double rx = xbase+x[i]+start*xinc;
	    		double ry = ybase+y[i]+start*yinc;
	    		double len2 = len - start;
	    		int n2 = (int)len2;

	    		for (int j=0; j<=n2; j++) {
	        			index = (int)distance+j;
	        			if (index<values.length)
	        				values[index] = getPixel(ip, rx, ry);
	           			//values[index] = ip.getInterpolatedValue(rx, ry);
	        			rx += xinc;
	     	   			ry += yinc;
	    		}
	    	distance += len;
	    	leftOver = len2 - n2;
		}
	
		return values;
	}	
	
	public double getPixel(ImageProcessor ip, double x, double y) {
		
		if (imageType == ImagePlus.GRAY8 || imageType == ImagePlus.GRAY16 || imageType == ImagePlus.GRAY32) {
			return ip.getInterpolatedValue(x, y);
		}
		else {
			return ((ColorProcessor) ip).getInterpolatedRGBPixel(x, y);
		}
	}
	
	public double[] averageWidth(ImagePlus imp, Roi roi, int lineWidth) {
		
		
		double[] pixels = getPixelsPolyline(roi, imp, 0);
		
		for (int width = 1; width <= lineWidth/2; width++) {
			double[] nextShiftPlus = getPixelsPolyline(roi, imp, width);
			double[] nextShiftMinus = getPixelsPolyline(roi, imp, -width);
			
			for(int i = 0; i < pixels.length; i++) {
				pixels[i] += nextShiftPlus[i] + nextShiftMinus[i];
			}
			
		}
		
		for(int i = 0; i < pixels.length; i++) {
			pixels[i] /= lineWidth;
		}
		
		return pixels;
	}
	
	//assume for now both Rois are polylines with the same number of points (and same anchor)
	public Roi[] interpolateRoi(Roi startRoi, Roi endRoi, int startFrame, int endFrame) {
		
		int dFrame = endFrame - startFrame;
		
		FloatPolygon startPolygon = startRoi.getFloatPolygon();
		FloatPolygon endPolygon = endRoi.getFloatPolygon();
		
		int startN = startPolygon.npoints;
		float[] startX = startPolygon.xpoints;
		float[] startY = startPolygon.ypoints;
		
		int endN = endPolygon.npoints;
		float[] endX = endPolygon.xpoints;
		float[] endY =  endPolygon.ypoints;
		
		float[] difX = new float[startN];
		float[] difY = new float[startN];
		
		for(int i = 0; i < startN; i++) {
			difX[i] = (endX[i] - startX[i]) /dFrame;
			difY[i] = (endY[i] - startY[i]) /dFrame;
		}
		
		float[] interpolatedX = startX;
		float[] interpolatedY = startY;
		
		Roi[] interpolatedRois = new Roi[dFrame+1];
		
		for(int frame = 0; frame < interpolatedRois.length; frame++) {
			
			interpolatedRois[frame] = new PolygonRoi(interpolatedX, interpolatedY, startN, Roi.POLYLINE);
			
			for (int i = 0; i < interpolatedX.length; i++) {
				interpolatedX[i] += difX[i];
				interpolatedY[i] += difY[i];
			}
		}
		
		return interpolatedRois;
	}
	
	public double[] alignPixels(double[] pixels, int kymoWidth, int indexToMatch, Roi roi) {
	
		double[] alignedPixels = new double[kymoWidth];
		
		Polygon roiPolygon = roi.getPolygon();
		
		int[] x = roiPolygon.xpoints;
		int[] y = roiPolygon.ypoints;
		int n = roiPolygon.npoints;
		
		double lengthBeforeAnchor = 0;
		
		for(int i = 0; i < anchorID && i < n; i++) {
			int dx = x[i + 1] - x[i];
			int dy = y[i + 1] - y[i];
			lengthBeforeAnchor += Math.sqrt(dx*dx+dy*dy);
		}
		
		int anchorIndex = (int) lengthBeforeAnchor;
		
		int startIndex = 0;
		if (indexToMatch - anchorIndex > 1) {
			startIndex = indexToMatch - anchorIndex;
		}
		
		for(int i = 0; i < pixels.length && startIndex + i < alignedPixels.length; i++) {
			alignedPixels[startIndex + i] = pixels[i];
		}
		
		return alignedPixels;
	}

	//for real time interpolation
	public void fillRoiArrayInterpolate() {
		
		System.out.println(recordedRois);
		Roi.removeRoiListener(this);
		
		if(!recordedRois.isEmpty()){
			if(recordedRois.size() == 1) { //nothing to interpolate
				for (Roi roi : recordedRois.values()) {
					for(int i = 1; i < interpolatedRois.length; i++) {
						interpolatedRois[i] = roi;
					}
				}
			}
			else {
				
				List<Integer> sortedRois = new ArrayList<Integer>(recordedRois.keySet());
				Collections.sort(sortedRois);
				
				int firstKeyFrame = sortedRois.get(0);
				int lastKeyFrame = sortedRois.get(sortedRois.size()-1);
				
				for (int frame : sortedRois) {
					interpolatedRois[frame] = recordedRois.get(frame);
				}
				
				interpolatedRois[1] = interpolatedRois[firstKeyFrame];
				
				interpolatedRois[numFrames] = interpolatedRois[lastKeyFrame];
				
				if(!sortedRois.contains(1)) {
					sortedRois.add(1);
				}
				if(!sortedRois.contains(numFrames)) {
					sortedRois.add(numFrames);
				}
				
				Collections.sort(sortedRois);
				
				ListIterator<Integer> roiIterator = sortedRois.listIterator();
				
				int currentFrame = 1;
				int nextFrame = 1;
					
				while (roiIterator.hasNext()) {
					
					currentFrame = roiIterator.next();
					System.out.println("current frame: " + currentFrame);
					Roi[] currentInterpolation = {interpolatedRois[currentFrame]};
					
					if (roiIterator.hasNext()) {
						nextFrame = roiIterator.next();
						System.out.println("next frame: " + nextFrame);
						currentInterpolation = interpolateRoi(interpolatedRois[currentFrame], interpolatedRois[nextFrame], currentFrame, nextFrame);
						roiIterator.previous();
					}
					
					for (int i = 0; i <currentInterpolation.length; i++) {
						interpolatedRois[currentFrame + i] = currentInterpolation[i];
					}
					currentFrame = nextFrame;
				}
			}
		}
		Roi.addRoiListener(this);
	}
	
	//for no real time interpolation
	public void fillRoiArray() {
		
		System.out.println(recordedRois);
		Roi.removeRoiListener(this);
		
		if(!recordedRois.isEmpty()) {
			
			if(recordedRois.size() == 1) { //nothing to interpolate
	
				for (Roi roi : recordedRois.values()) {
					for(int i = 1; i < interpolatedRois.length; i++) {
						interpolatedRois[i] = roi;
					}
				}
			}
			
			else {
				
				List<Integer> sortedRois = new ArrayList<Integer>(recordedRois.keySet());
				Collections.sort(sortedRois);
				
				for (int frame : sortedRois) {
					interpolatedRois[frame] = recordedRois.get(frame);
				}
				
				int firstKeyFrame = sortedRois.get(0);
				int lastKeyFrame = sortedRois.get(sortedRois.size()-1);
				
				interpolatedRois[1] = interpolatedRois[firstKeyFrame];
				interpolatedRois[numFrames] = interpolatedRois[lastKeyFrame];
				
				if(!sortedRois.contains(1)) {
					sortedRois.add(1);
				}
				if(!sortedRois.contains(numFrames)) {
					sortedRois.add(numFrames);
				}
				
				Collections.sort(sortedRois);
				
				ListIterator<Integer> roiIterator = sortedRois.listIterator();
				
				int currentFrame = 1;
				int nextFrame = 1;
				
				while (roiIterator.hasNext()) {
					
					currentFrame = roiIterator.next();
					System.out.println("current frame: " + currentFrame);
					
					if (roiIterator.hasNext()) {
						nextFrame = roiIterator.next();
						System.out.println("next frame: " + nextFrame);
						roiIterator.previous();
					}
					
					int dFrame = nextFrame - currentFrame;
					
					for (int i = 0; i < dFrame; i++) {
						interpolatedRois[currentFrame + i] = interpolatedRois[currentFrame];
					}
					currentFrame = nextFrame;
				}
			}
		}
		Roi.addRoiListener(this);
	}
	
	public void assembleKymographInterpolate() {
		
		int lineWidth = promptWidth();
		
		int maxAnchorIndex = 0;
		
		int kymoHeight = numFrames;
		int kymoLength = 0; //use length of longest ROI
		
		//find longest ROI
		for (Roi roi: recordedRois.values()) {
			
			double[] pixels = null;
			
			pixels = averageWidth(image, roi, lineWidth);
			
			Polygon roiPolygon = roi.getPolygon();
			
			int[] x = roiPolygon.xpoints;
			int[] y = roiPolygon.ypoints;
			int n = roiPolygon.npoints;
			
			double lengthBeforeAnchor = 0;
			
			for(int i = 0; i < anchorID; i++) {
				int dx = x[i + 1] - x[i];
				int dy = y[i + 1] - y[i];
				lengthBeforeAnchor += Math.sqrt(dx*dx+dy*dy);
			}
			
			if(lengthBeforeAnchor > maxAnchorIndex) {
				maxAnchorIndex = (int) lengthBeforeAnchor;
			}
						
			int length = pixels.length;
			if (length > kymoLength){
				kymoLength = length;
			}
		}
		
		ImageProcessor kymo;
		
		if(imageType == ImagePlus.GRAY8) {
			kymo = new ByteProcessor(kymoLength, kymoHeight);
		}
		else if(imageType == ImagePlus.GRAY16) {
			kymo = new ShortProcessor(kymoLength, kymoHeight);
		}
		else if(imageType  == ImagePlus.GRAY32) {
			kymo = new FloatProcessor(kymoLength, kymoHeight);
		}
		else {
			kymo = new ColorProcessor(kymoLength, kymoHeight);
		}
		
		Roi.removeRoiListener(this);
		
		//get pixels on each frame
		for(int frame = 1; frame <= numFrames; frame++){
			image.setSlice(frame);

			Roi currentRoi = interpolatedRois[frame];
			
			double[] pixels = null;
			double[] alignedPixels = new double[kymoLength];

			pixels = averageWidth(image, currentRoi, lineWidth);
			alignedPixels = alignPixels(pixels, kymoLength, maxAnchorIndex, currentRoi);
			
			for(int i = 0; i < alignedPixels.length && i < kymoLength; i++){
				//kymo.putPixelValue(i, frame, alignedPixels[i]);
				putPixel(kymo, i, frame, alignedPixels[i]);
			}
		}
		
		//display final kymograph
		ImagePlus kymoToDisplay = new ImagePlus("Kymograph", kymo);
		kymoToDisplay.show();
		
		System.out.println(recordedRois);
		
		Roi.addRoiListener(this);
	}
	
	
	public void putPixel(ImageProcessor ip, int x, int y, double value) {
		
		if (imageType == ImagePlus.GRAY8 || imageType == ImagePlus.GRAY16 || imageType == ImagePlus.GRAY32) {
			ip.putPixelValue(x, y, value);
		}
		else {
			((ColorProcessor) ip).putPixel(x, y, (int) value);
		}
	}
	
	@Override
	public void roiModified(ImagePlus imp, int id) {
		
		if(imp == image) {
			
			String type = "UNKNOWN";
			
	        switch (id) {
		        case CREATED: type="CREATED";
		        	break;
		        case MOVED: type="MOVED";
		        	break;
		        case MODIFIED: type="MODIFIED";
		        	break;
		        case EXTENDED: type="EXTENDED";
		        	break;
		        case COMPLETED: type="COMPLETED";
		        	break;
		        case DELETED: type="DELETED";
		        	break;
	        }
	        
	        if (id == MODIFIED || id == COMPLETED || id == MOVED) {
	        	
	        	Roi currentRoi = imp.getRoi();
	        	int currentFrame = imp.getCurrentSlice();
	        	
	        	IJ.log("ROI event: " + type);
	        	
	        	if(currentRoi == null) {
	        		IJ.error("RoiListener error: no ROI to record");
	        	}
	        	else if (currentRoi.getType() == Roi.POLYLINE) {
	        		//record as key frame
	        		Roi toPut = (Roi) currentRoi.clone();
	    		
	    			IJ.log("Frame: " + currentFrame + " record ROI: " + currentRoi);
	    			recordedRois.put(currentFrame, toPut);
	    			fillRoiArrayInterpolate();
	        	}
	        	else {
	        		IJ.error("RoiListener error: please use polyline tool");
	        	}
	        }
	        
	        else {
	        	IJ.log("Did not record ROI event: " + type);
	        }	
		}
	}

	private int promptWidth() {
		int lineWidth = (int) IJ.getNumber("Line Width", 1);
		
		if (lineWidth == IJ.CANCELED) {
			IJ.error("Canceled");
		}
		else if (lineWidth % 2 == 0) {
			IJ.error("Please enter odd line width");
		}
		
		return lineWidth;
	}
	
	@Override
	public void imageUpdated(ImagePlus ip) {

		int currentFrame = image.getCurrentSlice();
		IJ.log("Frame: " + currentFrame + ", change ROI to: " + interpolatedRois[currentFrame]);
		image.setRoi(interpolatedRois[currentFrame]);
	}
	
	@Override
	public void imageClosed(ImagePlus ip) {}

	@Override
	public void imageOpened(ImagePlus arg0) {}

	@Override
	public void keyPressed(KeyEvent e) {
		
		int keyCode = e.getKeyCode();
		
		if(keyCode == 17) { //if ctrl is pressed
        	
        	Point cursorLoc = canvas.getCursorLoc();
        	Roi currentRoi = image.getRoi();
        	IJ.log("cursorLoc " + cursorLoc + " roi " + currentRoi);
        	if (currentRoi != null) {
    			if(currentRoi.isHandle(canvas.screenX(cursorLoc.x), canvas.screenY(cursorLoc.y)) != -1) {
    				if(anchorExists) {
    					if(currentRoi.isHandle(canvas.screenX(cursorLoc.x), canvas.screenY(cursorLoc.y)) == anchorID) {
    						IJ.log("don't move anchor");
    					}
    					else {
    						IJ.log("mouse on handle: " + currentRoi.isHandle(canvas.screenX(cursorLoc.x), canvas.screenY(cursorLoc.y)) + " at location: " + cursorLoc.x + " , " + cursorLoc.y);
    					}
    				}
    				else {
    					updateAnchor(currentRoi.isHandle(canvas.screenX(cursorLoc.x), canvas.screenY(cursorLoc.y)));
    					IJ.log("set handleID: " + anchorID + " at location: " + cursorLoc.x + " , " + cursorLoc.y);
    				}      	
    			}
        	}
        	else {
        		IJ.error("no ROI/handle");
        	}
        }
        else {
        	IJ.getInstance().keyPressed(e);
        }
		
	}
	
	public void updateAnchor(int newAnchorID) {
		
		anchorID = newAnchorID;
		anchorExists = true;
		
		anchorLabel.setText("Anchor point set to: " + (anchorID + 1));
		anchorLabel.setSize(anchorLabel.getPreferredSize());
	}
	
	public void resetAnchor() {
		
		anchorID = 0;
		anchorExists = false;
		
		anchorLabel.setText("Anchor not set");
		anchorLabel.setSize(anchorLabel.getPreferredSize());
	}
	
	@Override
	public void windowClosed(WindowEvent e) {
		IJ.log("Plugin closed");
		removeListeners();
		frame = null;
	}
	
	@Override
	public void keyReleased(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void keyTyped(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub
		Point cursorLoc = canvas.getCursorLoc();
    	Roi currentRoi = image.getRoi();
    	IJ.log("cursorLoc " + cursorLoc + " roi " + currentRoi);
    	if (currentRoi != null) {
			if(currentRoi.isHandle(canvas.screenX(cursorLoc.x), canvas.screenY(cursorLoc.y)) != -1) {
				if(anchorExists) {
					if(currentRoi.isHandle(canvas.screenX(cursorLoc.x), canvas.screenY(cursorLoc.y)) == anchorID) {
						IJ.log("don't move anchor");
					}
					else {
						IJ.log("mouse on handle: " + currentRoi.isHandle(canvas.screenX(cursorLoc.x), canvas.screenY(cursorLoc.y)) + " at location: " + cursorLoc.x + " , " + cursorLoc.y);
					}
				}
				else {
					updateAnchor(currentRoi.isHandle(canvas.screenX(cursorLoc.x), canvas.screenY(cursorLoc.y)));
					IJ.log("set handleID: " + anchorID + " at location: " + cursorLoc.x + " , " + cursorLoc.y);
				}      	
			}
    	}
    	else {
    		IJ.error("no ROI/handle");
    	}
		canvas.removeMouseListener(this);
		frame.removeMouseListener(this);
		
		if (!anchorExists) {
			resetAnchor();
		}
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseExited(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseReleased(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void windowClosing(WindowEvent e) {
	}
}
