package fiji.plugin.trackmate.gui;

import java.awt.event.ActionEvent;
import java.util.Map;

import javax.swing.JPanel;

/**
 * The mother class for all the configuration panels.
 * 
 * @author Jean-Yves Tinevez
 *
 */
public abstract class ConfigurationPanel extends JPanel
{

	private static final long serialVersionUID = 1L;

	/**
	 * This event is fired when a preview button is fired within this
	 * configuration panel. It is the responsibility of concrete implementation
	 * to do whatever they want with it.
	 */
	public final ActionEvent PREVIEW_BUTTON_PUSHED = new ActionEvent( this, 0, "PreviewButtonPushed" );

	/**
	 * Echo the parameters of the given settings on this panel.
	 */
	public abstract void setSettings( final Map< String, Object > settings );

	/**
	 * @return a new settings map object with its values set by this panel.
	 */
	public abstract Map< String, Object > getSettings();

	/**
	 * Executes any task related to cleaning the possible previews generated by
	 * this panel prior to moving to another GUI panel.
	 */
	public abstract void clean();

}
