/*
 *  King Of Fans Zigbee Fan Controller
 *  
 *  Also contains code from https://github.com/DavinKD/SmartThings/blob/master/devicetypes/davindameron/tasmota-fan.src/tasmota-fan.groovy
 *
 *  To be used with Ceiling Fan Remote Controller Model MR101Z receiver by Chungear Industrial Co. Ltd
 *  at Home Depot Gardinier 52" Ceiling Fan, Universal Ceiling Fan/Light Premier Remote Model #99432
 *
 *  Copyright 2020 Rafael Borja, Ranga Pedamallu, Stephan Hackett, Dale Coffing
 *
 *  Contributing Authors (based on https://github.com/dcoffing/KOF-CeilingFan):
 *      Ranga Pedamallu; initial release and zigbee parsing mastermind!
 *      Stephan Hackett; new composite (child) device type genius! 
 *      Dale Coffing; icons, multiAttribute fan, code maintenance flunky
 *      Rafael Borja; New version for new Smartthings app (2019)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

metadata {
	definition(name: "King of Fans Zigbee Fan Controller", namespace: "rafaelborja", author: "Rafael Borja", ocfDeviceType: "oic.d.fan", genericHandler: "Zigbee") {    
    	capability "Switch Level"
		capability "Switch"
		capability "Fan Speed"
		capability "Health Check"
		capability "Actuator"
		capability "Refresh"
		capability "Sensor"
        capability "Configuration"

		command "low"
		command "medium"
		command "high"
		command "raiseFanSpeed"
		command "lowerFanSpeed"
        
        attribute "lastFanMode", "string" // Last fan speed value

		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0008,0003,0019,0202", outClusters: "0003,0019" , model: "HDC52EastwindFan"
     }
     
       tiles(scale: 2) {
		multiAttributeTile(name: "fanSpeed", type: "generic", width: 6, height: 4, canChangeIcon: true) {
			tileAttribute("device.fanSpeed", key: "PRIMARY_CONTROL") {
				attributeState "0", label: "Off", action: "switch.on", icon: "st.thermostat.fan-off", backgroundColor: "#ffffff"
				attributeState "1", label: "Low", action: "switch.off", icon: "st.thermostat.fan-on", backgroundColor: "#00a0dc"
				attributeState "2", label: "Medium", action: "switch.off", icon: "st.thermostat.fan-on", backgroundColor: "#00a0dc"
				attributeState "3", label: "High", action: "switch.off", icon: "st.thermostat.fan-on", backgroundColor: "#00a0dc"
			}
			tileAttribute("device.fanSpeed", key: "VALUE_CONTROL") {
				attributeState "VALUE_UP", action: "raiseFanSpeed"
				attributeState "VALUE_DOWN", action: "lowerFanSpeed"
			}
		}

		standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label: '', action: "refresh.refresh", icon: "st.secondary.refresh"
		}
		main "fanSpeed"
		details(["fanSpeed", "refresh"])
	}
    
    preferences {
        // section("Google assistant fan control using dimmer") {
        input ( type: "paragraph", element: "paragraph", title: "Google assistant fan control using dimmer", description: "\
        		If you are using Google assistant you can set this option to true to control fan speed using light dimmer.\
                Google asistant does not properly support fan speed dial, showing it as a light dimmer instead.\
                When this option is activiated you can set fan speed with the command \"Set <FAN NAME> speed to <0 to 100>\", where:\n\
                 - 0 to 24 is speed 25 (turn off),\n\
                 - 24 to 49 is speed 1 (low),\n\
                 - 50 to 74 is speed 2 (medium),\n\
                 - 75 to 100 is speed 3 (high),\n\
                 \n\
                 \n\
                 You can still use the on/off button as usual (fan on/off). To control light level you must use child light device (shown as a regular light)")
                 
        	input "dimmerAsFanControl", "enum", title: "Use dimmer to control fan?", options: ["1":"Yes", "0":"No"], displayDuringSetup: true, default: "0"
       // }
       
    }
}

def parse(String description) {
	log.info "parse($description)"
    def event = zigbee.getEvent(description)
    if (event) {
    	// "Sample 0104 0006 01 01 0000 00 D42D 00 00 0000 01 01 010086"
        
        log.info "Parse description ${description}"
    	log.info "Light event detected on controller (event): ${event}"
        
    	def childDevice = getChildDevices()?.find {		//find light child device
        	log.debug "parse() child device found"
            it.device.deviceNetworkId == "${device.deviceNetworkId}-Light" 
        }          
        event.displayed = true
        event.isStateChange = true
        
        // TODO find right event
        childDevice.createAndSendEvent(event)
        // childDevice.createAndSendEvent(description)
        childDevice.sendEvent(event)
        // TODO not needed? childDevice.parse(description)
        
        //send light events to light child device and update lightBrightness attribute
        if(event.value != "on" && event.value != "off" && !useDimmerAsFanControl()) {
        	log.debug "sendEvent lightBrightness"
        	
            //  TODO remove unused events
        	// TODO refactor to a single method
            sendEvent(name: "lightBrightness", value: event.value, displayed: true, isStateChange: true) 
            sendEvent(name: "levelSliderControl", value: event.value, displayed: true, isStateChange: true) 
            sendEvent(name: "level", value: event.value, displayed: true, isStateChange: true) 
            sendEvent(name: "switch level", value: event.value, displayed: true, isStateChange: true) 
            
        } else {
        	log.debug "not sending lightBrightness"
        }
    }
	else {
     	// "Sample: 0104 0006 01 01 0000 00 D42D 00 00 0000 07 01 86000100"
        // "Sample: 0104 0006 01 01 0000 00 D42D 00 00 0000 07 01 00"
        // "Sample: D42D0102020800003000, dni: D42D, endpoint: 01, cluster: 0202, size: 8, attrId: 0000, result: success, encoding: 30, value: 00"
       	log.info "Fan event detected on controller"
		def map = [:]
		if (description?.startsWith("read attr -")) {
			def descMap = zigbee.parseDescriptionAsMap(description)
            log.debug "descMap in parse $descMap"
			if (descMap.cluster == "0202" && descMap.attrId == "0000") {     // Fan Control Cluster Attribute Read Response            	                  
				map.name = "fanMode"
				map.value = descMap.value
                return fanEvents(descMap.value.toInteger())
			} 
		}	// End of Read Attribute Response
		def result = null            
        if (map) {            
			result = createEvent(map)                
		} else {
        	log.debug("parse: event map is null")
        }
		log.debug "Parse returned $map"            
		
        return result 
   	}                
}

/*
 * Returns the string representing speed value:
 */
def speedToLabel(speed) {
	def labelMap = [
        "0":"Off",
        "1":"Low",
        "2":"Medium",
        "3":"Medium-Hi",
        "4":"High",
        "5":"Off",
        "6":"Comfort Breeze™",
        "7":"Light"
     ]
     
     return labelMap["${speed}"]
}

/**
 * Creates events for Fan based on speed value (switch, level and fan level)
 */
def fanEvents(speed) {
	log.trace "fanEvents(${speed})"
    
	def value = (speed ? "on" : "off") // TODO review this portion as it is sensing light off to child device when changing dimmer value
	def result = [createEvent(name: "switch", value: value)]
	// result << createEvent(name: "level", value: speed == 99 ? 100 : speed)
	result << createEvent(name: "fanSpeed", value: speed)
    
    // In case dimmer is being used to control fan (Google assitant compatibility)
    if (useDimmerAsFanControl()) {
    	log.trace "Sending dimmer events for fan event"
        //  TODO remove unused events
        // TODO refactor to a single method
        result << sendEvent(name: "lightBrightness", value: speedToDimmerLevel(speed), displayed: true, isStateChange: true)  
        result << sendEvent(name: "levelSliderControl", value:  speedToDimmerLevel(speed), displayed: true, isStateChange: true) 
        result << sendEvent(name: "level", value:  speedToDimmerLevel(speed), displayed: true, isStateChange: true) 
        result << sendEvent(name: "switch level", value:  speedToDimmerLevel(speed), displayed: true, isStateChange: true) 
    }
    
    
    log.trace "fanEvents(${speed}) returning ${result}"
    
	return result
}


def installed() {
	log.debug "installed()"
	initialize()
}


def updated() {
	log.debug "updated()"
	/ * if(state.oldLabel != device.label) {updateChildLabel()} */ // TODO DEV ONLY
		initialize()    
}

def initialize() {	
	log.info "initialize()"     
    
    
    
    if(refreshChildren) { 
        deleteChildren()            
        device.updateSetting("refreshChildren", false) 
        refresh()
    }
    else {
        // createFanChild()
        createLightChild()
        response(refresh() + configure())
    }
}

def updateChildLabel() {
	log.info "updateChildLabel()"
    
    def childDeviceL = getChildDevices()?.find {
        	it.device.deviceNetworkId == "${device.deviceNetworkId}-Light"
    }
    if (childDeviceL) {childDeviceL.label = "${device.displayName}-Light"}    // rename with new label
} 


def createLightChild() {
	log.debug "createLightChild()"
	def childDevice = getChildDevices()?.find {
        	it.device.deviceNetworkId == "${device.deviceNetworkId}-Light"
    }
    if (!childDevice) {  
    
		childDevice = addChildDevice("King of Fans Zigbee Fan - Light Child Device", "${device.deviceNetworkId}-Light", null,[completedSetup: true,
        label: "${device.displayName} Light" ]) /*, isComponent: false, componentName: "fanLight" */
        log.info "Creating child light ${childDevice}" 
    }
	else {
        log.info "Child already exists"          
	}	
}

def deleteChildren() {	
	def children = getChildDevices()        	
    children.each {child->
  		deleteChildDevice(child.deviceNetworkId)
    }	
    log.info "Deleting children"                  
}

def configure() {
	log.info "configure() Configuring Reporting and Bindings."
    
    // Sample "[zdo bind 0xD42D 0x01 0x01 0x0006 {0022A3000016B5F4} {}, delay 2000, st cr 0xD42D 0x01 0x0006 0x0000 0x10 0x0000 0x0258 {}, delay 2000]"

	def cmd = 
    [
	  //Set long poll interval
	  "raw 0x0020 {11 00 02 02 00 00 00}", "delay 100",
	  "send 0x${device.deviceNetworkId} 1 1", "delay 100",
	  //Bindings for Fan Control
      // "zdo bind 0x${device.deviceNetworkId} 1 0 0x006 {${device.zigbeeId}} {}", "delay 100",
      
      "zdo bind 0x${device.deviceNetworkId} 1 1 0x006 {${device.zigbeeId}} {}", "delay 100",
      "zdo bind 0x${device.deviceNetworkId} 1 1 0x008 {${device.zigbeeId}} {}", "delay 100",
	  "zdo bind 0x${device.deviceNetworkId} 1 1 0x202 {${device.zigbeeId}} {}", "delay 100",
	  //Fan Control - Configure Report
      "zcl global send-me-a-report 0x006 0 0x10 1 300 {}", "delay 100",
       "send 0x${device.deviceNetworkId} 0 1", "delay 100",
       // Light?
      "zcl global send-me-a-report 0x006 1 0x10 1 300 {}", "delay 100",
       "send 0x${device.deviceNetworkId} 1 1", "delay 100",
       
      "zcl global send-me-a-report 0x008 0 0x20 1 300 {}", "delay 100",
       "send 0x${device.deviceNetworkId} 1 1", "delay 100",
	  "zcl global send-me-a-report 0x202 0 0x30 1 300 {}", "delay 100",
	  "send 0x${device.deviceNetworkId} 1 1", "delay 100",
      //Light Control - Configure Report
	  //Update values
      "st rattr 0x${device.deviceNetworkId} 1 0x006 0", "delay 100",
      "st rattr 0x${device.deviceNetworkId} 1 0x006 1", "delay 100", // Light?
      "st rattr 0x${device.deviceNetworkId} 1 0x008 0", "delay 100",
	  "st rattr 0x${device.deviceNetworkId} 1 0x202 0", "delay 100",
      
	 //Set long poll interval
	  "raw 0x0020 {11 00 02 1C 00 00 00}", "delay 100",
	  "send 0x${device.deviceNetworkId} 1 1", "delay 100",
      zigbee.configureReporting(0x0006, 0x00011, 0x10, 0, 600, null),
	]
    return cmd + refresh()
}


def getEndpoint (child) {
	log.debug "getEndpoint (${child})"
	
    def endpoint = child.deviceNetworkId == device.deviceNetworkId?getInitialEndpoint():child.deviceNetworkId.minus("-Light")
    
    log.debug "getEndpoint (${child}): {endpoint}"
    
    return endpoint
}

def off (physicalgraph.device.cache.DeviceDTO child) {
	log.debug "off (physicalgraph.device.cache.DeviceDTO child ${child})"
    
    
    
    def childDevice = getChildDevices()?.find {
        	it.device.deviceNetworkId == "${device.deviceNetworkId}-Light"
    }
    if (childDevice) {
    	log.debug "Sending event to child $childDevice"
        log.debug childDevice
    	childDevice.createAndSendEvent(name: "switch", value: "off", displayed: true, isStateChange: true) // childDevice.sendEvent(name: "device.switch", value: "off", displayed: true, isStateChange: true) + 
        	// childDevice.sendEvent(name: "switch", value: "off", displayed: true, isStateChange: true) +
        	// childDevice.createAndSendEvent(name: "switch", value: "off", displayed: true, isStateChange: true)
    }
    
    lightOff(getEndpoint(child))
}

def on (physicalgraph.device.cache.DeviceDTO child) {
	log.debug "on (physicalgraph.device.cache.DeviceDTO child ${child})"
    
    def childDevice = getChildDevices()?.find {
        	it.device.deviceNetworkId == "${device.deviceNetworkId}-Light"
    }
    if (childDevice) {
    	log.debug "Sending event to child $childDevice"
        log.debug childDevice
        
    	childDevice.createAndSendEvent(name: "switch", value: "on", displayed: true, isStateChange: true) // childDevice.sendEvent(name: "device.switch", value: "on", displayed: true, isStateChange: true) +
        	//childDevice.sendEvent(name: "switch", value: "on", displayed: true, isStateChange: true) +
        	
    }
    
	lightOn(getEndpoint(child))
}

/*
 * Called from child device
 */
def ping(physicalgraph.device.cache.DeviceDTO child) {
	log.debug "ping(${child})"   
    
    return ping()
}

def on() {
	log.debug "on()"
	log.info "Resuming Previous Fan Speed"   
	def lastFan =  device.currentValue("lastFanMode")	 //resumes previous fanspeed
	return setFanSpeed("$lastFan")
}

def off() {	
    log.debug "off()"
    def fanNow = device.currentValue("fanSpeed")    //save fanspeed before turning off so it can be resumed when turned back on
    log.debug "off(): Current fan speed: $fanNow"
    if (fanNow != "00") {
    	  //do not save lastfanmode if fan is already off
    	sendEvent("name":"lastFanMode", "value":fanNow)
    }
	
    def cmds=[
	"st wattr 0x${device.deviceNetworkId} 1 0x202 0 0x30 {00}"
    ]
    log.info "off(): Turning fan Off"    
    return cmds
}

def lightOn(String dni)  {
	log.info "lightOn(${dni})"
    
    log.debug "Loading childlights"
    def childDevice = getChildDevices()?.find {
        	it.device.deviceNetworkId == "${device.deviceNetworkId}-Light"
    }
    if (childDevice) {
    	log.debug "Sending event to child"
        log.debug childDevice
    	childDevice.sendEvent(name: "device.switch", value: "on", displayed: true, isStateChange: true)
        childDevice.sendEvent(name: "switch", value: "on", displayed: true, isStateChange: true)
        childDevice.createEvent(childDevice.createAndSendEvent(name: "switch", value: "on", displayed: true, isStateChange: true ))
    }
    
	return zigbee.on()
    
   
}

def lightOff(String id) {
	log.info "lightOff(${id})"
    
    log.debug "Loading childlights"
    def childDevice = getChildDevices()?.find {
        	it.device.deviceNetworkId == "${device.deviceNetworkId}-Light"
    }
    if (childDevice) {
    	log.debug "Sending event to child"
        log.debug childDevice
    	childDevice.sendEvent(name: "device.switch", value: "off", displayed: true, isStateChange: true)
        childDevice.sendEvent(name: "switch", value: "off", displayed: true, isStateChange: true)
        childDevice.createEvent(childDevice.createAndSendEvent(name: "switch", value: "off", displayed: true, isStateChange: true ))
    }
    
	return zigbee.off()
    
    
}


void childOn(String dni) {
	log.debug "childOn(String ${dni})"
    lightOn(null)
    // onOffCmd(0xFF, channelNumber(dni))
}
void childOff(String dni) {
	log.debug "childOff(String ${dni})"
    lightOff(null)
    // onOffCmd(0, channelNumber(dni))
}

def lightLevel(val) {
	log.debug "lightLevel(${val})"
    
    zigbee.setLevel(val) + (val?.toInteger() >= 1 ? zigbee.on() : []) 
    sendEvent(name:"level",value: val)
    
    log.debug "Loading childlights"
    def childDevice = getChildDevices()?.find {
        	it.device.deviceNetworkId == "${device.deviceNetworkId}-Light"
    }
    if (childDevice) {
    	log.debug "Sending event to child"
        log.debug childDevice
    	//childDevice.sendEvent(name: "device.value", value: val)
        // childDevice.sendEvent(name: "device.switch", value: "on", isStatusChange: true)
        childDevice.sendEvent(name: "switch", value: isDeviceOn? "on": "off", isStatusChange: true, display: true)
        childDevice.sendEvent(name: "value", value: val, isStatusChange: true, display: true)
        childDevice.createEvent(childDevice.createAndSendEvent(name: "level", value: value,  isStatusChange: true, display: true))
    }
}

/**
 * Called from APP when sliding light dimmer
 */
def setLevel(val, rate = null, device=null) {
	log.debug "setLevel(val=${val}, rate=${rate},device=${device})"
    
    def cmds
    if (device != null || !useDimmerAsFanControl() ) {
        // Dimmer acts on lights as usual
    	log.info "Adjusting Light Brightness via setlevel on parent: {$val}" 
    	def isDeviceOn = val?.toInteger() >= 1
    	cmds = zigbee.setLevel(val.toInteger(), 1) + refresh()
    }
    else {
    	log.info "Slider acting for fan control on setLevel" 
    	// Dimmer acts on fan control
		cmds = setFanSpeed(dimmerLevelToSpeed(val?.toInteger()))
        
    }

    log.debug "setLevel(val=${val}, rate=${rate},device=${device}) return ${cmds}"
    
    return cmds
}

def poll() {
	log.debug("poll()")
}

// Called from main device from app to set fan speed
def setFanSpeed(speed) {
	log.debug "setFanSpeed(${speed})"
    
    def cmds=[
	"st wattr 0x${device.deviceNetworkId} 1 0x202 0 0x30 {${speed}}"
    ]
    log.info "Adjusting Fan Speed to "+ speedToLabel(speed)    
    return cmds
}

def ping() {	
	log.debug("ping()")
    return zigbee.onOffRefresh()
}


def refresh(physicalgraph.device.cache.DeviceDTO child=null) {	
	log.info "refresh($child) called " 
    
	return zigbee.onOffRefresh() + zigbee.levelRefresh() + zigbee.readAttribute(0x0202, 0x0000) + zigbee.readAttribute(0x0006, 0x0000) +
    zigbee.readAttribute(0x0202, 0x0000) + zigbee.readAttribute(0x0202, 0x0001) + zigbee.readAttribute(0x0006, 0x0001) + zigbee.readAttribute(0x0006, 0x0000) +
    zigbee.readAttribute(0x0008, 0x0004) + zigbee.readAttribute(0x0008, 0x0004)
}

/**
 * Returns true if  1, dimmer will control fan speed (for Google assistant compatibility) 
 */
def useDimmerAsFanControl() {
	log.trace("useDimmerAsFanControl(): ${dimmerAsFanControl == 1}")
    
    // TODO using number since bool prefs are not saving
	return dimmerAsFanControl == 1
}

/**
 * Returns a dimmer value for a given speed
 *  - 0 to 24 is speed 25 (turn off)
 *  - 24 to 49 is speed 1 (low)
 *  - 50 to 74 is speed 2 (medium)
 *  - 75 to 100 is speed 3 (high)
 */
def speedToDimmerLevel(speed) {
	return speed*25
}

/**
 * Returns a fan value for a given dimmer value
 *  - 0 to 24 is speed 25 (turn off)
 *  - 24 to 49 is speed 1 (low)
 *  - 50 to 74 is speed 2 (medium)
 *  - 75 to 100 is speed 3 (high)
 */
def dimmerLevelToSpeed(dimmerLevel) {
	if (dimmerLevel == null) {
    	dimmerLevel = 0
    }
	return  Math.round(dimmerLevel/25)
}


    

def raiseFanSpeed() {
	setFanSpeed(Math.min((device.currentValue("fanSpeed") as Integer) + 1, 3))
}

def lowerFanSpeed() {
	setFanSpeed(Math.max((device.currentValue("fanSpeed") as Integer) - 1, 0))
}

def low() {
	setLevel(32)
}

def medium() {
	setLevel(66)
}

def high() {
	setLevel(99)
}