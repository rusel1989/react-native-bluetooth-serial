//
//  RCTBluetoothSerial.h
//  RCTBluetoothSerial
//
//  Created by Jakub Martyčák on 17.04.16.
//  Copyright © 2016 Jakub Martyčák. All rights reserved.
//

#import <React/RCTBridgeModule.h>
#import <React/RCTEventDispatcher.h>

//#import "RCTEventEmitter.h" Wasnt working properly yet, events were fired but listeneres not called
#import "BLE.h"

@interface RCTBluetoothSerial : NSObject <RCTBridgeModule,BLEDelegate> {
    BLE *_bleShield;
    BOOL _subscribed;
    RCTPromiseResolveBlock _connectionResolver;
    RCTPromiseRejectBlock _connectionRejector;
    NSString* _subscribeCallbackId;
    NSString* _subscribeBytesCallbackId;
    NSString* _rssiCallbackId;
    NSMutableString *_buffer;
    NSString *_delimiter;
}
@end
