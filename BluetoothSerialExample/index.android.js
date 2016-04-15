import React, {
  AppRegistry,
  Component,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  NativeModules
} from 'react-native'

const BluetoothSerial = NativeModules.BluetoothSerial

class BluetoothSerialExample extends Component {
  constructor (props) {
    super(props)
    this.state = {
      discovering: true,
      devices: []
    }
  }

  componentDidMount () {
    BluetoothSerial.discoverUnpairedDevices()
    .then((devices) => this.setState({ devices }))
  }

  /**
   * Connect to bluetooth device by id
   * @param  {String} id Device id
   */
  connect (id) {
    BluetoothSerial.connect(id, false)
    .then((connected) => alert(`Connected to id ${id}`))
    .catch((err) => alert(`Error conencting to device, ${err}`))
  }

  render () {
    return (
      <View style={styles.container}>
        <Text>Unpaired devices</Text>
        <View style={{ borderColor: '#eee', borderTopWidth: 0.5 }}>
          {this.state.devices.map((device, i) => {
            return (
              <TouchableOpacity style={styles.button} onPress={this.connect.bind(this, device.id)}>
                <Text>{`${device.name}<${device.address}>`}</Text>
              </TouchableOpacity>
            )
          })}
        </View>
      </View>
    )
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF'
  },
  button: {
    paddingHorizontal: 25,
    borderColor: '#eee',
    borderBottomWidth: 0.5
  }
})

AppRegistry.registerComponent('BluetoothSerialExample', () => BluetoothSerialExample)
