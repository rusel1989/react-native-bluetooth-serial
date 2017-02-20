## React Native Bluetooth Serial

React Native version of [BluetoothSerial](https://github.com/don/BluetoothSerial) plugin. For both
android and ios

## Compatibility
Officialy this library supports React Native >= 0.25, it may run on older versions but no guarantees.

## Installation
1. Install package via npm: `npm i -S react-native-bluetooth-serial`
2. Link libraries with: `rnpm link` or `react-native link` for React Native >= 0.27
3. For android you also need to put following code to `AndroidManifest.xml`
```
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
```

## Manual installation
#### iOS
1. `npm i -S react-native-bluetooth-serial`
2. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
3. Go to `node_modules` ➜ `react-native-bluetooth-serial` and add `RCTBluetoothSerial.xcodeproj`
4. In XCode, in the project navigator, select your project. Add `libRCTBluetoothSerial.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
5. Click `RCTBluetoothSerial.xcodeproj` in the project navigator and go the `Build Settings` tab. Make sure 'All' is toggled on (instead of 'Basic'). In the `Search Paths` section, look for `Header Search Paths` and make sure it contains both `$(SRCROOT)/../../react-native/React` and `$(SRCROOT)/../../../React` - mark both as `recursive`.
5. Run your project (`Cmd+R`)


#### Android
1. `npm i -S react-native-bluetooth-serial`
2. Open up `android/app/src/main/java/[...]/MainActivity.java` or `MainApplication.java` for React Native >= 0.29
  - Add `import com.rusel.RCTBluetoothSerial.*;` to the imports at the top of the file
  - Add `new RCTBluetoothSerialPackage()` to the list returned by the `getPackages()` method
3. Append the following lines to `android/settings.gradle`:
    ```
    include ':react-native-bluetooth-serial'
    project(':react-native-bluetooth-serial').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-bluetooth-serial/android')
    ```
4. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
    ```
    compile project(':react-native-bluetooth-serial')
    ```

## Example
As bluetooth is not available in any simulators, if you want to test it with some bluetooth peripherals you have
to run the example on actual device.
1. `git clone https://github.com/rusel1989/react-native-bluetooth-serial.git`
2. `cd react-native-bluetooth-serial/BluetoothSerialExample`
3. `npm i`
4. `react-native run-ios/run-android`

# Reading and writing
In Android after you connect to peripheral `write` and `read` methods should work for most of devices out of the box.
On ios with BLE it is little bit complicated, you need to know correct serice and characteristics UUIDs. Currently
supported and preconfigured services are Red Bear lab, Adafruit BLE, Bluegiga, Laird Virtual Serial Port and Rongta. If
you know about some services that you think should be supported send PR.

In near future i will try to improve device discovery on ios and also make services configurable from js.

## API
All following methods have been tested on both android and ios devices and return promise.

### [android] enable()
Enable bluetooth currently in android only.

### [android] disable()
Disable bluetooth currently in android only.

### isEnabled()
Resolves to boolean value indicating current bluetooth state.

### list()
Resolves to array of available devices, devices are in following format:
```
{
    id: String // MAC address (android) or UUID (ios)
    name: Sring // Device name
}
```
doesn't return unpaired devices in android.

### [android] discoverUnpairedDevices()
Resolves to array of unpaired devices on android, device will pair after successful connection. Format is same as list method.

### connect(String id)
Connect to device by MAC address on android or UUID on ios. Resolves to object with message or rejects with reason of failure.

### disconnect()
Disconnects from current device should always resolve to true.

### isConnected()
Resolves to true if there is active connection to device or false if not.

### write(Buffer|String data)
Write data to connected device, for now buffer is internally converted to Base64 encoded string and decoded to byte array
on native side, beacause react native is currently not capable of passing buffer directly to native methods. Resolves
to true when write was successful, otherwise rejects with error.


## Events
You can listen to few event with `BluetoothSerial.on(eventName, callback)`

Currently provided events are:
- `bluetoothEnabled` - when user enabled bt
- `bluetoothDisabled` - when user disabled bt
- `connectionSuccess` - when app connected to device
- `connectionLost` - when app lost connection to device (fired with `bluetoothDisabled`)

You can use `BluetoothSerial.removeListener(eventName, callback)` to stop listening to an event

## TODO
- Make services configurable on ios









