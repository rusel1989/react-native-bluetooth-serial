import React, {
  Component,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  NativeModules,
  Platform
} from 'react-native'

const BluetoothSerial = NativeModules.BluetoothSerial

class BluetoothSerialExample extends Component {
  constructor (props) {
    super(props)
    this.state = {
      discovering: true,
      devices: [],
      conencted: false
    }
  }

  componentDidMount () {
    BluetoothSerial.list()
    .then((devices) => {
      this.setState({ devices })
    })
  }

  /**
   * Discover unpaired devices, works only in android
   */
  discoverUnpaired () {
    BluetoothSerial.discoverUnpairedDevices()
    .then((unpairedDevices) => {
      const devices = this.state.devices
      unpairedDevices.forEach((device) => devices.push(device))
      this.setState({ devices })
    })
  }

  /**
   * Connect to bluetooth device by id
   * @param  {String} id Device id
   */
  connect (id) {
    BluetoothSerial.connect(id)
    .then((res) => {
      alert(res.message)
      this.setState({ connected: true })
    })
    .catch((err) => alert(err))
  }

  render () {
    return (
      <View style={styles.container}>
        <Text style={styles.heading}>Bluetooth Serial Example</Text>
        <Text>Bluetooth devices</Text>
        <View style={styles.listContainer}>
          {this.state.devices.map((device, i) => {
            return (
              <TouchableOpacity style={styles.listItem} onPress={this.connect.bind(this, device.id)}>
                <Text>{`${device.name}<${device.id}>`}</Text>
              </TouchableOpacity>
            )
          })}
        </View>
        <View>
          {Platform.OS === 'android'
            ? (
              <TouchableOpacity style={styles.button} onPress={this.discoverUnpaired.bind(this)}>
                <Text style={{ color: '#fff' }}>Discover unpaired devices</Text>
              </TouchableOpacity>
            ) : null}

        </View>
      </View>
    )
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    backgroundColor: '#F5FCFF'
  },
  heading: {
    fontWeight: 'bold',
    fontSize: 24
  },
  listContainer: {
    borderColor: '#ccc',
    borderTopWidth: 0.5
  },
  listItem: {
    padding: 25,
    borderColor: '#ccc',
    borderBottomWidth: 0.5
  },
  button: {
    padding: 25,
    backgroundColor: '#4C4C4C'
  }
})

export default BluetoothSerialExample

