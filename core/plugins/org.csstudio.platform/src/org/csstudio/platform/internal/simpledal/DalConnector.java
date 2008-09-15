/* 
 * Copyright (c) 2008 Stiftung Deutsches Elektronen-Synchrotron, 
 * Member of the Helmholtz Association, (DESY), HAMBURG, GERMANY.
 *
 * THIS SOFTWARE IS PROVIDED UNDER THIS LICENSE ON AN "../AS IS" BASIS. 
 * WITHOUT WARRANTY OF ANY KIND, EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED 
 * TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR PARTICULAR PURPOSE AND 
 * NON-INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE 
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, 
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR 
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE. SHOULD THE SOFTWARE PROVE DEFECTIVE 
 * IN ANY RESPECT, THE USER ASSUMES THE COST OF ANY NECESSARY SERVICING, REPAIR OR 
 * CORRECTION. THIS DISCLAIMER OF WARRANTY CONSTITUTES AN ESSENTIAL PART OF THIS LICENSE. 
 * NO USE OF ANY SOFTWARE IS AUTHORIZED HEREUNDER EXCEPT UNDER THIS DISCLAIMER.
 * DESY HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, 
 * OR MODIFICATIONS.
 * THE FULL LICENSE SPECIFYING FOR THE SOFTWARE THE REDISTRIBUTION, MODIFICATION, 
 * USAGE AND OTHER RIGHTS AND OBLIGATIONS IS INCLUDED WITH THE DISTRIBUTION OF THIS 
 * PROJECT IN THE FILE LICENSE.HTML. IF THE LICENSE IS NOT INCLUDED YOU MAY FIND A COPY 
 * AT HTTP://WWW.DESY.DE/LEGAL/LICENSE.HTM
 */
 package org.csstudio.platform.internal.simpledal;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.csstudio.platform.internal.simpledal.converters.ConverterUtil;
import org.csstudio.platform.logging.CentralLogger;
import org.csstudio.platform.model.pvs.IProcessVariableAddress;
import org.csstudio.platform.simpledal.ConnectionState;
import org.csstudio.platform.simpledal.IProcessVariableValueListener;
import org.csstudio.platform.simpledal.ValueType;
import org.epics.css.dal.CharacteristicInfo;
import org.epics.css.dal.DataExchangeException;
import org.epics.css.dal.DynamicValueCondition;
import org.epics.css.dal.DynamicValueEvent;
import org.epics.css.dal.DynamicValueListener;
import org.epics.css.dal.DynamicValueProperty;
import org.epics.css.dal.DynamicValueState;
import org.epics.css.dal.ResponseEvent;
import org.epics.css.dal.ResponseListener;
import org.epics.css.dal.SimpleProperty;
import org.epics.css.dal.Timestamp;
import org.epics.css.dal.context.ConnectionEvent;
import org.epics.css.dal.context.LinkListener;

/**
 * DAL Connectors are connected to the control system via the DAL API.
 * 
 * All events received from DAL are forwarded to
 * {@link IProcessVariableValueListener}�s which abstract from DAL.
 * 
 * For convinience the {@link IProcessVariableValueListener}�s are only weakly
 * referenced. The connector tracks for {@link IProcessVariableValueListener}�s
 * that have been garbage collected and removes those references from its
 * internal list. This way {@link IProcessVariableValueListener}�s must not be
 * disposed explicitly.
 * 
 * @author Sven Wende
 * 
 */
@SuppressWarnings("unchecked")
class DalConnector extends AbstractConnector implements DynamicValueListener,
		LinkListener, ResponseListener, PropertyChangeListener {
	
	public static final CharacteristicInfo C_TIMESTAMP_INFO= new CharacteristicInfo("timestamp",Timestamp.class,new Class[]{DynamicValueProperty.class},"Meta timestamp characteristic.",null,true);
	public static final CharacteristicInfo C_SEVERITY_INFO= new CharacteristicInfo("severity",String.class,new Class[]{DynamicValueProperty.class},"Meta timestamp characteristic.",null,true);
	public static final CharacteristicInfo C_STATUS_INFO= new CharacteristicInfo("status",String.class,new Class[]{DynamicValueProperty.class},"Meta timestamp characteristic.",null,true);
	
	{
		CharacteristicInfo.registerCharacteristicInfo(C_SEVERITY_INFO);
		CharacteristicInfo.registerCharacteristicInfo(C_TIMESTAMP_INFO);
		CharacteristicInfo.registerCharacteristicInfo(C_STATUS_INFO);
	}
	
	public static final String toEPICSFlavorSeverity(DynamicValueCondition condition) {
		if (condition.isNormal()) {
			return DynamicValueState.NORMAL.toString();
		}
		if (condition.isWarning()) {
			return DynamicValueState.WARNING.toString();
		}
		if (condition.isAlarm()) {
			return DynamicValueState.ALARM.toString();
		}
		if (condition.isError()) {
			return DynamicValueState.ERROR.toString();
		}
		return "UNKNOWN";
	}

	/**
	 * The DAL property, this connector is connected to.
	 */
	private DynamicValueProperty _dalProperty;

	/**
	 * Constructor.
	 */
	public DalConnector(IProcessVariableAddress pvAddress, ValueType valueType) {
		super(pvAddress, valueType);
	}

	/**
	 * Sets the DAL property, this connector is connected to.
	 * 
	 * @param dalProperty
	 *            the DAL property
	 */
	public void setDalProperty(DynamicValueProperty dalProperty) {
		_dalProperty = dalProperty;
		
		_dalProperty.addDynamicValueListener(this);
		
		_dalProperty.addPropertyChangeListener(this);

		// we add a LinkListener to get informed of connection state changes
		_dalProperty.addLinkListener(this);

		// send initial connection state
		forwardConnectionState(ConnectionState
				.translate(_dalProperty.getConnectionState()));

	}

	/**
	 * Returns the DAL property, this connector is connected to.
	 * 
	 * @return
	 */
	public DynamicValueProperty getDalProperty() {
		return _dalProperty;
	}
	
	public void propertyChange(PropertyChangeEvent evt) {
		doForwardValue(evt.getNewValue(), new Timestamp(), evt.getPropertyName());
	}

	/**
	 * {@inheritDoc}
	 */
	public void conditionChange(DynamicValueEvent event) {
		
		DynamicValueCondition condition= event.getCondition();
		
		doForwardValue(condition.getTimestamp(), condition.getTimestamp(), C_TIMESTAMP_INFO.getName());
		doForwardValue(condition.getDescription(), condition.getTimestamp(), C_STATUS_INFO.getName());
		doForwardValue(toEPICSFlavorSeverity(condition), condition.getTimestamp(), C_SEVERITY_INFO.getName());
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void errorResponse(DynamicValueEvent event) {
		//FIXME: forward condition changes
	}

	/**
	 * {@inheritDoc}
	 */
	public void timelagStarts(DynamicValueEvent event) {
		//FIXME: forward condition changes
	}

	/**
	 * {@inheritDoc}
	 */
	public void timelagStops(DynamicValueEvent event) {
		//FIXME: forward condition changes
	}

	/**
	 * {@inheritDoc}
	 */
	public void timeoutStarts(DynamicValueEvent event) {
		//FIXME: forward condition changes
	}

	/**
	 * {@inheritDoc}
	 */
	public void timeoutStops(DynamicValueEvent event) {
		//FIXME: forward condition changes
	}

	/**
	 * {@inheritDoc}
	 */
	public void valueChanged(final DynamicValueEvent event) {
		doForwardValue(event.getValue(), event.getTimestamp());
	}

	/**
	 * {@inheritDoc}
	 */
	public void valueUpdated(final DynamicValueEvent event) {
		doForwardValue(event.getValue(), event.getTimestamp());
	}

	/**
	 * {@inheritDoc}
	 */
	public void connected(final ConnectionEvent e) {
		if (getLatestValue()==null) {
			try {
				_dalProperty.getAsynchronous(this);
			} catch (DataExchangeException ex) {
				forwardError(ex.getMessage());
				CentralLogger.getInstance().warn(null, ex);
			}
		}
		doForwardConnectionStateChange(ConnectionState.translate(e.getState()));
	}

	/**
	 * {@inheritDoc}
	 */
	public void connectionFailed(ConnectionEvent e) {
		doForwardConnectionStateChange(ConnectionState.translate(e.getState()));
	}

	/**
	 * {@inheritDoc}
	 */
	public void connectionLost(ConnectionEvent e) {
		forwardConnectionEvent(e);
	}

	/**
	 * {@inheritDoc}
	 */
	public void destroyed(ConnectionEvent e) {
		forwardConnectionEvent(e);
	}

	/**
	 * {@inheritDoc}
	 */
	public void disconnected(ConnectionEvent e) {
		forwardConnectionEvent(e);
	}

	/**
	 * {@inheritDoc}
	 */
	public void resumed(ConnectionEvent e) {
		forwardConnectionEvent(e);
	}

	/**
	 * {@inheritDoc}
	 */
	public void suspended(ConnectionEvent e) {
		forwardConnectionEvent(e);
	}

	/**
	 * {@inheritDoc}
	 */
	public void responseError(ResponseEvent event) {
		Exception e = event.getResponse().getError();
		doForwardError(e != null ? e.getMessage() : "Unknown error!");
	}

	/**
	 * {@inheritDoc}
	 */
	public void responseReceived(ResponseEvent event) {
		// Igor: if necessary update last value. We expect one event only originating 
		//       from  initial asynchronous get

		doForwardValue(event.getResponse().getValue(), event.getResponse().getTimestamp());
		
		// Igor: this below not necessary any more
		//             
		/*IProcessVariableAddress pv = getProcessVariableAddress();
		String idTag = event.getResponse().getIdTag().toString();
		
		// Important: We need to check, that we forward only the right events because all Characteristics  are queried using the same DAL Property instance
		boolean forward = false;

		if (pv.isCharacteristic()) {
			forward = pv.getCharacteristic().equals(idTag);
		} else {
			forward = "value".equals(idTag);
		}

		if (forward) {
			//jhatje 18.0.7.2008, add timestamp of the event
			doForwardValue(event.getResponse().getValue(), event.getResponse().getTimestamp());
		}*/
	}

	private void forwardConnectionEvent(ConnectionEvent e) {
		doForwardConnectionStateChange(ConnectionState.translate(e.getState()));
	}
	
}
