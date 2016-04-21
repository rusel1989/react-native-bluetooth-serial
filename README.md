## React Native Bluetooth Serial

## Not published to npm yet


React Native version of [BluetoothSerial](https://github.com/don/BluetoothSerial) plugin. For both
android and ios

## Installation
1. Install package via npm: `npm i -S react-native-bluetooth-serial`
2. Link libraries with: `rnpm link`
3. For android you also need to put following code to `AndroidManifest.xml`
```
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
```

## Manual installation
Taken from [RCTCamera](https://github.com/lwansbrough/react-native-camera) and edited
#### iOS
1. `npm i -S react-native-bluetooth-serial`
2. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
3. Go to `node_modules` ➜ `react-native-bluetooth-serial` and add `RCTBluetoothSerial.xcodeproj`
4. In XCode, in the project navigator, select your project. Add `libRCTBluetoothSerial.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
5. Click `RCTBluetoothSerial.xcodeproj` in the project navigator and go the `Build Settings` tab. Make sure 'All' is toggled on (instead of 'Basic'). In the `Search Paths` section, look for `Header Search Paths` and make sure it contains both `$(SRCROOT)/../../react-native/React` and `$(SRCROOT)/../../../React` - mark both as `recursive`.
5. Run your project (`Cmd+R`)


#### Android
1. `npm i -S react-native-bluetooth-serial`
2. Open up `android/app/src/main/java/[...]/MainActivity.java
  - Add `import com.rusel.RCTBluetoothSerial.*;` to the imports at the top of the file
  - Add `new RCTBluetoothSerialPackage()` to the list returned by the `getPackages()` method
3. Append the following lines to `android/settings.gradle`:
    ```
    include ':react-native-bluetooth-serial'
    project(':react-native-bluetooth-serial').projectDir = new File(rootProject.projectDir,     '../node_modules/react-native-bluetooth-serial/android')
    ```
4. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
    ```
    compile project(':react-native-bluetooth-serial')
    ```

## API
All  api methods return promise.

### [android] enable()
Enable bluetooth currently in android only.

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









