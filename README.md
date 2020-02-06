# Dynamic_Kymograph

imagej plugin for kymographs with key framing and interpolation

## Prerequisites

This plugin requires [imageJ](https://imagej.net/ImageJ2).
We recommend installing [Fiji](http://fiji.sc/) (a package including imageJ and many plugins.) The rest of this readme will assume that the user is using Fiji.

## Installation

* Download the "Dynamic_Kymograph-(version number).jar" fine on your own computer. (The most recent version of the plugin can be found at [the releases tab](https://github.com/rudyzhou/Dynamic_Kymograph/releases) on the Dynamic_Kymograph GitHub page.)

* Save the file in the Fiji "plugins" folder.

* Restart Fiji, and the plugin should appear in “Process” -> “Dynamic Kymograph”

## Usage

1. In Fiji, open the image stack that you would like to analyze

2. Run the dynamic kymograph plugin (in “Process” -> “Dynamic Kymograph”).

3. The UI has the following features:
  * **Select anchor point** – Prompts the user to click on a vertex to set it as the anchor point (the anchor point status is reflected in the status message below the buttons)
  * **Make kymograph** – Prompts the user for line width and then generates a kymograph using all interpolated key frames and the selected anchor point
  * **Save Current ROI** -  Records and numbers the current ROI on the “Saved ROIs” window. Draws the ROI using a random color
  * **Reset key frames** – Clears all key frames and anchor point
  * **Log window** – Records events such as when a keyframe is generated, when an anchor point is set, when the plugin is closed, etc. Mainly used for debugging
  * **Saved ROIs window** – Displays the first frame of the stack. Any ROIs saved using the “Save Current ROI” button will appear here

4. Once you have surveyed the stack and found a microtubule to analyze, use the polyline tool to trace the microtubule

**Note:** Changing the number of points in a polyline is not currently supported by the plugin,
so this means when you first draw your polyline, you should use enough points so that you
have enough degrees of freedom to track any movements in the microtubule.


5. Make sure that one of the points you drew is located on the seed of the microtubule (the bright spot that remains static throughout the stack.) You may want to scroll through the video to confirm that you have found such a point. Then you can either use the “Select anchor point” button or simply press “ctrl” while your mouse is hovering over the vertex to set this vertex as the anchor point. (You should see a message in the “Log” confirming this)

**Note:** We recommend setting the anchor point as soon as you finish drawing your first polyline. You can move the anchor point after setting it, but you cannot change which point is the anchor point.

6. Once you have your initial polyline and anchor point, you can simply scroll through the video and modify your polyline on any frame to best capture the dynamics of the microtubule (by dragging the polyline vertices or translating the whole polyline using the arrow keys.)

```
More technically, every time you modify the polyline, the plugin will record a new
“key frame.” The plugin automatically interpolates all key frames, so when you scroll
through the video the changes in the polyline will be reflected. 
```

7. Once you watch the video and are happy with your polyline, you can press the “Make kymograph” button to generate your kymograph.

8. If you are not satisfied with your kymograph, you can simply continue editing the polyline as in step 4 and generate a new kymograph.

9. If you want to analyze a new microtubule on the same stack, press “Save current ROI” to keep track of your analyzed microtubule, and then press “Reset key frames” to begin working on the next microtubule.

**Note:** “Save current ROI” will draw your current ROI in a random color on the second
window. If you don’t like the color that is used, you can just keep pressing the “Save current
ROI” button to cycle through random colors until you find one you like. 

10. If you want to close the plugin, press the “x” button on the “Dynamic Kymograph” window


**Note:** If you want to analyze another stack, you must close the plugin and your current
stack, open the new stack, and then re-run the plugin. 


## Authors

* Rudy Zhou - [rudyzhou](https://github.com/rudyzhou)

## License

This project is licensed under the GNU General Public License v3 (GPLv3) - see the [license.md](https://github.com/rudyzhou/Dynamic_Kymograph/blob/master/license.md) file for more information.

## Acknowledgments

* Some code used from [Multiple Kymograph plugin](https://github.com/fiji/Multi_Kymograph/tree/Multi_Kymograph-3.0.1) by J. Rietdorf and A. Seitz
