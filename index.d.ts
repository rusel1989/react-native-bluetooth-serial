// Type definitions for @secullum/react-native-bluetooth-serial
// Project: https://github.com/rusel1989/react-native-bluetooth-serial
// Definitions by: Rodrigo Weber <https://github.com/RodrigoAWeber>
// Definitions: https://github.com/DefinitelyTyped/DefinitelyTyped
// TypeScript Version: 2.7.2
declare module '@secullum/react-native-bluetooth-serial' {
    import * as React from "react";
    import * as ReactNative from "react-native";

    class Buffer {
        constructor(data: number[]);
    }

    namespace BluetoothSerial {
        const on: (eventName: string, handler: () => void) => void
        const removeListener: (eventName: string, handler: () => void) => void
        const write: (data: Buffer | string) => Promise<Boolean>;
        const list: () => Promise<Array<{ id: string, name: string }>>;
        const isEnabled: () => Promise<Boolean>;
        const connect: (id: string) => Promise<void>;
        const disconnect: () => Promise<void>;
        const isConnected: () => Promise<Boolean>;
    }

    export = BluetoothSerial
}