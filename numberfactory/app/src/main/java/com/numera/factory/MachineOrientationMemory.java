package com.numera.factory;

/**
 * Last successfully selected rotation, separately for each machine kind.
 * UI and SharedPreferences are intentionally owned by FactoryView.
 */
final class MachineOrientationMemory {
    private final int[] types,rotations;
    MachineOrientationMemory(int[] machineTypes) {
        types=machineTypes.clone();
        rotations=new int[types.length];
    }
    int rotationFor(int type) {
        for(int i=0;i<types.length;i++)
            if(types[i]==type)return rotations[i];
        return 0;
    }
    boolean remember(int type,int rotation) {
        for(int i=0;i<types.length;i++) {
            if(types[i]!=type)continue;
            rotations[i]=rotation&3;
            return true;
        }
        return false;
    }
}
