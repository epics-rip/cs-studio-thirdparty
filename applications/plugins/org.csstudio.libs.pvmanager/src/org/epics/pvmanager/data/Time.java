/*
 * Copyright 2010 Brookhaven National Laboratory
 * All rights reserved. Use is subject to license terms.
 */

package org.epics.pvmanager.data;

import org.epics.pvmanager.TimeStamp;

/**
 * Time information.
 *
 * @author carcassi
 */
public interface Time {

    /**
     * The timestamp of the value, typically indicating when it was
     * generated. Never null. If never connected, it returns the
     * time when it was last determined that no connection was made.
     * 
     * @return the timestamp
     */
    TimeStamp getTimeStamp();

    /**
     * Returns a user defined tag, that can be used to store extra
     * time information, such as beam shot.
     *
     * @return the user tag
     */
    Integer getTimeUserTag();
}
