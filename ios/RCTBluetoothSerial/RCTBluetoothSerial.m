//
//  RCTBluetoothSerial.m
//  RCTBluetoothSerial
//
//  Created by Jakub Martyčák on 17.04.16.
//  Copyright © 2016 Jakub Martyčák. All rights reserved.
//

#import "RCTBluetoothSerial.h"

@interface RCTBluetoothSerial()
- (NSString *)readUntilDelimiter:(NSString *)delimiter;
- (NSMutableArray *)getPeripheralList;
- (void)sendDataToSubscriber;
- (CBPeripheral *)findPeripheralByUUID:(NSString *)uuid;
- (void)connectToUUID:(NSString *)uuid;
- (void)listPeripheralsTimer:(NSTimer *)timer;
- (void)connectFirstDeviceTimer:(NSTimer *)timer;
- (void)connectUuidTimer:(NSTimer *)timer;
@end

@implementation RCTBluetoothSerial

RCT_EXPORT_MODULE();

+ (BOOL)requiresMainQueueSetup
{
    return NO;
}

@synthesize bridge = _bridge;

- (instancetype)init {
    _bleShield = [[BLE alloc] init];
    [_bleShield controlSetup];
    [_bleShield setDelegate:self];

    _buffer = [[NSMutableString alloc] init];
    return self;
}

- (dispatch_queue_t)methodQueue
{
    // run all module methods in main thread
    // if we don't no timer callbacks got called
    return dispatch_get_main_queue();
}

- (NSArray<NSString *> *)supportedEvents
{
    return @[@"connectionSuccess",@"connectionLost",@"bluetoothEnabled",@"bluetoothDisabled",@"data"];
}


#pragma mark - Methods available form Javascript

RCT_EXPORT_METHOD(connect:(NSString *)uuid
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejector:(RCTPromiseRejectBlock)reject)
{

    NSLog(@"connect");

    // if the uuid is null or blank, scan and
    // connect to the first available device

    if (uuid == (NSString *)[NSNull null]) {
        [self connectToFirstDevice];
    } else if ([uuid isEqualToString:@""]) {
        [self connectToFirstDevice];
    } else {
        [self connectToUUID:uuid];
    }

    _connectionResolver = resolve;
    _connectionRejector = reject;
}

RCT_EXPORT_METHOD(disconnect:(RCTPromiseResolveBlock)resolve
                  rejector:(RCTPromiseRejectBlock)reject)
{

    NSLog(@"disconnect");

    _connectionResolver = nil;
    _connectionRejector = nil;

    if (_bleShield.activePeripheral) {
        if(_bleShield.activePeripheral.state == CBPeripheralStateConnected)
        {
            [[_bleShield CM] cancelPeripheralConnection:[_bleShield activePeripheral]];
        }
    }

    resolve((id)kCFBooleanTrue);
}

RCT_EXPORT_METHOD(subscribe:(NSString *)delimiter
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejector:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"subscribe");

    if (delimiter != nil) {
        _delimiter = [delimiter copy];
        _subscribed = TRUE;
        resolve((id)kCFBooleanTrue);
    } else {
        NSError *err = nil;
        reject(@"no_delimiter", @"Delimiter was null", err);
    }
}

RCT_EXPORT_METHOD(unsubscribe:(NSString *)delimiter
                  resolver:(RCTPromiseResolveBlock)resolve)
{
    NSLog(@"unsubscribe");

    _delimiter = nil;
    _subscribed = FALSE;

    resolve((id)kCFBooleanTrue);
}

RCT_EXPORT_METHOD(writeToDevice:(NSString *)message
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejector:(RCTPromiseRejectBlock)reject)
{
    NSLog(@"write");
    if (message != nil) {
        NSData *data = [[NSData alloc] initWithBase64EncodedString:message options:NSDataBase64DecodingIgnoreUnknownCharacters];
        [_bleShield write:data];
        resolve((id)kCFBooleanTrue);
    } else {
        NSError *err = nil;
        reject(@"no_data", @"Data was null", err);
    }
}

RCT_EXPORT_METHOD(list:(RCTPromiseResolveBlock)resolve
                  rejector:(RCTPromiseRejectBlock)reject)
{
    [self scanForBLEPeripherals:3];
    [NSTimer scheduledTimerWithTimeInterval:(float)3.0
                                     target:self
                                   selector:@selector(listPeripheralsTimer:)
                                   userInfo:resolve
                                    repeats:NO];
}

RCT_EXPORT_METHOD(isEnabled:(RCTPromiseResolveBlock)resolve
                  rejector:(RCTPromiseRejectBlock)reject)
{
    // short delay so CBCentralManger can spin up bluetooth
    [NSTimer scheduledTimerWithTimeInterval:(float)0.2
                                     target:self
                                   selector:@selector(bluetoothStateTimer:)
                                   userInfo:resolve
                                    repeats:NO];

}

RCT_EXPORT_METHOD(isConnected:(RCTPromiseResolveBlock)resolve
                  rejector:(RCTPromiseRejectBlock)reject)
{
    if (_bleShield.isConnected) {
        resolve((id)kCFBooleanTrue);
    } else {
        resolve((id)kCFBooleanFalse);
    }
}

RCT_EXPORT_METHOD(available:(RCTPromiseResolveBlock)resolve
                  rejector:(RCTPromiseRejectBlock)reject)
{
    // future versions could use messageAsNSInteger, but realistically, int is fine for buffer length
    NSNumber *buffLen = [NSNumber numberWithInteger:[_buffer length]];
    resolve(buffLen);
}

RCT_EXPORT_METHOD(read:(RCTPromiseResolveBlock)resolve
                  rejector:(RCTPromiseRejectBlock)reject)
{
    NSString *message = @"";

    if ([_buffer length] > 0) {
        long end = [_buffer length] - 1;
        message = [_buffer substringToIndex:end];
        NSRange entireString = NSMakeRange(0, end);
        [_buffer deleteCharactersInRange:entireString];
    }

    resolve(message);
}

RCT_EXPORT_METHOD(readUntil:(NSString *)delimiter
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejector:(RCTPromiseRejectBlock)reject)
{
    NSString *message = [self readUntilDelimiter:delimiter];
    resolve(message);
}

RCT_EXPORT_METHOD(clear:(RCTPromiseResolveBlock)resolve)
{
    long end = [_buffer length] - 1;
    NSRange truncate = NSMakeRange(0, end);
    [_buffer deleteCharactersInRange:truncate];
    resolve((id)kCFBooleanTrue);
}

#pragma mark - BLEDelegate

- (void)bleDidReceiveData:(unsigned char *)data length:(int)length
{
    NSLog(@"bleDidReceiveData");

    // Append to the buffer
    NSData *d = [NSData dataWithBytes:data length:length];
    NSString *s = [[NSString alloc] initWithData:d encoding:NSUTF8StringEncoding];
    NSLog(@"Received %@", s);

    if (s) {
        [_buffer appendString:s];

        if (_subscribed) {
            [self sendDataToSubscriber]; // only sends if a delimiter is hit
        }

    } else {
        NSLog(@"Error converting received data into a String.");
    }

    // Always send raw data if someone is listening
    //if (_subscribeBytesCallbackId) {
    //    NSData* nsData = [NSData dataWithBytes:(const void *)data length:length];
    //}

}

- (void)bleDidChangedState:(bool)isEnabled
{
    NSLog(@"bleDidChangedState");
    NSString *eventName;
    if (isEnabled) {
        eventName = @"bluetoothEnabled";
    } else {
        eventName = @"bluetoothDisabled";
    }
    [self.bridge.eventDispatcher sendDeviceEventWithName:eventName body:nil];
}

- (void)bleDidConnect
{
    NSLog(@"bleDidConnect");
    [self.bridge.eventDispatcher sendDeviceEventWithName:@"connectionSuccess" body:nil];
    //[self sendEventWithName:@"connectionSuccess" body:nil];

    if (_connectionResolver) {
        _connectionResolver((id)kCFBooleanTrue);
    }
}

- (void)bleDidDisconnect
{
    // TODO is there anyway to figure out why we disconnected?
    NSLog(@"bleDidDisconnect");
    [self.bridge.eventDispatcher sendDeviceEventWithName:@"connectionLost" body:nil];
    //[self sendEventWithName:@"connectionLost" body:nil];

    _connectionResolver = nil;
}

#pragma mark - timers

-(void)listPeripheralsTimer:(NSTimer *)timer
{
    RCTPromiseResolveBlock resolve = [timer userInfo];
    NSMutableArray *peripherals = [self getPeripheralList];
    resolve(peripherals);
}

-(void)connectFirstDeviceTimer:(NSTimer *)timer
{
    if(_bleShield.peripherals.count > 0) {
        NSLog(@"Connecting");
        [_bleShield connectPeripheral:[_bleShield.peripherals objectAtIndex:0]];
    } else {
        NSString *message = @"Did not find any BLE peripherals";
        NSError *err = nil;
        NSLog(@"%@", message);
        _connectionRejector(@"no_peripherals", message, err);

    }
}

-(void)connectUuidTimer:(NSTimer *)timer
{
    NSString *uuid = [timer userInfo];
    CBPeripheral *peripheral = [self findPeripheralByUUID:uuid];

    if (peripheral) {
        [_bleShield connectPeripheral:peripheral];
    } else {
        NSString *message = [NSString stringWithFormat:@"Could not find peripheral %@.", uuid];
        NSError *err = nil;
        NSLog(@"%@", message);
        _connectionRejector(@"wrong_uuid", message, err);

    }
}

- (void)bluetoothStateTimer:(NSTimer *)timer
{
    RCTPromiseResolveBlock resolve = [timer userInfo];
    int bluetoothState = [[_bleShield CM] state];
    BOOL enabled = bluetoothState == CBCentralManagerStatePoweredOn;

    if (enabled) {
        resolve((id)kCFBooleanTrue);
    } else {
        resolve((id)kCFBooleanFalse);
    }
}

#pragma mark - internal implemetation

- (NSString*)readUntilDelimiter: (NSString*) delimiter
{

    NSRange range = [_buffer rangeOfString: delimiter];
    NSString *message = @"";

    if (range.location != NSNotFound) {

        long end = range.location + range.length;
        message = [_buffer substringToIndex:end];

        NSRange truncate = NSMakeRange(0, end);
        [_buffer deleteCharactersInRange:truncate];
    }
    return message;
}

- (NSMutableArray*) getPeripheralList
{

    NSMutableArray *peripherals = [NSMutableArray array];

    for (int i = 0; i < _bleShield.peripherals.count; i++) {
        NSMutableDictionary *peripheral = [NSMutableDictionary dictionary];
        CBPeripheral *p = [_bleShield.peripherals objectAtIndex:i];

        NSString *uuid = p.identifier.UUIDString;
        [peripheral setObject: uuid forKey: @"uuid"];
        [peripheral setObject: uuid forKey: @"id"];

        NSString *name = [p name];
        if (!name) {
            name = [peripheral objectForKey:@"uuid"];
        }
        [peripheral setObject: name forKey: @"name"];

        NSNumber *rssi = [p btsAdvertisementRSSI];
        if (rssi) { // BLEShield doesn't provide advertised RSSI
            [peripheral setObject: rssi forKey:@"rssi"];
        }

        [peripherals addObject:peripheral];
    }

    return peripherals;
}

// calls the JavaScript subscriber with data if we hit the _delimiter
- (void) sendDataToSubscriber {

    NSString *message = [self readUntilDelimiter:_delimiter];

    if ([message length] > 0) {
      [self.bridge.eventDispatcher sendDeviceEventWithName:@"data" body:@{@"data": message}];
    }

}

// Ideally we'd get a callback when found, maybe _bleShield can be modified
// to callback on centralManager:didRetrievePeripherals. For now, use a timer.
- (void)scanForBLEPeripherals:(int)timeout
{

    NSLog(@"Scanning for BLE Peripherals");

    // disconnect
    if (_bleShield.activePeripheral) {
        if(_bleShield.activePeripheral.state == CBPeripheralStateConnected)
        {
            [[_bleShield CM] cancelPeripheralConnection:[_bleShield activePeripheral]];
            return;
        }
    }

    // remove existing peripherals
    if (_bleShield.peripherals) {
        _bleShield.peripherals = nil;
    }

    [_bleShield findBLEPeripherals:timeout];
}

- (void)connectToFirstDevice
{

    [self scanForBLEPeripherals:3];

    [NSTimer scheduledTimerWithTimeInterval:(float)3.0
                                     target:self
                                   selector:@selector(connectFirstDeviceTimer:)
                                   userInfo:nil
                                    repeats:NO];
}

- (void)connectToUUID:(NSString *)uuid
{

    int interval = 0;

    if (_bleShield.peripherals.count < 1) {
        interval = 3;
        [self scanForBLEPeripherals:interval];
    }

    [NSTimer scheduledTimerWithTimeInterval:interval
                                     target:self
                                   selector:@selector(connectUuidTimer:)
                                   userInfo:uuid
                                    repeats:NO];
}

- (CBPeripheral*)findPeripheralByUUID:(NSString*)uuid
{

    NSMutableArray *peripherals = [_bleShield peripherals];
    CBPeripheral *peripheral = nil;

    for (CBPeripheral *p in peripherals) {

        NSString *other = p.identifier.UUIDString;

        if ([uuid isEqualToString:other]) {
            peripheral = p;
            break;
        }
    }
    return peripheral;
}

@end
