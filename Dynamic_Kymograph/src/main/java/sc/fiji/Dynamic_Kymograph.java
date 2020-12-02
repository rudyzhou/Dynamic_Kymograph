/* This project is licensed under the GNU General Public License v3 (GPLv3).
 * A copy of the license can be found at the following link: <https://www.gnu.org/licenses/gpl-3.0.en.html>
 */

package sc.fiji;

import java.awt.Frame;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Button;
import java.awt.Color;
import java.awt.FlowLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

import ij.IJ;
import ij.ImageJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;

import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
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

/**
 * Dynamic_Kymograph.java
 * Purpose: imageJ plugin to generate kymographs using key framing and linear interpolation
 *
 * @author Rudy Zhou
 * @version v1.0
 */
@SuppressWarnings("serial")
public class Dynamic_Kymograph extends PlugInFrame implements PlugIn, ActionListener, ImageListener, RoiListener, KeyListener, MouseListener {
	
	//for UI window
	private Frame frame;
	private Label anchorLabel;
	
	//image properties
	protected ImagePlus image;
	private int numFrames;
	private ImageWindow window;
	private ImageCanvas canvas;
	private int imageType;
	
	//for Saved ROIs window
	private ImagePlus savedRois;
	private Overlay overlayRois;
	
	//maintains the "edited" polylines that the user inputs
	private HashMap<Integer, Roi> recordedRois = new HashMap<Integer, Roi>();
	private Roi[] interpolatedRois;
	
	//anchor point properties
	private int anchorID;
	private boolean anchorExists;
	static final int ANCHOR_KEY = 17; //set to "ctrl" key
	
	/**
	 * Runs the plugin. Initializes UI windows and begins listeners for user input.
	 *
	 * @param arg0 not used.
	 *
	 * @return void.
	 */
	public void run(String arg0) {
		
		//assume stack already opened; get the associated image parameters
		image = IJ.getImage();
		window = image.getWindow();
		canvas = image.getCanvas();
		numFrames = image.getImageStackSize();
		imageType = image.getType();

		//initialize Saved ROIs window as copy of first frame of image. Set up the overlay, which is used to store and display multiple ROIs
		savedRois = new ImagePlus("Saved ROIS",  image.getStack().getProcessor(1));
		savedRois.show();
		overlayRois = new Overlay();
		savedRois.setOverlay(overlayRois);
		
		//indexed by frames 1 through numFrames
		interpolatedRois = new Roi[numFrames + 1];
		
		anchorID = 0;
		anchorExists = false;
		
		removeListeners();
		addListeners();
	}

	/**
	 * Constructs the UI window. Initializes buttons and adds appropriate listeners.
	 * Note this method is ran whenever the plugin is ran.
	 */
	public Dynamic_Kymograph() {
		
		super("Dynamic Kymograph");
		if(frame != null) {
			WindowManager.toFront(frame);
			return;
		}
		if (IJ.isMacro()) {
			return;
		}
		
		//set up the UI frame
		frame = this;
		WindowManager.addWindow(this);
		
		frame.setVisible(true);
		frame.setTitle("Dynamic Kymograph");
		frame.setSize(500, 100);
		
		Panel mainPanel = new Panel();
		
		mainPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		
		Button anchorButton = new Button("Select anchor point");
		anchorButton.addActionListener(this);
		mainPanel.add(anchorButton);
		
		Button kymographButton = new Button("Make kymograph");
		kymographButton.addActionListener(this);
		mainPanel.add(kymographButton);
		
		Button saveCurrentRoi = new Button("Save current ROI");
		saveCurrentRoi.addActionListener(this);
		mainPanel.add(saveCurrentRoi);
		
		Button resetKeyFramesButton = new Button("Reset key frames");
		resetKeyFramesButton.addActionListener(this);
		mainPanel.add(resetKeyFramesButton);
		
		anchorLabel = new Label("Anchor not set");
		anchorLabel.setSize(anchorLabel.getPreferredSize());
		mainPanel.add(anchorLabel);
	
		frame.add(mainPanel);	
	}
	
	/**
	 * Used for testing in Java environment (not in imageJ.) Starts an instance of imageJ, opens a test image, and runs the plugin on the test image.
	 * Note that the directory of the test image must be changed to run on your own computer.
	 *
	 *@param args not used.
	 *
	 * @return void.
	 */
	public static void main(String[] args) {
		
		Class<?> clazz = Dynamic_Kymograph.class;
		
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring("file:".length(), url.length() - clazz.getName().length() - ".class".length());
		System.setProperty("plugins.dir", pluginsDir);

		// start ImageJ
		new ImageJ();

		// open example stack
		ImagePlus image = IJ.openImage("D:/Users/rudyz/Documents/Graduate3/biology/errors/newtest/newtest.tiff");	//TODO in general will need to change this file path
		image.show();

		// run the test plugin
		IJ.runPlugIn(clazz.getName(), "");
	}
	
	/**
	 * Generates a kymograph using interpolated frames.
	 *
	 * @return void.
	 */
	private void makeKymograph() {
		
		if(recordedRois.isEmpty()) {
			IJ.error("No ROIs recorded");
		}
		else {
			assembleKymographInterpolate();
			IJ.log("Making Kymograph");	
		}
	}
	
	/**
	 * Deletes all key frames (and their corresponding interpolated frames) and resets the anchor point.
	 *
	 * @return void.
	 */
	private void resetKeyFrames() {
			
		resetAnchor();
				
		image.deleteRoi();
		
		recordedRois.clear();
		interpolatedRois = new Roi[numFrames + 1];
		
		IJ.log("Reset key frames");
	}
	
	/**
	 * Prompts the user to use the mouse to click on a vertex to select it as the anchor point.
	 *
	 * @throws InterruptedException
	 * 
	 * @return void.
	 */
	private void promptAnchorPoint() throws InterruptedException {
		
		if(anchorExists) {
			IJ.error("promtAnchorPoint error: anchor already exists");
		}
		else {
			
			if(canvas != null && frame != null) {
				
				anchorLabel.setText("Click on a handle to set it as the anchor point");
				anchorLabel.setSize(anchorLabel.getPreferredSize());
				
				//prompt user for mouse input
				canvas.addMouseListener(this);
				frame.addMouseListener(this);
			}
			else {
				IJ.error("promptAnchorPoint error: frame or canvas does not exist");
			}
		}
		
	}
	
	/**
	 * Draws the current interpolated ROI (on the first frame) on the Saved ROIs window in a random color.
	 * Can cycle through random colors by repeatedly calling method.
	 *
	 * @return void.
	 */
	private void saveRoi() {
		
		if (interpolatedRois[1] != null) {
			
			Roi currentRoi = roiCopy((PolygonRoi) interpolatedRois[1]);
			
			if(overlayRois.contains(currentRoi)) { //allows user to cycle through random colors by repeatedly calling saveRoi
				
				int indexToReplace = 0;
				
				while(!overlayRois.get(indexToReplace).equals(currentRoi)) {
					indexToReplace++;
				}
				
				//remove both the ROI and its associated number from the overlay
				overlayRois.remove(indexToReplace + 1);
				overlayRois.remove(indexToReplace);
			}
			
			Polygon currentRoiPoly = currentRoi.getPolygon();
			int x = currentRoiPoly.xpoints[0];
			int y = currentRoiPoly.ypoints[0];
			
			//create number by the first vertex of the ROI
			TextRoi number = new TextRoi(x, y, Integer.toString(overlayRois.size()/2 + 1));
			
			Random rand = new Random();
			Color randomColor = new Color(rand.nextFloat(), rand.nextFloat(), rand.nextFloat());
			
			number.setStrokeColor(randomColor);
			currentRoi.setStrokeColor(randomColor);
			
			overlayRois.add(currentRoi);
			overlayRois.add(number);
			
			savedRois.updateAndDraw();
			savedRois.flatten();	//note that flattening might matter if you save the savedROIs image as something other than a .tif (like .png or .jpeg or something) to make sure the drawn lines and numbers appear
			
			savedRois.changes = true;	//so that imageJ will ask you if you want to save the image if you try to close the savedROIs window
		}
		else {
			IJ.error("saveRoi error: no ROI selected");
		}

	}
	
	/**
	 * Shows short message describing the plugin.
	 *
	 * @return void.
	 */
	public void showAbout() {
		IJ.showMessage("Dyamic Kymograph",
			"Plugin to generate kymographs using key framing and linear interpolation"
		);
	}

	/**
	 * For UI button presses. Calls the respective method for each button.
	 *
	 * @return void.
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		
		String label = e.getActionCommand();
		
		if (label == "Make kymograph") {
			makeKymograph();
		}
		else if (label == "Reset key frames") {
			resetKeyFrames();
		}
		else if (label == "Select anchor point") {
			try {
				promptAnchorPoint();
			} catch (InterruptedException e1) {
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
	
	/**
	 * Adds listeners for key framing and anchor point selection.
	 *
	 * @return void.
	 */
	private void addListeners() {
		
		if(window != null) {
			window.addKeyListener(this);
		}
		
		if(canvas != null) {
			canvas.addKeyListener(this);
		}
		
		
		ImagePlus.addImageListener(this);

		Roi.addRoiListener(this);
		
		IJ.log("added listeners");
	}
	
	/**
	 * Removes listeners for key framing and anchor point selection.
	 *
	 * @return void.
	 */
	private void removeListeners() {
        
		if (window!=null) {
            window.removeKeyListener(this);
        }
		
        if (canvas!=null) {
            canvas.removeKeyListener(this);
        }
		
        ImagePlus.removeImageListener(this);
        
        Roi.removeRoiListener(this);
        
        IJ.log("removed listeners");
	}
	
	/***************************************************************************************
	*    Title: MultipleKymograph_ source code (modified "getIrregularProfile" method)
	*    Author: J. Rietdorf and A. Seitz
	*    Date: 2008
	*    Code version: 3.0.1
	*    Availability: https://github.com/fiji/Multi_Kymograph/releases/tag/Multi_Kymograph-3.0.1
	*
	***************************************************************************************/
	/**
	 * Slightly modified from MultipleKymograph plugin.
	 * Walks (from start to end) on a polyline ROI to get the pixels along the way.
	 * Note implementation of "shift" is naive and is not perpendicular to polyline.
	 * 
	 *
	 * @param roi the polyline ROI to walk along
	 * @param imp the image (on the appropriate frame) that the ROI is associated with
	 * @param shift used to implement line width. Shifts the polyline up or down
	 * 
	 * @return Array of pixels along the ROI. Length of the array is roughly the length of the ROI.
	 */
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
	        				values[index] = getPixel(ip, rx, ry);	//modified to handle colored images
	        			rx += xinc;
	     	   			ry += yinc;
	    		}
	    	distance += len;
	    	leftOver = len2 - n2;
		}
	
		return values;
	}	
	
	/**
	 * Returns the pixel value at specified coordinates. Returns greyscale or ARGB pixel depending on image type.
	 *
	 * @param ip the image (on a specific frame)
	 * @param x x coordinate  
	 * @param y y coordinate
	 * 
	 * @return interpolated greyscale or ARGB pixel located at coordinates (x,y).
	 */
	private double getPixel(ImageProcessor ip, double x, double y) {
		
		if (imageType == ImagePlus.GRAY8 || imageType == ImagePlus.GRAY16 || imageType == ImagePlus.GRAY32) {
			return ip.getInterpolatedValue(x, y);
		}
		else {
			return ((ColorProcessor) ip).getInterpolatedRGBPixel(x, y);
		}
	}
	
	/**
	 * Implements line width by averaging over multiple pixel arrays obtained by changing the "shift" parameter in "getPixelsPolyline."
	 * 
	 * @param imp the image (on the appropriate frame)
	 * @param roi the polyline ROI to walk along
	 * @param lineWidth the number of "shifts" to average over
	 *
	 * @return elementwise average of the shifted pixel arrays.
	 */
	public double[] averageWidth(ImagePlus imp, Roi roi, int lineWidth) {
		
		double[] pixels = getPixelsPolyline(roi, imp, 0);
		
		if (imageType == ImagePlus.GRAY8 || imageType == ImagePlus.GRAY16 || imageType == ImagePlus.GRAY32) {
			
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
		}
		
		//TODO figure out what to do for colored kymographs. Current implementation is the same as for greyscale images, but this is not correct.
		else {
			
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
		}
		
		return pixels;
	}
	
	/**
	 * Helper method for "averageWidth". Used to convert a color represented by a ARGB int into 3 RGB values.
	 * Experimental. (I don't know if this works).
	 * 
	 * @param argb a color represented by a ARGB int
	 * 
	 * @return array of 3 integers (representing red, blue, and green).
	 */
	private int[] ARGBtoRGB(int argb) {
		//TODO not sure if this works
		int[] rgb = new int[3];
		int a = (argb >> 24) & 0xFF;
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		
		rgb[0] = a * r;
		rgb[1] = a * g;
		rgb[2] = a * b;
		
		return rgb;
	}
	
	/**
	 * Helper method for "fillRoiArrayInterpolate". Interpolates between two key frames represented by (startRoi, startFrame) and (endRoi, endFrame).
	 * Note that this method assumes both ROIs are polylines with the same number of points.
	 * 
	 * @param startRoi the polyline ROI recorded on startFrame
	 * @param endRoi the polyline ROI recorded on endFrame
	 * @param startFrame frame number associated with startRoi
	 * @param endFrame frame number associated with endRoi
	 * 
	 * @return array of interpolated ROIs such that the the i-th entry is the interpolated ROI on frame "startFrame + i"
	 */
	private Roi[] interpolateRoi(Roi startRoi, Roi endRoi, int startFrame, int endFrame) {
		
		int dFrame = endFrame - startFrame;
		
		FloatPolygon startPolygon = startRoi.getFloatPolygon();
		FloatPolygon endPolygon = endRoi.getFloatPolygon();
		
		int startN = startPolygon.npoints;
		float[] startX = startPolygon.xpoints;
		float[] startY = startPolygon.ypoints;
		
		int endN = endPolygon.npoints;
		float[] endX = endPolygon.xpoints;
		float[] endY =  endPolygon.ypoints;
		
		if(startN != endN) {
			IJ.error("interpolateRoi: polylines must have same number of points");
			return null;
		}
		
		else {
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
	}
	
	/**
	 * Helper method for "assembleKymographInterpolate". Aligns a slice of the kymograph based on the position of the anchor point.
	 * 
	 * @param pixels the kymograph slice (pixels along a ROI after averaging for line width) to align
	 * @param kymoWidth the length of the longest slice (the longest ROI) in the entire kymograph
	 * @param the index of the anchor point in the pixel array of the longest kympgraph slice
	 * @param roi the ROI used for this kymograph slice
	 * 
	 * @return a pixel array of size kymoWidth such that the anchor point of the input pixel array is aligned with indexToMatch
	 */
	private double[] alignPixels(double[] pixels, int kymoWidth, int indexToMatch, Roi roi) {
	
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
		
		int startIndex = 0;	//defined such that startIndex + lengthBeforeAnchor = indexToMatch so that the anchor point is aligned with the indexToMatch
		if (indexToMatch - anchorIndex > 1) {
			startIndex = indexToMatch - anchorIndex;
		}
		
		for(int i = 0; i < pixels.length && startIndex + i < alignedPixels.length; i++) {
			alignedPixels[startIndex + i] = pixels[i];
		}
		
		return alignedPixels;
	}

	/**
	 * Iterates through the current collection of recorded key frames and fills in the between frames with interpolated ROIs. For real time interpolation.
	 */
	public void fillRoiArrayInterpolate() {
		
		System.out.println(recordedRois);
		Roi.removeRoiListener(this);
		
		if(!recordedRois.isEmpty()){
			if(recordedRois.size() == 1) { //nothing to interpolate
				for (Roi roi : recordedRois.values()) {
					for(int i = 1; i < interpolatedRois.length; i++) {
						interpolatedRois[i] = roiCopy((PolygonRoi) roi);
					}
				}
			}
			else {
				//TODO this implementation is relatively inefficient. Could make more elegant and efficient with better data structure (see KeyFrames)
				List<Integer> sortedRois = new ArrayList<Integer>(recordedRois.keySet());
				Collections.sort(sortedRois);
				
				int firstKeyFrame = sortedRois.get(0);
				int lastKeyFrame = sortedRois.get(sortedRois.size()-1);
				
				for (int frame : sortedRois) {
					interpolatedRois[frame] = roiCopy((PolygonRoi) recordedRois.get(frame));
				}
				
				interpolatedRois[1] = roiCopy((PolygonRoi) interpolatedRois[firstKeyFrame]);	//propagate first key frame to beginning of image stack
				
				interpolatedRois[numFrames] = roiCopy((PolygonRoi) interpolatedRois[lastKeyFrame]); 	//propagate last key frame to end of image stack
				
				//the "if" statements ensure that there will be no interpolation before the first key frame and after the last key frame
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
					Roi[] currentInterpolation = {roiCopy((PolygonRoi) interpolatedRois[currentFrame])};
					
					if (roiIterator.hasNext()) {
						nextFrame = roiIterator.next();
						System.out.println("next frame: " + nextFrame);
						currentInterpolation = interpolateRoi(interpolatedRois[currentFrame], interpolatedRois[nextFrame], currentFrame, nextFrame);	//interpolate between currentFrame and nextFrame
						roiIterator.previous();
					}
					
					for (int i = 0; i <currentInterpolation.length; i++) {
						interpolatedRois[currentFrame + i] = roiCopy((PolygonRoi) currentInterpolation[i]);	//fill in the interpolated frames between currentFrame and nextFrame
					}
					
					currentFrame = nextFrame;
				}
			}
		}
		Roi.addRoiListener(this);
	}
	
	/**
	 * Iterates through the current collection of recorded key frames and fills in the between frames with the most recent key frame. No interpolation.
	 * Note that this method is not currently used by the plugin.
	 */
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
					System.out.println("current roi: " + recordedRois.get(currentFrame));
					
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
	
	public void fillRoiArrayInterpolateTest() {
		System.out.println(recordedRois);
		Roi.removeRoiListener(this);
		
		if(!recordedRois.isEmpty()) {
			
			interpolatedRois = new Roi[numFrames + 1];
			
			int firstKeyFrame = Integer.MAX_VALUE;
			int lastKeyFrame = Integer.MIN_VALUE;
			
			for(int key: recordedRois.keySet()) {
				if(key < firstKeyFrame) {
					firstKeyFrame = key;
				}
				if(key > lastKeyFrame) {
					lastKeyFrame = key;
				}
			}
			
			//System.out.println("first: " + firstKeyFrame);
			//System.out.println("last: " + lastKeyFrame);
			
			for(int frame = 1; frame <= firstKeyFrame; frame++) {
				interpolatedRois[frame] = roiCopy((PolygonRoi) recordedRois.get(firstKeyFrame));
			}
			
			for(int frame = lastKeyFrame; frame <= numFrames; frame++) {
				interpolatedRois[frame] = roiCopy((PolygonRoi) recordedRois.get(lastKeyFrame));
			}
			
			int currentKeyFrame = firstKeyFrame;
			int nextKeyFrame = lastKeyFrame;
			Roi[] currentInterpolation;
			
			for(int frame = firstKeyFrame + 1; frame < lastKeyFrame; frame++) {
				if (recordedRois.containsKey(frame)) {
					
					nextKeyFrame = frame;
					currentInterpolation = interpolateRoi(roiCopy((PolygonRoi) recordedRois.get(currentKeyFrame)), roiCopy((PolygonRoi) recordedRois.get(nextKeyFrame)), currentKeyFrame, nextKeyFrame);
					
					for (int i = 0; i <currentInterpolation.length; i++) {
						interpolatedRois[currentKeyFrame + i] = currentInterpolation[i];	//fill in the interpolated frames between currentFrame and nextFrame
					}
					
					currentKeyFrame = nextKeyFrame;
				}
				
			}
		}
		Roi.addRoiListener(this);
	}
	
	public void fillRoiArrayTest() {
		System.out.println(recordedRois);
		Roi.removeRoiListener(this);
		
		interpolatedRois = new Roi[numFrames + 1];
		
		if(!recordedRois.isEmpty()) {
			
			int firstKeyFrame = Integer.MAX_VALUE;
			int lastKeyFrame = Integer.MIN_VALUE;
			
			for(int key: recordedRois.keySet()) {
				if(key < firstKeyFrame) {
					firstKeyFrame = key;
				}
				if(key > lastKeyFrame) {
					lastKeyFrame = key;
				}
			}
			
			System.out.println("first: " + firstKeyFrame);
			System.out.println("last: " + lastKeyFrame);
			
			for(int frame = 1; frame <= firstKeyFrame; frame++) {
				interpolatedRois[frame] = roiCopy((PolygonRoi) recordedRois.get(firstKeyFrame));
			}
			
			for(int frame = lastKeyFrame; frame <= numFrames; frame++) {
				interpolatedRois[frame] = roiCopy((PolygonRoi) recordedRois.get(lastKeyFrame));
			}
			
			int currentKeyFrame = firstKeyFrame;
			
			for(int frame = firstKeyFrame + 1; frame < lastKeyFrame; frame++) {
				if (recordedRois.containsKey(frame)) {
					currentKeyFrame = frame;
				}
				interpolatedRois[frame] = roiCopy((PolygonRoi) recordedRois.get(currentKeyFrame));
			}
		}
		Roi.addRoiListener(this);
	}
	
	public void showKeyFrames() {
		
		System.out.println(recordedRois);
		Roi.removeRoiListener(this);
		
		if(!recordedRois.isEmpty()) {
				
				for (int frame : recordedRois.keySet()){
					interpolatedRois[frame] = recordedRois.get(frame);
			}
		}
		Roi.addRoiListener(this);
	}
	
	/**
	 * Assembles and displays a kymograph generated by interpolating between all key frames (uses the ROIs in the "interpolatedRois" array)
	 */
	public void assembleKymographInterpolate() {
		
		int lineWidth = promptWidth();
		
		int maxAnchorIndex = 0;
		
		int kymoHeight = numFrames;
		int kymoLength = 0; //use length of longest ROI
		
		//find longest ROI and the ROI with the furthest distance to the anchor point
		for (Roi roi: recordedRois.values()) {
			
			double[] pixels = null;
			
			pixels = averageWidth(image, roi, lineWidth);
			
			Polygon roiPolygon = roi.getPolygon();
			
			int[] x = roiPolygon.xpoints;
			int[] y = roiPolygon.ypoints;
			
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
				putPixel(kymo, i, frame, alignedPixels[i]);
			}
		}
		
		//display final kymograph
		ImagePlus kymoToDisplay = new ImagePlus("Kymograph", kymo);
		kymoToDisplay.show();
		
		System.out.println(recordedRois);
		
		Roi.addRoiListener(this);
	}
	
	/**
	 * Helper method for "assembleKymographInterpolate". Used to place a pixel value at location (x,y) in the kymograph.
	 * 
	 * @param ip the ImageProcessor of the kymograph
	 * @param x the x-coordinate in the kymograph
	 * @param y the y-coordinate in the kymograph
	 * @param value the pixel value (either greyscale or ARGB) to place at (x,y)
	 */
	private void putPixel(ImageProcessor ip, int x, int y, double value) {
		
		if (imageType == ImagePlus.GRAY8 || imageType == ImagePlus.GRAY16 || imageType == ImagePlus.GRAY32) {
			ip.putPixelValue(x, y, value);
		}
		else {
			((ColorProcessor) ip).putPixel(x, y, (int) value);
		}
	}
	
	/**
	 * Notified by RoiListener when an event occurs. Used to record and update key frames when a ROI is modified.
	 * 
	 * @param imp the image associated with the ROI that was modified
	 * @param id the type of ROI event
	 */
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
	        	
	        	Roi currentRoi =imp.getRoi();
	        	int currentFrame = imp.getCurrentSlice();
	        	
	        	IJ.log("ROI event: " + type);
	        	
	        	if(currentRoi == null) {
	        		IJ.error("RoiListener error: no ROI to record");
	        	}
	        	else if (currentRoi.getType() == Roi.POLYLINE) {
	        		//record as key frame
	        		//Roi toPut = (Roi) currentRoi.clone();
	        		Roi toPut = roiCopy((PolygonRoi) currentRoi);
	        		
	    			IJ.log("Frame: " + currentFrame + " record ROI: " + currentRoi);
	    			
	    			recordedRois.put(currentFrame, toPut);
	    			
	    			fillRoiArrayInterpolate();
	    			//fillRoiArrayInterpolateTest();
	    			//fillRoiArray();
	    			//fillRoiArrayTest();    			
	    			//showKeyFrames();
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

	private PolygonRoi roiCopy(PolygonRoi toCopy) {
		
		FloatPolygon polyToCopy = toCopy.getFloatPolygon();
		
		float[] xToCopy = polyToCopy.xpoints;
		float[] yToCopy = polyToCopy.ypoints;
		int numToCopy = polyToCopy.npoints;
		
		return new PolygonRoi(xToCopy, yToCopy, numToCopy, Roi.POLYLINE);
	}
	/**
	 * Prompts the user to input an integer for the line width.
	 * 
	 * @return the line width entered by the user.
	 */
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
	
	/**
	 * Notified by ImageListener when an image is updated. Used to track frame changes to update the ROI drawn on each frame.
	 * 
	 * @param ip the image that was updated
	 */
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

	/**
	 * Notified by KeyListener when an event occurs. Used to set anchor point. 
	 * 
	 * @param e the KeyEvent generated by a key press
	 */
	@Override
	public void keyPressed(KeyEvent e) {
		
		int keyCode = e.getKeyCode();
		
		if(keyCode == ANCHOR_KEY) { //if ctrl is pressed
        	
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
    					updateAnchor(currentRoi.isHandle(canvas.screenX(cursorLoc.x), canvas.screenY(cursorLoc.y)));	//set anchor point to current mouse location if it's on a vertex
    					IJ.log("set handleID: " + anchorID + " at location: " + cursorLoc.x + " , " + cursorLoc.y);
    				}      	
    			}
        	}
        	else {
        		
        		IJ.error("no ROI/handle");
        	}
        }
        else {
        	IJ.getInstance().keyPressed(e);	//pass the key press to imageJ (in case ANCHOR_KEY is used for some other action)
        }
		
	}
	
	@Override
	public void keyReleased(KeyEvent e) {}

	@Override
	public void keyTyped(KeyEvent e) {}
	
	/**
	 * Sets/updates the anchor point. Also updates the anchor status message.
	 * 
	 * @param newAnchorID the new value for the anchorID
	 */
	public void updateAnchor(int newAnchorID) {
		
		anchorID = newAnchorID;
		anchorExists = true;
		
		anchorLabel.setText("Anchor point set to: " + (anchorID + 1));
		anchorLabel.setSize(anchorLabel.getPreferredSize());
	}
	
	/**
	 * Resets the anchor point back to default (the first vertex drawn - indexed by 0.) Also updates the anchor status message. 
	 */
	public void resetAnchor() {
		
		anchorID = 0;
		anchorExists = false;
		
		anchorLabel.setText("Anchor not set");
		anchorLabel.setSize(anchorLabel.getPreferredSize());
	}
	
	/**
	 * Notified by WindowListener when the plugin UI is closed. Cleans up the plugin so that it can be ran again. 
	 * 
	 * @param e the WindowEvent corresponding to closing the UI window
	 */
	@Override
	public void windowClosed(WindowEvent e) {
		IJ.log("Plugin closed");
		removeListeners();
		frame = null;
	}

	/**
	 * Notified by MouseListener when the mouse is clicked. Used to set the anchor point by clicking on a vertex.
	 * 
	 * @param e the MouseEvent corresponding to the mouse click
	 */
	@Override
	public void mouseClicked(MouseEvent e) {
		
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
					updateAnchor(currentRoi.isHandle(canvas.screenX(cursorLoc.x), canvas.screenY(cursorLoc.y)));	//set anchor point to current mouse location if it's on a vertex
					IJ.log("set handleID: " + anchorID + " at location: " + cursorLoc.x + " , " + cursorLoc.y);
				}      	
			}
    	}
    	else {
    		IJ.error("no ROI/handle");
    	}
    	
    	//remove listeners to end the prompt
		canvas.removeMouseListener(this);
		frame.removeMouseListener(this);
		
		if (!anchorExists) {
			resetAnchor();
		}
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {}

	@Override
	public void mouseExited(MouseEvent arg0) {}

	@Override
	public void mousePressed(MouseEvent arg0) {}

	@Override
	public void mouseReleased(MouseEvent arg0) {}
}
