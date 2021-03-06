package fiji.plugin.trackmate.tracking.kalman;

import java.util.Random;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.features.track.TrackIndexAnalyzer;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings.TrackMateObject;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import fiji.plugin.trackmate.visualization.trackscheme.TrackScheme;
import ij.ImageJ;

public class KalmanTrackerInteractiveTest3
{
	private static final int NFRAMES = 41;

	private static final double WIDTH = 400;

	public static void main( final String[] args )
	{
		ImageJ.main( args );
		final KalmanTrackerInteractiveTest3 tester = new KalmanTrackerInteractiveTest3();

		System.out.println( "Straight line with Kalman tracker:" );
		tester.test( tester.createSingleLine() );
	}


	private Model test( final SpotCollection spots )
	{
		final double maxSearchRadius = 2 * WIDTH / NFRAMES; // small
		final int maxFrameGap = 2;
		final double initialSearchRadius = 2 * WIDTH / ( NFRAMES );
		final KalmanTracker tracker = new KalmanTracker( spots, maxSearchRadius, maxFrameGap, initialSearchRadius );
		tracker.setLogger( Logger.DEFAULT_LOGGER );
		if ( !tracker.checkInput() || !tracker.process() )
			System.err.println( tracker.getErrorMessage() );

		final Model model = new Model();
		model.setSpots( spots, false );
		model.setTracks( tracker.getResult(), false );

		final SpotCollection predictions = tracker.getPredictions();
		model.beginUpdate();

		try
		{
			for ( final Integer f : predictions.keySet() )
				for ( final Spot s : predictions.iterable( f, true ) )
					model.addSpotTo( s, f );
		}
		finally
		{
			model.endUpdate();
		}

		final DisplaySettings ds = DisplaySettings.defaultStyle().copy();
		ds.setSpotColorBy( TrackMateObject.SPOTS, Spot.QUALITY );
		ds.setTrackColorBy( TrackMateObject.TRACKS, TrackIndexAnalyzer.TRACK_INDEX );
		ds.setSpotShowName( true );

		final TrackIndexAnalyzer ta = new TrackIndexAnalyzer();
		ta.process( model.getTrackModel().trackIDs( true ), model );

		final SelectionModel selectionModel = new SelectionModel( model );
		final HyperStackDisplayer view = new HyperStackDisplayer( model, selectionModel, ds );
		view.render();

		final TrackScheme trackscheme = new TrackScheme( model, selectionModel, ds );
		trackscheme.render();

		return model;
	}

	private SpotCollection createSingleLine()
	{
		final SpotCollection spots = new SpotCollection();
		final Random ran = new Random( 1l );

		/*
		 * The 4th lonely riders.
		 */
		final double x0 = 0;
		final double y0 = WIDTH / 2;
		final double vx0 = WIDTH / ( NFRAMES - 1 );
		final double vy0 = 0;

		final double sigma = 4d;

		double x = x0;
		double y = y0;
		for ( int t = 0; t < NFRAMES; t++ )
		{
			final Spot spot = new Spot( x + ran.nextGaussian() * sigma, y + ran.nextGaussian() * sigma, 0, 2, 1, "S_" + t );
			spots.add( spot, t );

			x += vx0;
			y += vy0;
		}
		return spots;
	}
}
