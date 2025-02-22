/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.contrib.signals.data.ambertimes.v10;

import javax.xml.XMLConstants;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.validation.SchemaFactory;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.signals.data.AbstractSignalsReader;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.jaxb.amberTimes10.XMLAmberTimes;
import org.matsim.jaxb.amberTimes10.XMLAmberTimes.XMLSignalSystem;
import org.matsim.jaxb.amberTimes10.XMLAmberTimes.XMLSignalSystem.XMLSignal;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author jbischoff
 * @author dgrether
 */
public final class AmberTimesReader10 extends AbstractSignalsReader {

	private AmberTimesData amberTimesData;

	public AmberTimesReader10(AmberTimesData amberTimesData) {
		this.amberTimesData = amberTimesData;
	}


	public void read( InputSource stream ) {
		// create jaxb infrastructure
		JAXBContext jc;
		XMLAmberTimes xmlatdefs;
		try {
			jc = JAXBContext.newInstance(org.matsim.jaxb.amberTimes10.ObjectFactory.class);
			Unmarshaller u = jc.createUnmarshaller();
			// validate XML file
			u.setSchema(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(getClass().getResource("/dtd/amberTimes_v1.0.xsd")));
			xmlatdefs = (XMLAmberTimes) u.unmarshal(stream);
		} catch (JAXBException | SAXException e) {
			throw new UncheckedIOException(e);
		}

		// convert from Jaxb types to MATSim-API conform types

		// Global Defaults
		if (xmlatdefs.getGlobalDefaults() != null){
			if (xmlatdefs.getGlobalDefaults().getAmber() != null){
				amberTimesData
				.setDefaultAmber(xmlatdefs.getGlobalDefaults().getAmber().getSeconds().intValue());
			}
			if (xmlatdefs.getGlobalDefaults().getAmberTimeGreen() != null){
				amberTimesData.setDefaultAmberTimeGreen(xmlatdefs.getGlobalDefaults().getAmberTimeGreen()
						.getProportion().doubleValue());
			}
			if (xmlatdefs.getGlobalDefaults().getRedAmber() != null){
				amberTimesData.setDefaultRedAmber(xmlatdefs.getGlobalDefaults().getRedAmber().getSeconds()
						.intValue());
			}
		}

		for (XMLSignalSystem xmlss : xmlatdefs.getSignalSystem()) {
			Id<SignalSystem> ssid = Id.create(xmlss.getRefId(), SignalSystem.class);
			AmberTimeData atdata = new AmberTimeDataImpl(ssid);

			// Signalsystem Defaults
			if (xmlss.getSystemDefaults() != null){
				if (xmlss.getSystemDefaults().getAmber() != null){
					atdata.setDefaultAmber(xmlss.getSystemDefaults().getAmber().getSeconds().intValue());
				}
				if (xmlss.getSystemDefaults().getRedAmber() != null){
					atdata.setDefaultRedAmber(xmlss.getSystemDefaults().getRedAmber().getSeconds().intValue());
				}
			}

			for (XMLSignal xmls : xmlss.getSignal()) {

				Id<Signal> sid = Id.create(xmls.getRefId(), Signal.class);
				atdata.setAmberTimeOfSignal(sid, xmls.getAmber().getSeconds().intValue());
				atdata.setRedAmberTimeOfSignal(sid, xmls.getRedAmber().getSeconds().intValue());

			}

			amberTimesData.addAmberTimeData(atdata);
		}

	}

}
