export type RootStackParamList = {
  Home: undefined;
  Multi: undefined;
  Scroll: undefined;
  List: undefined;
  Lifecycle: undefined;
};

declare global {
  namespace ReactNavigation {
    interface RootParamList extends RootStackParamList {}
  }
}
