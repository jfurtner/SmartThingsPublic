//Credit to Serge Sozonoff whom I based this code on.
metadata {
	definition (name: "Securifi Moisture Sensor", namespace: "jfurtner", author: "jfurtner") {
		capability "Water Sensor"
		capability "Sensor"
        capability "Configuration"
        capability "Speech Recognition"

        attribute "tamperSwitch","ENUM",["open","closed"]

        command "enrollResponse"

		fingerprint endpointId: '08', profileId: '0104', inClusters: "0000,0003,0500", outClusters: "0003"
	}

	// simulator metadata
	simulator {
		status "active": "zone report :: type: 19 value: 0031"
		status "inactive": "zone report :: type: 19 value: 0030"
	}

	// UI tile definitions
	tiles {
    	tiles(scale: 2) {
			multiAttributeTile(name:"moisture", type: "generic", width: 6, height: 4){
				tileAttribute ("device.moisture", key: "PRIMARY_CONTROL") {
					attributeState "dry", label: "Dry", icon:"st.alarm.water.dry", backgroundColor:"#ffffff"
					attributeState "flood", label: "Wet", icon:"st.alarm.water.wet", backgroundColor:"#53a7c0"
				}
			}
        }

        standardTile("tamperSwitch", "device.tamperSwitch", width: 2, height: 1) {
			state("open", label:'${name}', icon:"st.contact.contact.open", backgroundColor:"#ffa81e")
			state("closed", label:'${name}', icon:"st.contact.contact.closed", backgroundColor:"#79b821")
		}        
        valueTile("logEvent", "device.phraseSpoken", width:2, height:2) {
        	state 'val', label:'${currentValue}', defaultState: ''
        }
		main (["moisture"])
		details(["moisture","tamperSwitch"])
	}
}
def configure() {
	logDebug("** PIR02 ** configure called for device with network ID ${device.deviceNetworkId}")

	String zigbeeId = swapEndianHex(device.hub.zigbeeId)
	logDebug "Configuring Reporting, IAS CIE, and Bindings."
	def configCmds = [
    	"zcl global write 0x500 0x10 0xf0 {${zigbeeId}}", "delay 200",
		"send 0x${device.deviceNetworkId} 1 1", "delay 1500",

        "zcl global send-me-a-report 1 0x20 0x20 0x3600 0x3600 {01}", "delay 200",
        "send 0x${device.deviceNetworkId} 1 1", "delay 1500",

		"zdo bind 0x${device.deviceNetworkId} 1 1 0x001 {${device.zigbeeId}} {}", "delay 1500",

        "raw 0x500 {01 23 00 00 00}", "delay 200",
        "send 0x${device.deviceNetworkId} 1 1", "delay 1500",
	]
    return configCmds // send refresh cmds as part of config
}
def enrollResponse() {
	logDebug "Sending enroll response"
    [	

	"raw 0x500 {01 23 00 00 00}", "delay 200",
    "send 0x${device.deviceNetworkId} 1 1"

    ]
}

def logDebug(String message) {
	log.debug(message)
    sendEvent(name: 'phraseSpoken', value: message)
}

// Parse incoming device messages to generate events
def parse(String description) {
	logDebug("** WTD01 parse received ** ${description}")
    def result = []        
	Map map = [:]

    if (description?.startsWith('zone status')) {
	    map = parseIasMessage(description)
    }

	//logDebug "Parse returned $map"
    map.each { k, v ->
    	logDebug("sending event ${v}")
        sendEvent(v)
    }

//	def result = map ? createEvent(map) : null

    if (description?.startsWith('enroll request')) {
    	List cmds = enrollResponse()
        logDebug "enroll response: ${cmds}"
        result = cmds?.collect { new physicalgraph.device.HubAction(it) }
    }
    return result
}

private Map parseIasMessage(String description) {
    List parsedMsg = description.split(' ')
    String msgCode = parsedMsg[2]

    Map resultMap = [:]
    switch(msgCode) {
        case '0x0038': // Dry
            //logDebug 'Detected Dry'
            resultMap["moisture"] = [name: "moisture", value: "dry"]
            resultMap["tamperSwitch"] = getContactResult("closed")            
            break

        case '0x0039': // Wet
            //logDebug 'Detected Moisture'
            resultMap["moisture"] = [name: "moisture", value: "flood"]
            resultMap["tamperSwitch"] = getContactResult("closed")            
            break

        case '0x0032': // Tamper Alarm
        	//logDebug 'Detected Tamper'
            resultMap["moisture"] = [name: "moisture", value: "active"]
            resultMap["tamperSwitch"] = getContactResult("open")            
            break

        case '0x0034': // Supervision Report
        	//logDebug 'No flood with tamper alarm'
            resultMap["moisture"] = [name: "moisture", value: "inactive"]
            resultMap["tamperSwitch"] = getContactResult("open")            
            break

        case '0x0035': // Restore Report
        	//logDebug 'Moisture with tamper alarm'
            resultMap["moisture"] = [name: "moisture", value: "active"]
            resultMap["tamperSwitch"] = getContactResult("open") 
            break

//        case '0x0036': // Trouble/Failure
//        	logDebug 'msgCode 36 not handled yet'
//            break
        default:
        	logDebug "msgCode ${msgCode}: ${description}"
        	break
    }
    return resultMap
}

private Map getContactResult(value) {
	//logDebug "Tamper Switch Status ${value}"
	def linkText = getLinkText(device)
	def descriptionText = "${linkText} was ${value == 'open' ? 'opened' : 'closed'}"
	return [
		name: 'tamperSwitch',
		value: value,
		descriptionText: descriptionText
	]
}


private hex(value) {
	new BigInteger(Math.round(value).toString()).toString(16)
}

private String swapEndianHex(String hex) {
    reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
    int i = 0;
    int j = array.length - 1;
    byte tmp;
    while (j > i) {
        tmp = array[j];
        array[j] = array[i];
        array[i] = tmp;
        j--;
        i++;
    }
    return array
}