package eu.h2020.symbiote.subman.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import eu.h2020.symbiote.subman.messaging.RabbitManager;

/**
 * SubscriptionManager REST interface. Created by Petar Krivic on
 * 30/01/2018.
 */
@RestController
public class RestInterface {

	@Autowired
	RabbitManager rabbitManager;
	
	@RequestMapping(value = "/sm", method = RequestMethod.POST)
	public String interworkingInterfaceRequest(){
		return new String("inprogress");
	}
}
