const ReactNative = require('react-native')

const { NativeModules, DeviceEventEmitter } = ReactNative
const BluetoothSerial = NativeModules.BluetoothSerial

/**
 * Listen for available events
 * @param  {String} eventName Name of event one of connectionSuccess, connectionLost, data, rawData
 * @param  {Function} handler Event handler
 */
BluetoothSerial.on = (eventName, handler) => {
  DeviceEventEmitter.addListener(eventName, handler)
}

module.exports = BluetoothSerial
