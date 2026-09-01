import {StyleSheet} from 'react-native';

export const glassAssets = {
  backdrop: require('./assets/backdrop.png'),
  icon: require('./assets/icon.png'),
};

export const shared = StyleSheet.create({
  scene: {flex: 1, backgroundColor: '#0b1020'},
  fill: {flex: 1},
  card: {
    padding: 20,
    justifyContent: 'center',
  },
  title: {color: '#ffffff', fontSize: 22, fontWeight: '700'},
  body: {color: 'rgba(255,255,255,0.88)', fontSize: 14, marginTop: 6},
  caption: {color: 'rgba(255,255,255,0.66)', fontSize: 12},
  row: {flexDirection: 'row', alignItems: 'center', gap: 12},
  icon: {width: 36, height: 36, borderRadius: 18},
  thumb: {width: 64, height: 64, borderRadius: 12},
  button: {
    marginTop: 14,
    alignSelf: 'flex-start',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 999,
    backgroundColor: 'rgba(255,255,255,0.18)',
  },
  buttonLabel: {color: '#ffffff', fontSize: 14, fontWeight: '600'},
});
