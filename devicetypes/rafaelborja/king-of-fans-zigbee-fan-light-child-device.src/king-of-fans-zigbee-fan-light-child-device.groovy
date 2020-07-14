/**
 *  King Of Fans Zigbee Fan Controller - Light Child Device
 *
 *  Copyright 2020 Rafael Borja
 *  Based on https://github.com/dcoffing/KOF-CeilingFan
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
	definition (name: "King of Fans Zigbee Fan - Light Child Device", namespace: "rafaelborja", ocfDeviceType:"oic.d.light", author: "Rafael Borja", vid: "generic-dimmer") {
		capability "Switch"
		capability "Actuator"
		capability "Sensor"
        capability "Refresh"
        capability "Switch Level"
        
        command "off"
        command "on"
	}

	// TODO Is fingerprint needed for child device?
    //    Zemismart HGZB-42
    fingerprint profileId: "C05E", deviceId: "0000", inClusters: "0000, 0003, 0004, 0005, 0006, 0008", outClusters: "0019", manufacturer: "3A Smart Home DE", model: "LXN-2S27LX1.0", deviceJoinName: "ZigBee Smart Switch"
    fingerprint profileId: "C05E", deviceId: "0000", inClusters: "0000, 0003, 0004, 0005, 0006, 0008", outClusters: "0019", manufacturer: "FeiBit", model: "FNB56-ZSW02LX2.0", deviceJoinName: "ZigBee Smart Switch"

    //    Zemismart HGZB-43
    fingerprint profileId: "C05E", deviceId: "0000", inClusters: "0000, 0003, 0004, 0005, 0006, 0008", outClusters: "0019", manufacturer: "3A Smart Home DE", model: "LXN-3S27LX1.0", deviceJoinName: "ZigBee Smart Switch"
    fingerprint profileId: "C05E", deviceId: "0000", inClusters: "0000, 0003, 0004, 0005, 0006, 0008", outClusters: "0019", manufacturer: "FeiBit", model: "FNB56-ZSW03LX2.0", deviceJoinName: "ZigBee Smart Switch"

    tiles(scale: 2) {
        standardTile ("switch", "device.switch", width: 2, height: 2, canChangeIcon: true, decoration: "flat") {
            state ("off", label: '${name}', action: "on", icon: "st.switches.light.off", backgroundColor: "#ffffff", nextState: "turningOn")
            state ("on", label: '${name}', action: "off", icon: "st.switches.light.on", backgroundColor: "#00a0dc", nextState: "turningOff")
            state ("turningOff", label: '${name}', action: "on", icon: "st.switches.light.off", backgroundColor: "#ffffff", nextState: "turningOn")
            state ("turningOn", label: '${name}', action: "off", icon: "st.switches.light.on", backgroundColor: "#00a0dc", nextState: "turningOff")
        }

        controlTile("level", "device.level", "slider", range:"(1..9)", height: 2, width: 2, canChangeIcon: true, decoration: "flat", inactiveLabel: false) {
            state "level", action: "setLevel"
        }

        main("switch")
        details(["switch", "level"])
    }
}

void on() {
    log.info "on()"
	sendEvent(name: "device.switch", value: "on", displayed: true, isStateChange: true)
    sendEvent(name: "switch", value: "on", displayed: true, isStateChange: true)
    parent.on(device)
    
    
}

void off() {
	log.info "off()"
    sendEvent(name: "switch", value: "off", displayed: true, isStateChange: true)
    sendEvent(name: "device.switch", value: "off", displayed: true, isStateChange: true)
    
	parent.off(device)
}

void refresh() {
	log.debug "refresh()"
    parent.refresh(device)
	parent.childRefresh(device.deviceNetworkId)
}


def createAndSendEvent(map) {
    log.debug "child[ ${device.deviceNetworkId} ].createAndSendEvent($map)"
    	results.each { name, value ->
    		sendEvent(name: name, value: value, displayed: true, isStateChange: true) // check if displayed: true, isStateChange: true is needed
  	}
  	return null
}

def parse(description) {
	log.debug "PARSE IN Child: $description"
}

def setLevel(value, rate = 10) { parent.setLevel(value, rate, device) }

def poll() {
    log.debug "poll()"
    parent.poll(device)
}

def ping() {
    log.debug "ping()"
    parent.ping(device)
}

def installed () {
    log.debug "installed() - parent $parent"

    sendEvent(name: "checkInterval", value: 5, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
}

def uninstalled () {
    log.debug "uninstalled()"
    //parent.delete()
}