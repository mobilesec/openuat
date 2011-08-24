/**
 *  Filename: BandyDisplayable.java (in org.openbandy.ui)
 *  This file is part of the OpenBandy project.
 * 
 *  OpenBandy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  OpenBandy is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with OpenBandy. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * (c) Copyright Philipp Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 *  www.openbandy.org
 */

package org.openbandy.ui;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;


/**
 * The BandyDisplayable introduces a new method 'show', which is imho missing in
 * javax.microedition.lcdui.Displayable.
 * 
 * NOTE Any class implementing the BandyDisplayalb interface MUST be of type
 * javax.microedition.lcdui.Displayable!!
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public interface BandyDisplayable {

	/**
	 * Tells the BandyDisplayable to set itself as the current screen.
	 * 
	 * @param display
	 *            The MIDlets display
	 * @param previousDisplayable
	 *            The displayable that is shown before this one
	 */
	public void show(Display display, Displayable previousDisplayable);

}
