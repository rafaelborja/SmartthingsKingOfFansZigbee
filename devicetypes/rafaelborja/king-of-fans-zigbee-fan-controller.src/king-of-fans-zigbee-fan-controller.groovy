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
				attributeState "3", label: "Medium-High", action: "switch.off", icon: "st.thermostat.fan-on", backgroundColor: "#00a0dc"
                attributeState "4", label: "High", action: "switch.off", icon: "st.thermostat.fan-on", backgroundColor: "#00a0dc"
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
        input ( type: "paragraph", element: "paragraph", title: "Fan control using dimmer", description: "\
        		Some devices, such as Google Home or Alexa can't control the fan speed via mode select.\
        		If you are using such a decive, you can use the dimmer to control the fan speed.\
                You can still use the on/off button as usual (fan on/off). To control light level you must use child light device (shown as a regular light)")
                 
        	input "dimmerControl", "enum", title: "What should the dimmer control?", options: ["Light", "Fan"], displayDuringSetup: true, defaultValue: "Light"
       // }
       
    }
}

def parse(String description) {
	log.info "parse($description)"
    // TODO description 'on/off: 1' or 'on/off: 0' can represent both light on/of or power on/off on remote control. After this event fan speed and state must be retrieved and updated
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
        def isStateChange = childDevice.isStateChange(event)
        log.trace "isStateChange: ${isStateChange}"
        event.isStateChange = isStateChange
        
        childDevice.sendEvent(event)
        
        //send light events to light child device and update lightBrightness attribute
        if(event.value != "on" && event.value != "off" && !useDimmerAsFanControl()) {
        	log.debug "sendEvent lightBrightness"
        	
            //  TODO remove unused events
        	// TODO refactor to a single method
            log.trace "lightBrightness: ${device.currentValue('lightBrightness')}"
            log.trace "levelSliderControl: ${device.currentValue('levelSliderControl')}"
            log.trace "level: ${device.currentValue('level')}"
            log.trace "switch level: ${device.currentValue('switch level')}"
            // sendEvent(name: "lightBrightness", value: event.value, displayed: true, isStateChange: isStateChange) 
            // sendEvent(name: "levelSliderControl", value: event.value, displayed: true, isStateChange: isStateChange) 
            sendEvent(name: "level", value: event.value, displayed: true, isStateChange: isStateChange) 
            // sendEvent(name: "switch level", value: event.value, displayed: true, isStateChange: isStateChange) 
            
        } else {
        	log.debug "not sending lightBrightness"
        }
    }
	else {
    	// TODO show fanMode event in history
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
	log.trace "[fanEvents(${speed})]"
    
	def value = (speed ? "on" : "off") // TODO rafaeb review this portion as it is sensing light off to child device when changing dimmer value
	def result = [createEvent(name: "switch", value: value)]
	// result << createEvent(name: "level", value: speed == 99 ? 100 : speed)
	
    def isSpeedChange = isSpeedChange(speed)
    result << createEvent(name: "fanSpeed", value: speed, displayed: true, isStateChange: isSpeedChange)
    
    // In case dimmer is being used to control fan (Google assitant compatibility)
    if (useDimmerAsFanControl()) {
    	log.trace "Sending dimmer events for fan event"
        //  TODO remove unused events
        // TODO refactor to a single method
        log.trace "lightBrightness: ${device.currentValue('lightBrightness')}"
        log.trace "levelSliderControl: ${device.currentValue('levelSliderControl')}"
        log.trace "level: ${device.currentValue('level')}"
        log.trace "switch level: ${device.currentValue('switch level')}"
        // result << sendEvent(name: "lightBrightness", value: speedToDimmerLevel(speed), displayed: true, isStateChange: isSpeedChange)  
        // result << sendEvent(name: "levelSliderControl", value:  speedToDimmerLevel(speed), displayed: true, isStateChange: isSpeedChange) 
        result << sendEvent(name: "level", value:  speedToDimmerLevel(speed), displayed: true, isStateChange: isSpeedChange) 
        // result << sendEvent(name: "switch level", value:  speedToDimmerLevel(speed), displayed: true, isStateChange: isSpeedChange) 
    }
    
    
    log.trace "[fanEvents(${speed})]: ${result}"
    
	return result
}

def isSpeedChange(speed) {
	log.trace "[isSpeedChange(${speed})]"
    
    def speedChange = true
    
    def currentSpeed = device.currentValue("fanSpeed")
    log.trace "speed vs currentSpeed: ${speed} vs ${currentSpeed}"
    speedChange = (speed != currentSpeed)
    log.trace "[isSpeedChange]: ${speedChange}"
    
    return speedChange
    
}


def installed() {	log.debug "[installed()]"

    
	initialize()
}


def updated() {
	log.debug "[updated()]"
    
    initialize()
}

def initialize() {	
	log.info "[initialize()]"     
    // TODO subscribe child to events subscribe(theswitch, "switch.on", incrementCounter)
    
    // Converting and remove legacy (v.01) flag
    if (settings?.dimmerAsFanControl != null) {
    	if (settings.dimmerAsFanControl == 1) {
        	settings.dimmerControl = "Fan"
        } else {
        	settings.dimmerControl = "Light"
        }
        settings?.remove("dimmerAsFanControl")
    }
    
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
    
    lightOff(getEndpoint(child))
}

def on (physicalgraph.device.cache.DeviceDTO child) {
	log.debug "on (physicalgraph.device.cache.DeviceDTO child ${child})"
    
    def childDevice = getChildDevices()?.find {
        	it.device.deviceNetworkId == "${device.deviceNetworkId}-Light"
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
        childDevice.sendEvent(name: "switch", value: isDeviceOn? "on": "off", isStatusChange: true, display: true)
        childDevice.sendEvent(name: "value", value: val, isStatusChange: true, display: true)
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
		cmds = setFanSpeed(dimmerLevelToSpeed(val?.toInteger())) + refresh()
        
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
    
    log.trace "That's tthe refresh: ${zigbee.onOffRefresh() + zigbee.levelRefresh() + zigbee.readAttribute(0x0202, 0x0000) + zigbee.readAttribute(0x0006, 0x0000) + zigbee.readAttribute(0x0202, 0x0001) + zigbee.readAttribute(0x0008, 0x0004)}"
	return zigbee.onOffRefresh() /* same as zigbee.readAttribute(0x0006, 0x0000) + */ + 
    	zigbee.levelRefresh() + 
        zigbee.readAttribute(0x0202, 0x0000) + 
        zigbee.readAttribute(0x0006, 0x0000) + 
        zigbee.readAttribute(0x0202, 0x0001) + 
        zigbee.readAttribute(0x0008, 0x0004)
     	// + zigbee.readAttribute(0x0006, 0x0001)  TODO Confirm it was the cause of blinking light
    
}

/**
 * Returns true if dimmer will control fan speed (for Google Assistant, Alexa compatibility) 
 */
def useDimmerAsFanControl() {
	log.trace("useDimmerAsFanControl(): ${settings.dimmerControl}")
	return settings.dimmerControl == "Fan"
}

/**
 * Returns a dimmer value for a given speed
 * - 0 to 18 is speed 0 (turn off)
 *  - 20 to 39 is speed 1 (low)
 *  - 40 to 59 is speed 2 (medium)
 *  - 60 to 79 is speed 3 (medium-high)
 *  - 80 to 100 is speed 4 (high)
 */
 
def speedToDimmerLevel(speed) {
	if(speed !=6) {
    return speed*25
    }
    else {
    return speed-5
    }
}

/**
 * Returns a fan value for a given dimmer value
 *  - 0 to 20 is speed 0 (turn off)
 *  - 21 to 40 is speed 1 (low)
 *  - 41 to 60 is speed 2 (medium)
 *  - 61 to 80 is speed 3 (medium-high)
 *  - 81 to 100 is speed 4 (high)
 */
 def dimmerLevelToSpeed(dimmerLevel) {
	
    
    if(dimmerLevel == 1) {
    return 6 
    }
    else {
    return (Math.floor(dimmerLevel/20.04)) as Integer
    }
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
