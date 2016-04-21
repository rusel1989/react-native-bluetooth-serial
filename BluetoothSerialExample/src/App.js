import React, {
  Component,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  NativeModules,
  Platform,
  Switch
} from 'react-native'

import BluetoothSerial from 'react-native-bluetooth-serial'

const Button = ({ label, onPress }) =>
  <TouchableOpacity style={styles.button} onPress={onPress}>
    <Text style={{ color: '#fff' }}>{label}</Text>
  </TouchableOpacity>

class BluetoothSerialExample extends Component {
  constructor (props) {
    super(props)
    this.state = {
      discovering: false,
      devices: [],
      conencted: false
    }
  }

  componentWillMount () {
    Promise.all([
      BluetoothSerial.isEnabled(),
      BluetoothSerial.list()
    ])
    .then((values) => {
      const [ isEnabled, devices ] = values
      this.setState({ isEnabled, devices })
    })
  }

  /**
   * [android]
   * enable bluetooth on device
   */
  enable () {
    BluetoothSerial.enable()
    .then((res) => this.setState({ isEnabled: res }))
    .catch((err) => alert(err))
  }

  /**
   * [android]
   * Discover unpaired devices, works only in android
   */
  discoverUnpaired () {
    if (this.state.discovering) {
      return false
    } else {
      this.setState({ discovering: true })
      BluetoothSerial.discoverUnpairedDevices()
      .then((unpairedDevices) => {
        const devices = this.state.devices
        const deviceIds = devices.map((d) => d.id)
        unpairedDevices.forEach((device) => {
          if (deviceIds.indexOf(device.id) < 0) {
            devices.push(device)
          }
        })
        this.setState({ devices, discovering: false })
      })
    }
  }

  /**
   * Connect to bluetooth device by id
   * @param  {Object} device
   */
  connect (device) {
    this.setState({ connecting: true })
    BluetoothSerial.connect(device.id)
    .then((res) => {
      alert(res.message)
      this.setState({ device, connected: true, connecting: false })
    })
    .catch((err) => alert(err))
  }

  /**
   * Disconnect from bluetooth device
   */
  disconnect () {
    this.setState({ connected: false })
    BluetoothSerial.disconnect()
    .catch((err) => alert(err))
  }

  /**
   * Toggle connection when we have active device
   * @param  {Boolean} value
   */
  toggleConnect (value) {
    if (value === true && this.state.device) {
      this.connect(this.state.device)
    } else {
      this.disconnect()
    }
  }

  /**
   * Write message to device
   * @param  {String} message
   */
  write (message) {
    if (!this.state.connected) {
      alert('You must connect to device first')
    }

    BluetoothSerial.write(message)
    .then((res) => {
      alert('Successfuly wrote to device')
      this.setState({ connected: true })
    })
    .catch((err) => alert(err))
  }

  render () {
    return (
      <View style={styles.container}>
        <Text style={styles.heading}>Bluetooth Serial Example</Text>
        {this.state.isEnabled === false
        ? (
          <View style={{ backgroundColor: '#ff6523' }}>
            <Text style={[styles.connectionInfo, { color: '#fff', alignSelf: 'center' }]}>
              Bluetooth not enabled !
              {Platform.OS === 'android'
              ? (
                <TouchableOpacity onPress={this.enable.bind(this)}>
                  <Text style={{ fontWeight: 'bold' }}>ENABLE</Text>
                </TouchableOpacity>
              ) : null}
            </Text>
          </View>
        ) : null}
        <View style={styles.connectionInfoWrapper}>
          <View>
            <Switch
              onValueChange={this.toggleConnect.bind(this)}
              disabled={!this.state.device}
              value={this.state.connected || this.state.connecting}/>
          </View>
          <View>
            {this.state.connected
            ? (
              <Text style={styles.connectionInfo}>
                ✓ Connected to {this.state.device.name}
              </Text>
            ) : (
              <Text style={[styles.connectionInfo, { color: '#ff6523' }]}>
                ✗ Not connected to any device
              </Text>
            )}
          </View>
        </View>

        <Text style={{ alignSelf: 'center' }}>Bluetooth devices</Text>
        <View style={styles.listContainer}>
          {this.state.devices.map((device, i) => {
            return (
              <TouchableOpacity key={`${device.id}_${i}`} style={styles.listItem} onPress={this.connect.bind(this, device)}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                  <Text style={{ fontWeight: 'bold' }}>{device.name}</Text>
                  <Text>{`<${device.id}>`}</Text>
                </View>
              </TouchableOpacity>
            )
          })}
        </View>
        <View style={{ flexDirection: 'row', justifyContent: 'center' }}>
          {Platform.OS === 'android'
          ? (
            <Button
              label={this.state.discovering ? '... Discovering' : 'Discover devices'}
              onPress={this.discoverUnpaired.bind(this)} />
          ) : null}
          <Button
            label='Write to device'
            onPress={this.write.bind(this, 'test')} />
        </View>
      </View>
    )
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5FCFF'
  },
  heading: {
    fontWeight: 'bold',
    fontSize: 24,
    marginVertical: 10,
    alignSelf: 'center'
  },
  connectionInfoWrapper: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 25
  },
  connectionInfo: {
    fontWeight: 'bold',
    alignSelf: 'center',
    fontSize: 18,
    marginVertical: 10,
    color: '#238923'
  },
  listContainer: {
    marginTop: 15,
    borderColor: '#ccc',
    borderTopWidth: 0.5
  },
  listItem: {
    flex: 1,
    padding: 25,
    borderColor: '#ccc',
    borderBottomWidth: 0.5
  },
  button: {
    margin: 5,
    padding: 25,
    backgroundColor: '#4C4C4C'
  }
})

export default BluetoothSerialExample

