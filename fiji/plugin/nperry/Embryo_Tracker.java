/** 
 * Author: Nick Perry 
 */

package fiji.plugin.nperry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.plugin.PlugIn;
import ij.*;
import ij.process.ImageProcessor;
import mpicbg.imglib.algorithm.gauss.GaussianConvolutionRealType;
import mpicbg.imglib.algorithm.math.MathLib;
import mpicbg.imglib.algorithm.roi.MedianFilter;
import mpicbg.imglib.cursor.Cursor;
import mpicbg.imglib.cursor.LocalizableByDimCursor;
import mpicbg.imglib.cursor.LocalizableByDimCursor3D;
import mpicbg.imglib.cursor.special.LocalNeighborhoodCursor;
import mpicbg.imglib.cursor.special.LocalNeighborhoodCursor3D;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImagePlusAdapter;
import mpicbg.imglib.image.display.imagej.ImageJFunctions;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyMirrorFactory;
import mpicbg.imglib.type.logic.BitType;
import mpicbg.imglib.type.numeric.RealType;
import mpicbg.imglib.algorithm.roi.StructuringElement;

public class Embryo_Tracker<T extends RealType<T>> implements PlugIn {
	/** Class/Instance variables */
	protected Image<T> img;								// Stores the image used by Imglib
	
	final static byte VISITED = (byte)1;
	
	// <delete me>
	int ox[];
	int oy[];
	int points;
	// </delete me>
	
	
	/** Ask for parameters and then execute. */
	public void run(String arg) {
		// 1 - Obtain the currently active image:
		ImagePlus imp = IJ.getImage();
		if (null == imp) return;
		
		// 2 - Ask for parameters:
		// currently, this information is not used.
		GenericDialog gd = new GenericDialog("Track");
		gd.addNumericField("Average blob diameter (pixels):", 0, 0);  // get the expected blob size (in pixels).
		gd.addChoice("Search type:", new String[] {"Maxima", "Minima"}, "Maxima");  // determines if we are searching for maxima, or minima.
		gd.showDialog();
		if (gd.wasCanceled()) return;

		// 3 - Retrieve parameters from dialogue:
		int diam = (int)gd.getNextNumber();
		String searchType = gd.getNextString();
		
		// 4 - Execute!
		Object[] result = exec(imp, diam, searchType);
		
		// Display (for testing)
		if (null != result) {
			ImagePlus scaled = (ImagePlus) result[1];
			scaled.show();

			IJ.log("outputting points...");
			imp.setRoi(new PointRoi(ox, oy, points));
			imp.updateAndDraw();
		}
	}
	
	/** Execute the plugin functionality: apply a median filter (for salt and pepper noise), a Gaussian blur, and then find maxima. */
	public Object[] exec(ImagePlus imp, int diam, String searchType) {
		// 0 - Check validity of parameters:
		if (null == imp) return null;
		
		// 1 - Set up for use with Imglib:
		img = ImagePlusAdapter.wrap(imp);
		int numDim = img.getNumDimensions();
		
		// 2 - Apply a median filter, to get rid of salt and pepper noise which could be mistaken for maxima in the algorithm:
		/*StructuringElement strel;
		
		// 2.1 - Need to figure out the dimensionality of the image in order to create a StructuringElement of the correct dimensionality (StructuringElement needs to have same dimensionality as the image):
		if (numDim == 3) {  // 3D case
			strel = new StructuringElement(new int[]{3, 3, 1}, "3D Square");  // unoptimized shape for 3D case. Note here that we manually are making this shape (not using a class method). This code is courtesy of Larry Lindsey
			Cursor<BitType> c = strel.createCursor();  // in this case, the shape is manually made, so we have to manually set it, too.
			while (c.hasNext()) 
			{ 
			    c.fwd(); 
			    c.getType().setOne(); 
			} 
			c.close(); 
		} else {  							// 2D case
			strel = StructuringElement.createCube(2, 3);  // unoptimized shape
		}
		
		// 2.2 - Apply the median filter:
		final MedianFilter<T> medFilt = new MedianFilter<T>(img, strel, new OutOfBoundsStrategyMirrorFactory<T>()); 
		// ***note: add back medFilt.checkInput() when it's fixed ***
		if (medFilt.process()) {  // checkInput ensures the input is correct, and process runs the algorithm.
			img = medFilt.getResult(); 
		} else { 
	        System.out.println(medFilt.getErrorMessage()); 
	        return null;
		}
		
		// 3 - Apply a Gaussian filter (code courtesy of Stephan Preibisch). Theoretically, this will make the center of blobs the brightest, and thus easier to find:
		final GaussianConvolutionRealType<T> conv = new GaussianConvolutionRealType<T>(img, new OutOfBoundsStrategyMirrorFactory<T>(), 6.0f); // Use sigma of 10.0f, probably need a better way to do this
		if (conv.checkInput() && conv.process()) {  // checkInput ensures the input is correct, and process runs the algorithm.
			img = conv.getResult(); 
		} else { 
	        System.out.println(conv.getErrorMessage()); 
	        return null;
		}*/
		
		// 3.5 - Apply a Laplace transform?
		
		// 4 - Find maxima of newly convoluted image:
		if (numDim == 2) {
			findMaxima2D(img);
		} else {
			findMaxima3D(img);
		}
		
		// 5 - Return (for testing):
		ImagePlus newImg = ImageJFunctions.copyToImagePlus(img, imp.getType());  	// convert Image<T> to ImagePlus
		if (imp.isInvertedLut()) {													// if original image had inverted LUT, invert this new image's LUT also
			ImageProcessor newImgP = newImg.getProcessor();
			newImgP.invertLut();
		}
		return new Object[]{"new", newImg};
	}
	
	/**
	 * 
	 * @param img
	 */
	public void findMaxima2D(Image<T> img) {
		// 1 - Initialize local variables, cursors
		final LocalizableByDimCursor<T> curr = img.createLocalizableByDimCursor(new OutOfBoundsStrategyMirrorFactory<T>()); // this cursor is the main cursor which iterates over all the pixels in the image.  
		LocalizableByDimCursor<T> local = img.createLocalizableByDimCursor(new OutOfBoundsStrategyMirrorFactory<T>());		// this cursor is used to search a connected "lake" of pixels, or pixels with the same value
		LocalNeighborhoodCursor<T> neighbors = new LocalNeighborhoodCursor<T>(local);										// this cursor is used to search the immediate neighbors of a pixel
		ArrayList< int[] > maxCoordinates = new ArrayList< int[] >();  	// holds the positions of the local maxima
		T currentValue = img.createType();  							// holds the value of the current pixel's intensity. We use createType() here because getType() gives us a pointer to the cursor's object, but since the neighborhood moves the parent cursor, when we check the neighbors, we actually change the object stored here, or the pixel we are trying to compare to. see fiji-devel list for further explanation.
		T neighborValue; 												// holds the value of the neighbor's intensity
		int width = img.getDimensions()[0];								// width of the image. needed for storing info in the visited and visitedLakeMember arrays correctly
		byte visited[] = new byte[img.getNumPixels()];					// stores whether or not this pixel has been searched either by the main cursor, or directly in a lake search.
		LinkedList< int[] > toSearch = new LinkedList< int[] >();		// holds the positions of pixels that belong to the current lake and need to have neighbors searched
		LinkedList< int[] > searched = new LinkedList< int[] >();		// holds the positions of pixels that belong to the current lake and have already had neighbors searched
		boolean isMax;													// flag which tells us whether the current lake is a local max or not
		int nextCoords[] = new int [2];									// keeping the array declarations outside the while loop to improve speed. coords of current lake member.
		int currCoords[] = new int[2];									// coords of outer, main loop
		int neighborCoords[] = new int[2];								// coords of neighbor
		int averagedMaxPos[] = new int[2];								// for a local max lake, this stores the 'center' of the lake's position.

		// 2 - Search all pixels for LOCAL maxima. A local maximum is a pixel that is the brightest in its immediate neighborhood (so the pixel is brighter or as bright as the 26 direct neighbors of it's cube-shaped neighborhood if 3D). If neighboring pixels have the same value as the current pixel, then the pixels are treated as a local "lake" and the lake is searched to determine whether it is a maximum "lake" or not.
		
		// 2.1 - Iterate over all pixels in the image.
		while(curr.hasNext()) { 
			curr.fwd();
			curr.getPosition(currCoords);
			if ((visited[getIndexOfPosition2D(currCoords, width)] & VISITED) == 1) {	// if we've already seen this pixel, then we've already decided if its a max or not, so skip it
				continue;
			}
			isMax = true;  				// this pixel could be a max
			toSearch.add(currCoords);  	// add this initial pixel to the queue of pixels we need to search (currently the only thing in the queue). This queue represents the 'lake;' any group of pixels with the same value are stored here and searched completely.
			
			// 2.2 - Iterate through queue which contains the pixels of the "lake"
			while ((nextCoords = toSearch.poll()) != null) {
				if ((visited[getIndexOfPosition2D(nextCoords, width)] & VISITED) == 1) {	// prevents us from just searching the lake infinitely
					continue;
				} else {	// if we've never seen, add to visited list, and add to searched list.
					visited[getIndexOfPosition2D(nextCoords, width)] |= VISITED;	
					searched.add(nextCoords);
				}
				local.setPosition(nextCoords);		// set the local cursors position to the next member of the lake, so that the neighborhood cursor can search its neighbors.
				currentValue.set(local.getType());  // store the value of this pixel in a variable
				neighbors.update();					// needed to get the neighborhood cursor to function correctly
				
				// 2.3 - Iterate through immediate neighbors
				while(neighbors.hasNext()) {
					neighbors.fwd();
					neighborCoords = local.getPosition();
					neighborValue = neighbors.getType();
					
					// Case 1: neighbor's value is strictly larger than ours, so ours cannot be a local maximum.
					if (neighborValue.compareTo(currentValue) > 0) {
						isMax = false;
					}
					
					// Case 2: neighbor's value is strictly equal to ours, which means we could still be at a maximum, but the max value is a blob, not just a single point. We must check the area. In other words, we have a lake.
					else if (neighborValue.compareTo(currentValue) == 0 && isWithinImageBounds2D(neighborCoords)) {
						toSearch.add(neighborCoords);
					}
				}
				neighbors.reset();  // needed to get the outer cursor to work correctly;
			}
			if (isMax) {	// if we get here, we've searched the entire lake, so find the average point and call that a max by adding to results list
				averagedMaxPos = findAveragePosition2D(searched);
				maxCoordinates.add(averagedMaxPos);
			} else {		// if isMax == false, then everything we've searched is not a max, so just get rid of it.
				searched.clear();
			}
		}
		curr.close();
		neighbors.close();
		
		// 3 - Print out list of maxima, set up for point display (FOR TESTING):
		ox = new int[maxCoordinates.size()];
		oy = new int[maxCoordinates.size()];
		points = maxCoordinates.size();
		int index = 0;
		String img_dim = MathLib.printCoordinates(img.getDimensions());
		IJ.log("Image dimensions: " + img_dim);
		Iterator<int[]> itr = maxCoordinates.iterator();
		while (itr.hasNext()) {
			int coords[] = itr.next();
			ox[index] = coords[0];
			oy[index] = coords[1];
			String pos_str = MathLib.printCoordinates(coords);
			IJ.log(pos_str);
			index++;
		}
	}
	
	public void findMaxima3D(Image<T> img) {
		// **Note** See above 2D version for comments.
		// 1 - Initialize local variables, cursors
		final LocalizableByDimCursor<T> curr = img.createLocalizableByDimCursor(new OutOfBoundsStrategyMirrorFactory<T>());
		LocalizableByDimCursor<T> local = img.createLocalizableByDimCursor(new OutOfBoundsStrategyMirrorFactory<T>());
		LocalNeighborhoodCursor<T> neighbors = new LocalNeighborhoodCursor3D<T>(local);
		ArrayList< int[] > maxCoordinates = new ArrayList< int[] >();
		T currentValue = img.createType();
		T neighborValue;
		int width = img.getDimensions()[0];
		int numPixelsInXYPlane = img.getDimensions()[1] * width;
		byte visited[] = new byte[img.getNumPixels()];
		LinkedList< int[] > toSearch = new LinkedList< int[] >();
		LinkedList< int[] > searched = new LinkedList< int[] >();
		boolean isMax;
		int nextCoords[] = new int [3];
		int currCoords[] = new int[3];
		int neighborCoords[] = new int[3];
		int averagedMaxPos[] = new int[3];

		// 2 - Search all pixels for LOCAL maxima.
		
		// 2.1 - Iterate over all pixels in the image.
		while(curr.hasNext()) { 
			curr.fwd();
			curr.getPosition(currCoords);
			if ((visited[getIndexOfPosition3D(currCoords, width, numPixelsInXYPlane)] & VISITED) == 1) {
				continue;
			}
			isMax = true;
			toSearch.add(currCoords);
			
			// 2.2 - Iterate through queue which contains the pixels of the "lake"		
			while ((nextCoords = toSearch.poll()) != null) {
				if ((visited[getIndexOfPosition3D(nextCoords, width, numPixelsInXYPlane)] & VISITED) == 1) {
					continue;
				} else {
					visited[getIndexOfPosition3D(nextCoords, width, numPixelsInXYPlane)] |= VISITED;	
					searched.add(nextCoords);
				}
				local.setPosition(nextCoords);
				currentValue.set(local.getType());
				neighbors.update();
				
				// 2.3 - Iterate through immediate neighbors
				while(neighbors.hasNext()) {
					neighbors.fwd();
					neighborCoords = local.getPosition();
					neighborValue = neighbors.getType();
					
					// Case 1: neighbor's value is strictly larger than ours, so ours cannot be a local maximum.
					if (neighborValue.compareTo(currentValue) > 0) {
						isMax = false;
					}
					
					// Case 2: neighbor's value is strictly equal to ours, which means we could still be at a maximum, but the max value is a blob, not just a single point. We must check the area.
					else if (neighborValue.compareTo(currentValue) == 0 && isWithinImageBounds3D(neighborCoords)) {
						toSearch.add(neighborCoords);
					}
				}
				neighbors.reset();  // needed to get the outer cursor to work correctly;		
			}
			if (isMax) {
				averagedMaxPos = findAveragePosition3D(searched);
				maxCoordinates.add(averagedMaxPos);
			} else {
				searched.clear();
			}
		}
		curr.close();
		neighbors.close();
		
		// 3 - Print out list of maxima, set up for point display (FOR TESTING):
		ox = new int[maxCoordinates.size()];
		oy = new int[maxCoordinates.size()];
		points = maxCoordinates.size();
		int index = 0;
		String img_dim = MathLib.printCoordinates(img.getDimensions());
		IJ.log("Image dimensions: " + img_dim);
		Iterator<int[]> itr = maxCoordinates.iterator();
		while (itr.hasNext()) {
			int coords[] = itr.next();
			ox[index] = coords[0];
			oy[index] = coords[1];
			String pos_str = MathLib.printCoordinates(coords);
			IJ.log(pos_str);
			index++;
		}
	}
	
	/**
	 * 
	 * @param searched
	 * @return
	 */
	public int[] findAveragePosition2D(LinkedList < int[] > searched) {
		int count = 0;
		int avgX = 0, avgY = 0;
		while(!searched.isEmpty()) {
			int curr[] = searched.poll();
			avgX += curr[0];
			avgY += curr[1];
			count++;
		}
		return new int[] {avgX/count, avgY/count};
	}

	/**
	 * Given a position array, returns whether or not the position is within the bounds of the image, or out of bounds.
	 * 
	 * @param pos
	 * @return
	 */
	public boolean isWithinImageBounds2D(int pos[]) {
		return pos[0] > -1 && pos[0] < img.getDimension(0) && pos[1] > -1 && pos[1] < img.getDimension(1);
	}
	
	/**
	 * Given an array of a pixels position, the width of the image, and the number of pixels in a plane,
	 * returns the index of the position in a linear array as calculated by x + width * y + numPixInPlane * z.
	 *
	 * @param pos
	 * @param width
	 * @return
	 */
	public int getIndexOfPosition2D(int pos[], int width) {
		return pos[0] + width * pos[1];
	}
	
	/**
	 * 
	 * @param searched
	 * @return
	 */
	public int[] findAveragePosition3D(LinkedList < int[] > searched) {
		int count = 0;
		int avgX = 0, avgY = 0, avgZ = 0;
		while(!searched.isEmpty()) {
			int curr[] = searched.poll();
			avgX += curr[0];
			avgY += curr[1];
			avgZ += curr[2];
			count++;
		}
		return new int[] {avgX/count, avgY/count, avgZ/count};
	}

	/**
	 * Given a position array, returns whether or not the position is within the bounds of the image, or out of bounds.
	 * 
	 * @param pos
	 * @return
	 */
	public boolean isWithinImageBounds3D(int pos[]) {
		return pos[0] > -1 && pos[0] < img.getDimension(0) && pos[1] > -1 && pos[1] < img.getDimension(1) && pos[2] > -1 && pos[2] < img.getDimension(2);
	}
	
	/**
	 * Given an array of a pixels position, the width of the image, and the number of pixels in a plane,
	 * returns the index of the position in a linear array as calculated by x + width * y + numPixInPlane * z.
	 *
	 * @param pos
	 * @param width
	 * @param numPixelsInXYPlane
	 * @return
	 */
	public int getIndexOfPosition3D(int pos[], int width, int numPixelsInXYPlane) {
		return pos[0] + width * pos[1] + numPixelsInXYPlane * pos[2];
	}
}
