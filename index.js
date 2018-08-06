const ReactNative = require('react-native')
const { Buffer } = require('buffer')
const { NativeModules, DeviceEventEmitter } = ReactNative;
const BluetoothSerial = NativeModules.BluetoothSerial

const listeners = {};

const removeListenerByName = function(eventName)  {
  listeners[eventName].forEach(listener => {
    DeviceEventEmitter.removeListener(eventName, listener)
  });
  delete listeners[eventName];
}
/**
 * Listen for available events
 * @param  {String} eventName Name of event one of connectionSuccess, connectionLost, data, rawData
 * @param  {Function} handler Event handler
 */
BluetoothSerial.on = (eventName, handler) => {
  if(!listeners.hasOwnProperty(eventName)) {
    listeners[eventName] = [];
  }
  listeners[eventName].push(handler);

  DeviceEventEmitter.addListener(eventName, handler)
}

/**
 * Stop listening for event
 * @param  {String} eventName Name of event one of connectionSuccess, connectionLost, data, rawData
 * @param  {Function} handler Event handler
 */
BluetoothSerial.removeListener = (eventName, handler) => {
  DeviceEventEmitter.removeListener(eventName, handler)
}

/**
 * Stop all listening of events
 */
BluetoothSerial.removeAllRegisterListener = () => {
  for(var eventName in listeners) {
    removeListenerByName(eventName);
  };
}

/**
 * Stop all listening of events
 */
BluetoothSerial.removeEventNameListeners = (eventName) => {
  for(var currentEventName in listeners) {
      if(eventName === currentEventName) {
        removeListenerByName(currentEventName);
      }
  };
}

/**
 * Write data to device, you can pass string or buffer,
 * We must convert to base64 in RN there is no way to pass buffer directly
 * @param  {Buffer|String} data
 * @return {Promise<Boolean>}
 */
BluetoothSerial.write = (data) => {
  if (typeof data === 'string') {
    data = new Buffer(data)
  }
  return BluetoothSerial.writeToDevice(data.toString('base64'))
}

module.exports = BluetoothSerial