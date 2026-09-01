/**
 * Phase 1 verification harness for @griffinkrutherford/liquid-glass-android.
 *
 * The library is installed from the packed npm tarball (see package.json), so
 * this app resolves it exactly the way an external consumer does.
 *
 * @format
 */

import React from 'react';
import {NativeModules, StatusBar} from 'react-native';
import {NavigationContainer} from '@react-navigation/native';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {HomeScreen} from './src/screens/HomeScreen';
import {MultiGlassScreen} from './src/screens/MultiGlassScreen';
import {ScrollScreen} from './src/screens/ScrollScreen';
import {ListScreen} from './src/screens/ListScreen';
import {LifecycleScreen} from './src/screens/LifecycleScreen';
import type {RootStackParamList} from './src/navigation';

const Stack = createNativeStackNavigator<RootStackParamList>();

const reportScreen = (name?: string) =>
  NativeModules.PerformanceMonitor?.setScreen(name ?? 'Unknown');

export default function App() {
  return (
    <SafeAreaProvider>
      <StatusBar barStyle="light-content" />
      <NavigationContainer
        onReady={() => reportScreen('Home')}
        onStateChange={state => reportScreen(state?.routes[state.index]?.name)}>
        <Stack.Navigator
          screenOptions={{
            headerStyle: {backgroundColor: '#0b1020'},
            headerTintColor: '#ffffff',
            contentStyle: {backgroundColor: '#0b1020'},
          }}>
          <Stack.Screen
            name="Home"
            component={HomeScreen}
            options={{title: 'Nested content'}}
          />
          <Stack.Screen
            name="Multi"
            component={MultiGlassScreen}
            options={{title: 'Multiple glass'}}
          />
          <Stack.Screen
            name="Scroll"
            component={ScrollScreen}
            options={{title: 'ScrollView'}}
          />
          <Stack.Screen
            name="List"
            component={ListScreen}
            options={{title: 'FlatList'}}
          />
          <Stack.Screen
            name="Lifecycle"
            component={LifecycleScreen}
            options={{title: 'Lifecycle'}}
          />
        </Stack.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
}
